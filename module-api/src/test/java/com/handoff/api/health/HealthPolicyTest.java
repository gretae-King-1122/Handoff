package com.handoff.api.health;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HealthPolicyTest {
    private final HealthPolicy.Environment environment = new HealthPolicy.Environment(5, 16580, 36, "PJD110", "LSPosed 2.2.1");
    private HealthPolicy.Evidence event(long at, long module, long qq, HealthEvent.Kind kind, HealthEvent.Entry entry) {
        return new HealthPolicy.Evidence(at, module, qq, HealthEvent.of(kind, entry), environment.framework());
    }
    @Test void installationNeverCountsAsRegressionPass() {
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.WEB, 100, environment,
                List.of(event(101, 5, 16580, HealthEvent.Kind.INSTALLED, HealthEvent.Entry.BROWSER))));
    }
    @Test void positiveCaseNeedsFreshMatchingEnvironmentAndEntry() {
        assertTrue(HealthPolicy.mayConfirm(HealthPolicy.Case.WEB, 100, environment,
                List.of(event(101, 5, 16580, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER))));
        for (var e : List.of(event(99, 5, 16580, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER),
                event(101, 4, 16580, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER),
                event(101, 5, 14540, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER),
                event(101, 5, 16580, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.SDK))) {
            assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.WEB, 100, environment, List.of(e)));
        }
    }
    @Test void miniEntriesAreInterchangeableButBrowserIsNotMiniEvidence() {
        for (var entry : List.of(HealthEvent.Entry.SDK, HealthEvent.Entry.SERVICE, HealthEvent.Entry.CONTAINER))
            assertTrue(HealthPolicy.mayConfirm(HealthPolicy.Case.MINI, 100, environment,
                    List.of(event(100, 5, 16580, HealthEvent.Kind.ACCEPTED, entry))));
    }
    @Test void negativeCasesRemainExplicitlyManual() {
        assertTrue(HealthPolicy.mayConfirm(HealthPolicy.Case.OTHER, 100, environment, List.of()));
        assertTrue(HealthPolicy.mayConfirm(HealthPolicy.Case.UNKNOWN, 100, environment, List.of()));
    }
    @Test void bridgeAuthorizationNeverAcceptsAnUnrelatedOrSimilarlyNamedPackage() {
        assertTrue(HealthPolicy.mayReport(1, 1, null));
        assertTrue(HealthPolicy.mayReport(2, 1, new String[]{"com.tencent.mobileqq"}));
        assertFalse(HealthPolicy.mayReport(2, 1, new String[]{"com.tencent.mobileqq.fake"}));
        assertFalse(HealthPolicy.mayReport(2000, 1, null));
    }
    @Test void versionsSelectEvidenceNotAnAutomaticCompatibilityVerdict() {
        assertTrue(QqAdapters.resolve(16580).observed());
        assertFalse(QqAdapters.resolve(14540).observed());
        assertFalse(QqAdapters.resolve(99999).observed());
        assertThrows(UnsupportedOperationException.class, () -> QqAdapters.STRICT.containers().clear());
    }
    @Test void unknownOrDifferentFrameworkCannotPassPositiveRegression() {
        var signal = event(101, 5, 16580, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER);
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.WEB, 100,
                new HealthPolicy.Environment(5, 16580, 36, "PJD110", "unknown"), List.of(signal)));
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.WEB, 100,
                new HealthPolicy.Environment(5, 16580, 36, "PJD110", "other framework"), List.of(signal)));
    }
    @Test void metadataSanitizerHandlesUnicodeWithoutLeakingOrCrashing() {
        assertEquals("_", HealthPolicy.safe("😀"));
        assertFalse(HealthPolicy.safe("token=中文\n").contains("\n"));
        assertEquals(80, HealthPolicy.safe("a".repeat(200)).length());
    }
    @Test void douyinPilotIsOptInWithoutChangingExistingDefaults() {
        for (var entry : HealthEvent.Entry.values())
            assertEquals(entry != HealthEvent.Entry.DOUYIN && entry != HealthEvent.Entry.KUAISHOU
                    && entry != HealthEvent.Entry.DYNAMIC, entry.defaultEnabled());
    }
    @Test void dynamicEvidenceCannotValidateExistingPlatformOrViceVersa() {
        var signal = event(101, 5, 16580, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.DYNAMIC);
        assertTrue(HealthPolicy.mayConfirm(HealthPolicy.Case.DYNAMIC, 100, environment, List.of(signal)));
        for (var entry : HealthEvent.Entry.values()) if (entry != HealthEvent.Entry.DYNAMIC)
            assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.DYNAMIC, 100, environment,
                    List.of(event(101, 5, 16580, HealthEvent.Kind.ACCEPTED, entry))));
        for (var test : List.of(HealthPolicy.Case.WEB, HealthPolicy.Case.MINI, HealthPolicy.Case.DOUYIN, HealthPolicy.Case.KUAISHOU))
            assertFalse(HealthPolicy.mayConfirm(test, 100, environment, List.of(signal)));
    }
    @Test void douyinAndBilibiliEvidenceCannotValidateEachOther() {
        var douyin = event(101, 5, 16580, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.DOUYIN);
        var bili = event(101, 5, 16580, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER);
        assertTrue(HealthPolicy.mayConfirm(HealthPolicy.Case.DOUYIN, 100, environment, List.of(douyin)));
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.DOUYIN, 100, environment, List.of(bili)));
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.WEB, 100, environment, List.of(douyin)));
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.MINI, 100, environment, List.of(douyin)));
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.DOUYIN, 102, environment, List.of(douyin)));
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.DOUYIN, 100, environment,
                List.of(event(101, 5, 16580, HealthEvent.Kind.INSTALLED, HealthEvent.Entry.DOUYIN))));
    }
    @Test void kuaishouEvidenceIsIndependentOfEveryExistingPlatformAndEntry() {
        var signal = event(101, 5, 16580, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.KUAISHOU);
        assertTrue(HealthPolicy.mayConfirm(HealthPolicy.Case.KUAISHOU, 100, environment, List.of(signal)));
        for (var test : List.of(HealthPolicy.Case.WEB, HealthPolicy.Case.MINI, HealthPolicy.Case.DOUYIN))
            assertFalse(HealthPolicy.mayConfirm(test, 100, environment, List.of(signal)));
        for (var entry : HealthEvent.Entry.values()) if (entry != HealthEvent.Entry.KUAISHOU)
            assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.KUAISHOU, 100, environment,
                    List.of(event(101, 5, 16580, HealthEvent.Kind.ACCEPTED, entry))));
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.KUAISHOU, 102, environment, List.of(signal)));
        assertFalse(HealthPolicy.mayConfirm(HealthPolicy.Case.KUAISHOU, 100, environment,
                List.of(event(101, 5, 16580, HealthEvent.Kind.INSTALLED, HealthEvent.Entry.KUAISHOU))));
    }
}
