package com.handoff.hook;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MiniAppRouteTest {
    private static final String ACTIVITY = "com.tencent.mobileqq.activity.QPublicTransFragmentActivity";

    @Test void nonEmptyMetadataBundleDoesNotDisableValidVideoHandoff() throws Throwable {
        // Device trace: hasExtras=true, overridesModel=false, receiver=false, videoMatch=true.
        // Synthetic key names; the decision must not depend on ordinary metadata values.
        Set<String> metadata = Set.of("launch-report", "start-time");
        assertTrue(MiniAppDispatch.supportedCaller(ACTIVITY, false, metadata));
        AtomicInteger starts = new AtomicInteger();
        MiniAppDispatch.intercept(new MiniAppDispatchTest.Info(),
                MiniAppDispatch.supportedCaller(ACTIVITY, false, metadata), video -> {
                    starts.incrementAndGet(); return true;
                }, () -> fail("non-empty metadata must not bypass handoff"));
        assertEquals(1, starts.get());
    }

    @Test void replacedModelOrCallbackInBundleMustStillPassThrough() throws Throwable {
        for (Set<String> keys : Set.of(Set.of("KEY_APPINFO"), Set.of("receiver"),
                Set.of("launch-report", "KEY_APPINFO"))) {
            assertFalse(MiniAppDispatch.supportedCaller(ACTIVITY, false, keys));
            AtomicInteger originals = new AtomicInteger();
            MiniAppDispatch.intercept(new MiniAppDispatchTest.Info(),
                    MiniAppDispatch.supportedCaller(ACTIVITY, false, keys),
                    video -> fail("unknown contract"), () -> originals.incrementAndGet());
            assertEquals(1, originals.get());
        }
    }

    @Test void explicitCallbackAndUnknownActivityRemainUnsupported() {
        assertFalse(MiniAppDispatch.supportedCaller(ACTIVITY, true, Set.of("metadata")));
        assertFalse(MiniAppDispatch.supportedCaller("other-activity", false, Set.of()));
        assertFalse(MiniAppDispatch.supportedCaller(null, false, Set.of()));
        assertFalse(MiniAppDispatch.supportedCaller(ACTIVITY, false, (Set<String>) null));
        assertTrue(MiniAppDispatch.supportedCaller(ACTIVITY, false, Set.of()));
    }
}
