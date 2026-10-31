package com.handoff.hook;

import com.handoff.api.health.HealthEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicHandoffTest {
    private static class Reporter implements LsposedBackend.Reporter {
        final List<HealthEvent> events = new ArrayList<>(); boolean enabled = true;
        @Override public void info(String message) { assertFalse(message.contains("https://")); }
        @Override public void warn(String message, Throwable cause) { fail("No raw exception"); }
        @Override public void event(HealthEvent event) { events.add(event); }
        @Override public boolean enabled(HealthEvent.Entry entry) { assertEquals(HealthEvent.Entry.DYNAMIC, entry); return enabled; }
    }
    @Test void startupIsNotAcceptanceAndOnlyFirstTerminalResultIsRecorded() {
        Reporter r = new Reporter(); DynamicHandoff.Completion[] callback = new DynamicHandoff.Completion[1];
        assertTrue(DynamicHandoff.redirect(c -> { callback[0] = c; return true; }, r));
        assertEquals(1, r.events.size()); assertEquals(HealthEvent.Kind.ENTERED, r.events.get(0).kind());
        callback[0].complete(DynamicHandoff.Result.ACCEPTED);
        callback[0].complete(DynamicHandoff.Result.FAILED);
        assertEquals(2, r.events.size()); assertEquals(HealthEvent.Kind.ACCEPTED, r.events.get(1).kind());
        assertEquals(HealthEvent.Entry.DYNAMIC, r.events.get(1).entry());
    }
    @Test void disabledNeverStartsAndOriginalIsCalledOnce() throws Throwable {
        Reporter r = new Reporter(); r.enabled = false;
        assertEquals("qq", BrowserDispatch.intercept(() -> DynamicHandoff.redirect(c -> { fail(); return true; }, r), () -> "qq"));
        assertFalse(r.dynamicNetworkEnabled());
    }
    @Test void startupFailureDoesNotAcceptLateCallbacksAndPreservesOriginal() throws Throwable {
        Reporter r = new Reporter(); DynamicHandoff.Completion[] callback = new DynamicHandoff.Completion[1];
        assertEquals("qq", BrowserDispatch.intercept(() -> DynamicHandoff.redirect(c -> { callback[0] = c; return false; }, r), () -> "qq"));
        callback[0].complete(DynamicHandoff.Result.ACCEPTED);
        assertEquals(2, r.events.size()); assertEquals(HealthEvent.Reason.DYNAMIC_START_FAILED, r.events.get(1).reason());
    }
    @Test void reportsCancellationAndFailedFallbackSeparately() {
        for (var result : DynamicHandoff.Result.values()) {
            Reporter r = new Reporter();
            assertTrue(DynamicHandoff.redirect(c -> { c.complete(result); return true; }, r));
            assertEquals(result == DynamicHandoff.Result.ACCEPTED ? HealthEvent.Kind.ACCEPTED
                    : result == DynamicHandoff.Result.CANCELLED ? HealthEvent.Kind.PASSED : HealthEvent.Kind.LAUNCH_FAILED,
                    r.events.get(1).kind());
        }
    }
}
