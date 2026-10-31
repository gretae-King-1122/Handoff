package com.handoff.ui;

import io.github.libxposed.service.HookedTarget;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class FrameworkSnapshotTest {
    private static class Source implements FrameworkSnapshot.Source {
        boolean metadataError, scopeError, targetError;
        int api = 102;
        List<String> scope = List.of("com.tencent.mobileqq");
        List<FrameworkSnapshot.Target> targets = List.of();
        public String name() { if (metadataError) throw new IllegalStateException(); return "LSPosed"; }
        public String version() { return "2.2.1"; }
        public long versionCode() { return 7268; }
        public int api() { return api; }
        public List<String> scope() { if (scopeError) throw new IllegalStateException(); return scope; }
        public List<FrameworkSnapshot.Target> targets() { if (targetError) throw new UnsupportedOperationException(); return targets; }
    }
    private FrameworkSnapshot.Target target(String process, long code, HookedTarget.State state) {
        return new FrameworkSnapshot.Target(process, code, state);
    }
    @Test public void readsFrameworkMetadataAndScopeWithoutClaimingRunning() {
        var s = FrameworkSnapshot.read(new Source(), 6);
        assertTrue(s.connected()); assertTrue(s.scopeKnown()); assertTrue(s.scoped());
        assertEquals("LSPosed 2.2.1", s.label()); assertEquals(7268, s.versionCode()); assertEquals(102, s.api());
        assertEquals(FrameworkSnapshot.RuntimeState.NOT_RUNNING, s.runtime());
    }
    @Test public void unavailableMetadataNeverLooksConnected() {
        Source source = new Source(); source.metadataError = true;
        assertFalse(FrameworkSnapshot.read(source, 6).connected());
        assertFalse(FrameworkSnapshot.read((io.github.libxposed.service.XposedService) null, 6).connected());
    }
    @Test public void failedScopeReadPreservesFrameworkButRemainsUnknown() {
        Source source = new Source(); source.scopeError = true;
        var s = FrameworkSnapshot.read(source, 6);
        assertTrue(s.connected()); assertFalse(s.scopeKnown()); assertFalse(s.scoped());
        assertEquals("未知", s.scopeLabel());
    }
    @Test public void unsupportedRunningTargetsDoNotHideScopeOrMetadata() {
        Source source = new Source(); source.targetError = true;
        var s = FrameworkSnapshot.read(source, 6);
        assertTrue(s.connected()); assertTrue(s.scoped());
        assertEquals(FrameworkSnapshot.RuntimeState.UNKNOWN, s.runtime());
        source.api = 101;
        assertEquals(FrameworkSnapshot.RuntimeState.UNKNOWN, FrameworkSnapshot.read(source, 6).runtime());
    }
    @Test public void subprocessAndOtherPackagesCannotProveMainProcessActivation() {
        var targets = List.of(target("com.tencent.mobileqq:mini", 6, HookedTarget.State.UP_TO_DATE),
                target("com.tencent.mobileqq.fake", 6, HookedTarget.State.UP_TO_DATE));
        assertEquals(FrameworkSnapshot.RuntimeState.NOT_RUNNING, FrameworkSnapshot.runtime(targets, 6));
    }
    @Test public void activationRequiresMatchingModuleAndUpToDateState() {
        for (var state : HookedTarget.State.values()) {
            var targets = List.of(target("com.tencent.mobileqq", 6, state));
            var expected = state == HookedTarget.State.UP_TO_DATE ? FrameworkSnapshot.RuntimeState.CURRENT
                    : state == HookedTarget.State.RELOADING ? FrameworkSnapshot.RuntimeState.RELOADING : FrameworkSnapshot.RuntimeState.OUTDATED;
            assertEquals(expected, FrameworkSnapshot.runtime(targets, 6));
        }
        assertEquals(FrameworkSnapshot.RuntimeState.OUTDATED,
                FrameworkSnapshot.runtime(List.of(target("com.tencent.mobileqq", 4, HookedTarget.State.UP_TO_DATE)), 6));
        assertNotEquals(FrameworkSnapshot.RuntimeState.CURRENT,
                FrameworkSnapshot.runtime(List.of(target("com.tencent.mobileqq", 0, HookedTarget.State.UP_TO_DATE)), 0));
    }
}
