package com.handoff.hook;

import com.handoff.api.health.HealthEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KuaishouHandoffTest {
    private static class Reporter implements LsposedBackend.Reporter {
        final List<HealthEvent> events = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        boolean enabled = true;
        @Override public void info(String message) { messages.add(message); }
        @Override public void warn(String message, Throwable cause) { fail("No raw exception logging"); }
        @Override public void event(HealthEvent event) { events.add(event); }
        @Override public boolean enabled(HealthEvent.Entry entry) {
            assertEquals(HealthEvent.Entry.KUAISHOU, entry); return enabled;
        }
    }

    @Test void acceptedLaunchBlocksOriginalExactlyOnceAndReportsOnlyKuaishouEvidence() throws Throwable {
        Reporter reporter = new Reporter(); AtomicInteger starts = new AtomicInteger();
        assertNull(BrowserDispatch.intercept(() -> KuaishouHandoff.redirect(() -> {
            starts.incrementAndGet(); return true;
        }, reporter), () -> fail("No QQ browser after accepted start")));
        assertEquals(1, starts.get());
        assertEquals(List.of(HealthEvent.of(HealthEvent.Kind.ENTERED, HealthEvent.Entry.KUAISHOU),
                HealthEvent.of(HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.KUAISHOU)), reporter.events);
    }

    @Test void disabledPilotDoesNotAttemptALaunchAndPreservesOriginal() throws Throwable {
        Reporter reporter = new Reporter(); reporter.enabled = false;
        assertEquals("qq", BrowserDispatch.intercept(() -> KuaishouHandoff.redirect(() -> fail("Disabled"), reporter), () -> "qq"));
        assertEquals(HealthEvent.Reason.DISABLED, reporter.events.get(1).reason());
        assertTrue(reporter.messages.isEmpty());
    }

    @Test void unavailableSettingsFailOpen() {
        Reporter reporter = new Reporter() {
            @Override public boolean enabled(HealthEvent.Entry entry) { throw new IllegalStateException(); }
        };
        assertFalse(KuaishouHandoff.redirect(() -> fail("Unreadable settings"), reporter));
        assertEquals(HealthEvent.Reason.CONFIG_UNAVAILABLE, reporter.events.get(1).reason());
    }

    @Test void failedOrThrowingLaunchResumesQqOnlyOnceWithoutLeakingExceptionText() throws Throwable {
        for (boolean throwsException : List.of(false, true)) {
            Reporter reporter = new Reporter(); AtomicInteger original = new AtomicInteger();
            BrowserDispatch.intercept(() -> KuaishouHandoff.redirect(() -> {
                if (throwsException) throw new IllegalStateException("private share_token");
                return false;
            }, reporter), original::incrementAndGet);
            assertEquals(1, original.get());
            assertEquals(HealthEvent.Reason.KUAISHOU_START_FAILED, reporter.events.get(1).reason());
            assertFalse(reporter.messages.toString().contains("private"));
        }
    }

    @Test void acceptedLaunchDoesNotFailOpenWhenReportingThrows() throws Throwable {
        Reporter reporter = new Reporter() {
            @Override public void event(HealthEvent event) { throw new IllegalStateException(); }
            @Override public void info(String message) { throw new IllegalStateException(); }
        };
        assertNull(BrowserDispatch.intercept(() -> KuaishouHandoff.redirect(() -> true, reporter), () -> fail("Already accepted")));
    }

    @Test void externalStartReentryDoesNotRepeatThePilot() throws Throwable {
        AtomicInteger attempts = new AtomicInteger(); AtomicInteger external = new AtomicInteger();
        BrowserDispatch.intercept(() -> KuaishouHandoff.redirect(() -> {
            attempts.incrementAndGet();
            BrowserDispatch.intercept(() -> fail("Recursive launch"), external::incrementAndGet);
            return true;
        }, new Reporter()), () -> fail("Original must be suppressed"));
        assertEquals(1, attempts.get()); assertEquals(1, external.get());
    }

    @Test void resolverStartupDoesNotClaimThatKuaishouAcceptedTheWork() throws Throwable {
        Reporter reporter = new Reporter();
        KuaishouHandoff.Completion[] callback = new KuaishouHandoff.Completion[1];
        assertNull(BrowserDispatch.intercept(() -> KuaishouHandoff.redirectShort(completion -> {
            callback[0] = completion; return true;
        }, reporter), () -> fail("Resolver owns this request")));
        assertEquals(List.of(HealthEvent.of(HealthEvent.Kind.ENTERED, HealthEvent.Entry.KUAISHOU)), reporter.events);
        callback[0].complete(KuaishouHandoff.ShortResult.ACCEPTED);
        callback[0].complete(KuaishouHandoff.ShortResult.FAILED);
        assertEquals(2, reporter.events.size());
        assertEquals(HealthEvent.Kind.ACCEPTED, reporter.events.get(1).kind());
    }

    @Test void asyncFallbackAndCancellationAreNotSuccessfulHandoffs() {
        for (var result : List.of(KuaishouHandoff.ShortResult.FAILED, KuaishouHandoff.ShortResult.CANCELLED,
                KuaishouHandoff.ShortResult.FALLBACK_FAILED)) {
            Reporter reporter = new Reporter();
            assertTrue(KuaishouHandoff.redirectShort(completion -> { completion.complete(result); return true; }, reporter));
            assertNotEquals(HealthEvent.Kind.ACCEPTED, reporter.events.get(1).kind());
            assertEquals(result == KuaishouHandoff.ShortResult.CANCELLED ? HealthEvent.Reason.KUAISHOU_CANCELLED
                    : result == KuaishouHandoff.ShortResult.FAILED ? HealthEvent.Reason.KUAISHOU_RESOLVE_FAILED
                    : HealthEvent.Reason.KUAISHOU_FALLBACK_FAILED, reporter.events.get(1).reason());
        }
    }

    @Test void disabledOrUnavailableShortResolverRunsOriginalExactlyOnce() throws Throwable {
        Reporter disabled = new Reporter(); disabled.enabled = false;
        assertEquals("qq", BrowserDispatch.intercept(() -> KuaishouHandoff.redirectShort(
                completion -> fail("Disabled resolver"), disabled), () -> "qq"));
        Reporter failed = new Reporter(); AtomicInteger original = new AtomicInteger();
        BrowserDispatch.intercept(() -> KuaishouHandoff.redirectShort(completion -> false, failed), original::incrementAndGet);
        assertEquals(1, original.get());
        assertEquals(HealthEvent.Reason.KUAISHOU_START_FAILED, failed.events.get(1).reason());
    }
}
