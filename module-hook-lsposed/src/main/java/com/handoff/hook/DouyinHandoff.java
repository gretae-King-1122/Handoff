package com.handoff.hook;

import com.handoff.api.health.HealthEvent;

/** Small, independently testable pilot policy; the caller owns the existing dispatch recursion guard. */
final class DouyinHandoff {
    interface Launch { boolean start() throws Throwable; }
    private DouyinHandoff() { }

    static boolean redirect(Launch launch, LsposedBackend.Reporter reporter) {
        HealthReports.emit(reporter, HealthEvent.Kind.ENTERED, HealthEvent.Entry.DOUYIN);
        if (!HealthReports.enabled(reporter, HealthEvent.Entry.DOUYIN)) return false;
        boolean accepted;
        try { accepted = launch.start(); } catch (Throwable ignored) { accepted = false; }
        HealthReports.emit(reporter, accepted ? HealthEvent.Kind.ACCEPTED : HealthEvent.Kind.LAUNCH_FAILED,
                HealthEvent.Entry.DOUYIN, accepted ? HealthEvent.Reason.NONE : HealthEvent.Reason.DOUYIN_START_FAILED);
        // Never log the share text, short code, video ID, original URL or exception message.
        try { reporter.info(accepted ? "HANDOFF douyin request accepted; playback unverified"
                : "HANDOFF douyin launch failed; pass QQ browser"); } catch (Throwable ignored) { }
        return accepted;
    }
}
