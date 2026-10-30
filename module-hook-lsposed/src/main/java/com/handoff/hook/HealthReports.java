package com.handoff.hook;

import com.handoff.api.health.HealthEvent;

final class HealthReports {
    private HealthReports() { }
    static void emit(LsposedBackend.Reporter reporter, HealthEvent.Kind kind, HealthEvent.Entry entry) {
        emit(reporter, kind, entry, HealthEvent.Reason.NONE);
    }
    static void emit(LsposedBackend.Reporter reporter, HealthEvent.Kind kind, HealthEvent.Entry entry, HealthEvent.Reason reason) {
        try { reporter.event(new HealthEvent(kind, entry, reason)); } catch (Throwable ignored) { }
    }
    static boolean enabled(LsposedBackend.Reporter reporter, HealthEvent.Entry entry) {
        boolean enabled;
        try { enabled = reporter.enabled(entry); } catch (Throwable ignored) {
            emit(reporter, HealthEvent.Kind.PASSED, entry, HealthEvent.Reason.CONFIG_UNAVAILABLE);
            return false;
        }
        if (!enabled) emit(reporter, HealthEvent.Kind.PASSED, entry, HealthEvent.Reason.DISABLED);
        return enabled;
    }
    static HealthEvent.Reason reason(String value) {
        return switch (value == null ? "" : value) {
            case "callback" -> HealthEvent.Reason.CALLBACK;
            case "inactive-activity" -> HealthEvent.Reason.INACTIVE;
            case "unknown-activity" -> HealthEvent.Reason.CALLER;
            case "unreadable-args", "unreadable-extras" -> HealthEvent.Reason.UNREADABLE;
            default -> HealthEvent.Reason.MODEL;
        };
    }
}
