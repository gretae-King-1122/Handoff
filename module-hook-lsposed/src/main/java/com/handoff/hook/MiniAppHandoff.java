package com.handoff.hook;

import android.app.Activity;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import com.handoff.api.health.HealthEvent;

/** Exact-signature resolved SDK/service boundaries, before strategy / container dispatch. */
final class MiniAppHandoff {
    private static final MethodInstallGuard INSTALLATIONS = new MethodInstallGuard();
    private final LsposedBackend.Reporter reporter;
    private final boolean boundaryDiagnostics;

    private MiniAppHandoff(LsposedBackend.Reporter reporter, boolean boundaryDiagnostics) {
        this.reporter = reporter;
        this.boundaryDiagnostics = boundaryDiagnostics;
    }

    static void install(XposedInterface xposed, ClassLoader loader, LsposedBackend.Reporter reporter,
                        boolean boundaryDiagnostics)
            throws Throwable {
        new MiniAppHandoff(reporter, boundaryDiagnostics).installTarget(xposed, loader, false);
    }

    static void installSdk(XposedInterface xposed, ClassLoader loader, LsposedBackend.Reporter reporter,
                           boolean boundaryDiagnostics) throws Throwable {
        new MiniAppHandoff(reporter, boundaryDiagnostics).installTarget(xposed, loader, true);
    }

    private void installTarget(XposedInterface xposed, ClassLoader loader, boolean sdk) throws Throwable {
        String className = sdk ? reporter.profile().contract().miniSdk() : reporter.profile().contract().miniService();
        String entry = sdk ? "sdk" : "service";
        Class<?> service = Class.forName(className, false, loader);
        Class<?> model = Class.forName(reporter.profile().contract().miniModel(), false, loader);
        Method method = service.getDeclaredMethod("startMiniApp", Activity.class, model, Bundle.class, ResultReceiver.class);
        if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers()) != sdk) {
            throw new NoSuchMethodException("Unsupported mini launch signature");
        }
        boolean added = INSTALLATIONS.install(method, () -> xposed.hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> intercept(chain, method, entry)));
        if (added) info(reporter, "HANDOFF mini installed " + method);
        HealthReports.emit(reporter, HealthEvent.Kind.INSTALLED, HealthEvent.entry(entry));
    }

    /** Resolved arguments stay local to one invocation; no Activity/model is cached across clicks. */
    private record LaunchCall(Activity activity, Object model, String rejectionReason) {
        boolean supported() { return rejectionReason == null; }
    }

    private Object intercept(XposedInterface.Chain chain, Method method, String entry) throws Throwable {
        if (boundaryDiagnostics) info(reporter, "[DEBUG-mini-boundary] enter " + method);
        LaunchCall call;
        try {
            call = readCall(chain);
            reportCall(call, entry);
        } catch (Throwable ignored) {
            info(reporter, "HANDOFF mini pass reason=unreadable-args");
            HealthReports.emit(reporter, HealthEvent.Kind.PASSED, HealthEvent.entry(entry), HealthEvent.Reason.UNREADABLE);
            return chain.proceed();
        }
        if ("1109937557".equals(Reflect.str(call.model(), "appId"))
                && !HealthReports.enabled(reporter, HealthEvent.entry(entry))) return chain.proceed();
        // Keep the host invocation outside argument/launch exception isolation: never retry it.
        return MiniAppDispatch.intercept(call.model(), call.supported(),
                video -> launch(call.activity(), video, reporter, entry), chain::proceed);
    }

    private LaunchCall readCall(XposedInterface.Chain chain) {
        Object arg = chain.getArgs().get(0);
        Activity activity = arg instanceof Activity ? (Activity) arg : null;
        Object model = chain.getArgs().get(1);
        Bundle extras = (Bundle) chain.getArgs().get(2);
        String reason = activity == null || activity.isFinishing() || activity.isDestroyed()
                ? "inactive-activity" : MiniAppDispatch.callerRejectionReason(activity.getClass().getName(),
                chain.getArgs().get(3) != null, extras == null ? Set.of() : extras.keySet(), reporter.profile().contract().miniCaller());
        return new LaunchCall(activity, model, reason);
    }

    private void reportCall(LaunchCall call, String entry) {
        if ("1109937557".equals(Reflect.str(call.model(), "appId"))) {
            HealthReports.emit(reporter, HealthEvent.Kind.ENTERED, HealthEvent.entry(entry));
            if (!call.supported()) HealthReports.emit(reporter, HealthEvent.Kind.PASSED,
                    HealthEvent.entry(entry), HealthReports.reason(call.rejectionReason()));
            else if (MiniAppVideo.read(call.model()) == null) HealthReports.emit(reporter, HealthEvent.Kind.PASSED,
                    HealthEvent.entry(entry), HealthEvent.Reason.MODEL);
        }
        if (boundaryDiagnostics) {
            info(reporter, "[DEBUG-mini-boundary] caller="
                    + (call.activity() == null ? "null" : call.activity().getClass().getName())
                    + " callerResult=" + (call.supported() ? "supported" : call.rejectionReason())
                    + " " + MiniBoundarySummary.describe(call.model()));
        }
        if (!call.supported() && "1109937557".equals(Reflect.str(call.model(), "appId"))) {
            info(reporter, "HANDOFF mini pass reason=" + call.rejectionReason());
        }
    }

    static boolean launch(Activity activity, MiniAppVideo video, LsposedBackend.Reporter reporter, String entry) {
        long start = SystemClock.elapsedRealtimeNanos();
        String how = BiliLauncher.launchVideo(activity, video.url(), video.id());
        if (how == null) {
            info(reporter, "HANDOFF mini pass reason=launch-failed entry=" + entry + " id=" + video.id());
            HealthReports.emit(reporter, HealthEvent.Kind.LAUNCH_FAILED, HealthEvent.entry(entry), HealthEvent.Reason.START_FAILED);
            return false;
        }
        // No lifecycle shortcuts or invented callback result; QQ still owns its caller's cleanup.
        info(reporter, "HANDOFF blocked host mini package=" + reporter.profile().packageName() + " id=" + video.id() + " source=" + video.source()
                + " entry=" + entry + " via " + how
                + " launchUs=" + (SystemClock.elapsedRealtimeNanos() - start) / 1_000);
        HealthReports.emit(reporter, HealthEvent.Kind.ACCEPTED,
                "container-dispatch".equals(entry) ? HealthEvent.Entry.CONTAINER : HealthEvent.entry(entry));
        return true;
    }

    private static void info(LsposedBackend.Reporter reporter, String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }
}
