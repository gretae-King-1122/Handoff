package com.handoff.hook;

import com.handoff.api.health.HealthEvent;

/** Reports only typed outcomes; the browser dispatch owns recursion and original-call handling. */
final class DefaultBrowserHandoff {
    interface Launch { boolean start() throws Throwable; }
    private DefaultBrowserHandoff() { }

    static boolean redirect(Launch launch, LsposedBackend.Reporter reporter) {
        HealthReports.emit(reporter, HealthEvent.Kind.ENTERED, HealthEvent.Entry.DEFAULT_BROWSER);
        if (!HealthReports.enabled(reporter, HealthEvent.Entry.DEFAULT_BROWSER)) return false;
        boolean accepted;
        try { accepted = launch.start(); } catch (Throwable ignored) { accepted = false; }
        HealthReports.emit(reporter, accepted ? HealthEvent.Kind.ACCEPTED : HealthEvent.Kind.LAUNCH_FAILED,
                HealthEvent.Entry.DEFAULT_BROWSER, accepted ? HealthEvent.Reason.NONE : HealthEvent.Reason.BROWSER_START_FAILED);
        try { reporter.info(accepted ? "HANDOFF default browser request accepted; page load unverified"
                : "HANDOFF default browser unavailable; pass host browser"); } catch (Throwable ignored) { }
        return accepted;
    }
}
