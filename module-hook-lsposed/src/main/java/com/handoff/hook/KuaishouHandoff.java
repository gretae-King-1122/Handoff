package com.handoff.hook;

import com.handoff.api.health.HealthEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent settings and diagnostics, using the existing browser dispatch recursion guard. */
final class KuaishouHandoff {
    interface Launch { boolean start() throws Throwable; }
    enum ShortResult { ACCEPTED, FAILED, CANCELLED, FALLBACK_FAILED }
    interface Completion { void complete(ShortResult result); }
    interface ShortLaunch { boolean start(Completion completion) throws Throwable; }
    private KuaishouHandoff() { }

    static boolean redirect(Launch launch, LsposedBackend.Reporter reporter) {
        HealthReports.emit(reporter, HealthEvent.Kind.ENTERED, HealthEvent.Entry.KUAISHOU);
        if (!HealthReports.enabled(reporter, HealthEvent.Entry.KUAISHOU)) return false;
        boolean accepted;
        try { accepted = launch.start(); } catch (Throwable ignored) { accepted = false; }
        HealthReports.emit(reporter, accepted ? HealthEvent.Kind.ACCEPTED : HealthEvent.Kind.LAUNCH_FAILED,
                HealthEvent.Entry.KUAISHOU, accepted ? HealthEvent.Reason.NONE : HealthEvent.Reason.KUAISHOU_START_FAILED);
        try { reporter.info(accepted ? "HANDOFF kuaishou request accepted; playback unverified"
                : "HANDOFF kuaishou launch failed; pass QQ browser"); } catch (Throwable ignored) { }
        return accepted;
    }

    static boolean redirectShort(ShortLaunch launch, LsposedBackend.Reporter reporter) {
        HealthReports.emit(reporter, HealthEvent.Kind.ENTERED, HealthEvent.Entry.KUAISHOU);
        if (!HealthReports.enabled(reporter, HealthEvent.Entry.KUAISHOU)) return false;
        AtomicBoolean completed = new AtomicBoolean();
        Completion completion = result -> {
            if (!completed.compareAndSet(false, true)) return;
            HealthEvent.Kind kind = result == ShortResult.ACCEPTED ? HealthEvent.Kind.ACCEPTED
                    : result == ShortResult.CANCELLED ? HealthEvent.Kind.PASSED : HealthEvent.Kind.LAUNCH_FAILED;
            HealthEvent.Reason reason = switch (result) {
                case ACCEPTED -> HealthEvent.Reason.NONE;
                case CANCELLED -> HealthEvent.Reason.KUAISHOU_CANCELLED;
                case FAILED -> HealthEvent.Reason.KUAISHOU_RESOLVE_FAILED;
                case FALLBACK_FAILED -> HealthEvent.Reason.KUAISHOU_FALLBACK_FAILED;
            };
            HealthReports.emit(reporter, kind, HealthEvent.Entry.KUAISHOU, reason);
            info(reporter, "HANDOFF kuaishou short result=" + result + "; playback unverified");
        };
        boolean started;
        try { started = launch.start(completion); } catch (Throwable ignored) { started = false; }
        if (started) info(reporter, "HANDOFF kuaishou short resolving; not yet accepted by Kuaishou");
        else {
            if (completed.compareAndSet(false, true))
                HealthReports.emit(reporter, HealthEvent.Kind.LAUNCH_FAILED, HealthEvent.Entry.KUAISHOU,
                        HealthEvent.Reason.KUAISHOU_START_FAILED);
            info(reporter, "HANDOFF kuaishou resolver unavailable; pass QQ browser");
        }
        return started;
    }

    private static void info(LsposedBackend.Reporter reporter, String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }
}
