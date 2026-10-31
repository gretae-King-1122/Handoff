package com.handoff.hook;

import com.handoff.api.health.HealthEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DouyinHandoffTest {
    private static class Reporter implements LsposedBackend.Reporter {
        final List<HealthEvent> events = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        boolean enabled = true;
        @Override public void info(String message) { messages.add(message); }
        @Override public void warn(String message, Throwable cause) { fail("No raw exception logging"); }
        @Override public void event(HealthEvent event) { events.add(event); }
        @Override public boolean enabled(HealthEvent.Entry entry) {
            assertEquals(HealthEvent.Entry.DOUYIN, entry); return enabled;
        }
    }

    @Test void acceptedLaunchBlocksOriginalExactlyOnceAndReportsOnlyDouyinEvidence() throws Throwable {
        Reporter reporter = new Reporter(); AtomicInteger starts = new AtomicInteger();
        assertNull(BrowserDispatch.intercept(() -> DouyinHandoff.redirect(() -> {
            starts.incrementAndGet(); return true;
        }, reporter), () -> fail("No QQ browser after accepted start")));
        assertEquals(1, starts.get());
        assertEquals(List.of(HealthEvent.of(HealthEvent.Kind.ENTERED, HealthEvent.Entry.DOUYIN),
                HealthEvent.of(HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.DOUYIN)), reporter.events);
    }

    @Test void disabledPilotDoesNotAttemptALaunchAndPreservesOriginal() throws Throwable {
        Reporter reporter = new Reporter(); reporter.enabled = false;
        assertEquals("qq", BrowserDispatch.intercept(() -> DouyinHandoff.redirect(() -> fail("Disabled"), reporter), () -> "qq"));
        assertEquals(HealthEvent.Reason.DISABLED, reporter.events.get(1).reason());
        assertTrue(reporter.messages.isEmpty());
    }

    @Test void unavailableSettingsFailOpen() {
        Reporter reporter = new Reporter() {
            @Override public boolean enabled(HealthEvent.Entry entry) { throw new IllegalStateException(); }
        };
        assertFalse(DouyinHandoff.redirect(() -> fail("Unreadable settings"), reporter));
        assertEquals(HealthEvent.Reason.CONFIG_UNAVAILABLE, reporter.events.get(1).reason());
    }

    @Test void failedOrThrowingLaunchResumesQqOnlyOnceWithoutLeakingExceptionText() throws Throwable {
        for (boolean throwsException : List.of(false, true)) {
            Reporter reporter = new Reporter(); AtomicInteger original = new AtomicInteger();
            BrowserDispatch.intercept(() -> DouyinHandoff.redirect(() -> {
                if (throwsException) throw new IllegalStateException("private share_token");
                return false;
            }, reporter), original::incrementAndGet);
            assertEquals(1, original.get());
            assertEquals(HealthEvent.Reason.DOUYIN_START_FAILED, reporter.events.get(1).reason());
            assertFalse(reporter.messages.toString().contains("private"));
        }
    }

    @Test void acceptedLaunchDoesNotFailOpenWhenReportingThrows() throws Throwable {
        Reporter reporter = new Reporter() {
            @Override public void event(HealthEvent event) { throw new IllegalStateException(); }
            @Override public void info(String message) { throw new IllegalStateException(); }
        };
        assertNull(BrowserDispatch.intercept(() -> DouyinHandoff.redirect(() -> true, reporter), () -> fail("Already accepted")));
    }

    @Test void externalStartReentryDoesNotRepeatThePilot() throws Throwable {
        AtomicInteger attempts = new AtomicInteger(); AtomicInteger external = new AtomicInteger();
        BrowserDispatch.intercept(() -> DouyinHandoff.redirect(() -> {
            attempts.incrementAndGet();
            BrowserDispatch.intercept(() -> fail("Recursive launch"), external::incrementAndGet);
            return true;
        }, new Reporter()), () -> fail("Original must be suppressed"));
        assertEquals(1, attempts.get()); assertEquals(1, external.get());
    }
}
