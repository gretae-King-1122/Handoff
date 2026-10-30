package com.handoff.hook;

import com.handoff.api.health.HealthEvent;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HealthReportsTest {
    private static class Reporter implements LsposedBackend.Reporter {
        final List<HealthEvent> events = new ArrayList<>();
        @Override public void info(String message) { }
        @Override public void warn(String message, Throwable cause) { }
        @Override public void event(HealthEvent event) { events.add(event); }
    }

    @Test void disabledEntryPreservesOriginalExactlyOnce() throws Throwable {
        Reporter reporter = new Reporter() {
            @Override public boolean enabled(HealthEvent.Entry entry) { return false; }
        };
        AtomicInteger original = new AtomicInteger();
        assertEquals(1, BrowserDispatch.intercept(() -> HealthReports.enabled(reporter, HealthEvent.Entry.BROWSER),
                original::incrementAndGet));
        assertEquals(1, original.get());
        assertEquals(List.of(new HealthEvent(HealthEvent.Kind.PASSED, HealthEvent.Entry.BROWSER,
                HealthEvent.Reason.DISABLED)), reporter.events);
    }

    @Test void unavailableSettingsAreNotReportedAsUserDisabled() {
        Reporter reporter = new Reporter() {
            @Override public boolean enabled(HealthEvent.Entry entry) { throw new IllegalStateException(); }
        };
        assertFalse(HealthReports.enabled(reporter, HealthEvent.Entry.SDK));
        assertEquals(HealthEvent.Reason.CONFIG_UNAVAILABLE, reporter.events.get(0).reason());
    }

    @Test void brokenTelemetryCannotUndoAnAcceptedLaunch() throws Throwable {
        Reporter reporter = new Reporter() {
            @Override public void event(HealthEvent event) { throw new AssertionError("telemetry unavailable"); }
        };
        assertNull(BrowserDispatch.intercept(() -> {
            HealthReports.emit(reporter, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER);
            return true;
        }, () -> fail("accepted launch must not open QQ too")));
        assertTrue(HealthReports.enabled(reporter, HealthEvent.Entry.BROWSER));
    }
}
