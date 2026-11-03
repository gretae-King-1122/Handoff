package com.handoff.hook;

import com.handoff.api.health.HealthEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultBrowserHandoffTest {
    private static class Reporter implements LsposedBackend.Reporter {
        final List<HealthEvent> events = new ArrayList<>();
        boolean enabled = true;
        @Override public void info(String message) { assertFalse(message.contains("secret")); }
        @Override public void warn(String message, Throwable cause) { fail("No raw launch errors"); }
        @Override public void event(HealthEvent event) { events.add(event); }
        @Override public boolean enabled(HealthEvent.Entry entry) {
            assertEquals(HealthEvent.Entry.DEFAULT_BROWSER, entry);
            return enabled;
        }
    }

    @Test void acceptedRequestSkipsHostBrowserAndKeepsEvidenceSeparateFromVideo() throws Throwable {
        Reporter reporter = new Reporter();
        AtomicInteger starts = new AtomicInteger();
        assertNull(BrowserDispatch.intercept(() -> DefaultBrowserHandoff.redirect(() -> {
            starts.incrementAndGet();
            BrowserDispatch.intercept(() -> fail("Recursive external start"), () -> null);
            return true;
        }, reporter), () -> fail("Host browser must not start")));
        assertEquals(1, starts.get());
        assertEquals(List.of(HealthEvent.of(HealthEvent.Kind.ENTERED, HealthEvent.Entry.DEFAULT_BROWSER),
                HealthEvent.of(HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.DEFAULT_BROWSER)), reporter.events);
    }

    @Test void disabledOrUnreadableSettingsDoNotAttemptExternalStart() throws Throwable {
        Reporter disabled = new Reporter(); disabled.enabled = false;
        Reporter unreadable = new Reporter() {
            @Override public boolean enabled(HealthEvent.Entry entry) { throw new IllegalStateException(); }
        };
        for (Reporter reporter : List.of(disabled, unreadable)) {
            assertEquals("original", BrowserDispatch.intercept(() -> DefaultBrowserHandoff.redirect(
                    () -> fail("Unavailable setting"), reporter), () -> "original"));
        }
        assertEquals(HealthEvent.Reason.DISABLED, disabled.events.get(1).reason());
        assertEquals(HealthEvent.Reason.CONFIG_UNAVAILABLE, unreadable.events.get(1).reason());
    }

    @Test void missingBrowserAndLaunchFailureResumeOriginalExactlyOnce() throws Throwable {
        for (boolean throwsException : List.of(false, true)) {
            Reporter reporter = new Reporter(); AtomicInteger original = new AtomicInteger();
            BrowserDispatch.intercept(() -> DefaultBrowserHandoff.redirect(() -> {
                if (throwsException) throw new IllegalStateException("secret query token");
                return false;
            }, reporter), original::incrementAndGet);
            assertEquals(1, original.get());
            assertEquals(HealthEvent.Reason.BROWSER_START_FAILED, reporter.events.get(1).reason());
        }
    }

    @Test void reportingFailureCannotOpenHostBrowserAfterAcceptedLaunch() throws Throwable {
        Reporter reporter = new Reporter() {
            @Override public void event(HealthEvent event) { throw new IllegalStateException(); }
            @Override public void info(String message) { throw new IllegalStateException(); }
        };
        assertNull(BrowserDispatch.intercept(() -> DefaultBrowserHandoff.redirect(() -> true, reporter),
                () -> fail("External request was already accepted")));
    }
}
