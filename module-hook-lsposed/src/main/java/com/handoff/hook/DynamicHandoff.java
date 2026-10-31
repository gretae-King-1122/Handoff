package com.handoff.hook;

import com.handoff.api.health.HealthEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent settings and diagnostics, using the existing browser dispatch recursion guard. */
final class DynamicHandoff {
    enum Result { ACCEPTED, FAILED, CANCELLED, FALLBACK_FAILED }
    interface Completion { void complete(Result result); }
    interface Launch { boolean start(Completion completion) throws Throwable; }
    private DynamicHandoff() { }

    static boolean redirect(Launch launch, LsposedBackend.Reporter reporter) {
        HealthReports.emit(reporter, HealthEvent.Kind.ENTERED, HealthEvent.Entry.DYNAMIC);
        if (!HealthReports.enabled(reporter, HealthEvent.Entry.DYNAMIC)) return false;
        AtomicBoolean completed = new AtomicBoolean();
        Completion completion = result -> {
            if (!completed.compareAndSet(false, true)) return;
            HealthEvent.Kind kind = result == Result.ACCEPTED ? HealthEvent.Kind.ACCEPTED
                    : result == Result.CANCELLED ? HealthEvent.Kind.PASSED : HealthEvent.Kind.LAUNCH_FAILED;
            HealthEvent.Reason reason = switch (result) {
                case ACCEPTED -> HealthEvent.Reason.NONE;
                case CANCELLED -> HealthEvent.Reason.DYNAMIC_CANCELLED;
                case FAILED -> HealthEvent.Reason.DYNAMIC_RESOLVE_FAILED;
                case FALLBACK_FAILED -> HealthEvent.Reason.DYNAMIC_FALLBACK_FAILED;
            };
            HealthReports.emit(reporter, kind, HealthEvent.Entry.DYNAMIC, reason);
            info(reporter, "HANDOFF dynamic link result=" + result + "; playback unverified");
        };
        boolean started;
        try { started = launch.start(completion); } catch (Throwable ignored) { started = false; }
        if (started) info(reporter, "HANDOFF dynamic link resolving; not yet accepted by target App");
        else {
            if (completed.compareAndSet(false, true))
                HealthReports.emit(reporter, HealthEvent.Kind.LAUNCH_FAILED, HealthEvent.Entry.DYNAMIC,
                        HealthEvent.Reason.DYNAMIC_START_FAILED);
            info(reporter, "HANDOFF dynamic resolver unavailable; pass QQ browser");
        }
        return started;
    }

    private static void info(LsposedBackend.Reporter reporter, String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }
}
