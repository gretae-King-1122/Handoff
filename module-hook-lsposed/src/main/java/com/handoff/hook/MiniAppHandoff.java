package com.handoff.hook;

import android.app.Activity;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/** Exact-signature resolved SDK/service boundaries, before strategy / container dispatch. */
final class MiniAppHandoff {
    private static final MethodInstallGuard INSTALLATIONS = new MethodInstallGuard();
    private MiniAppHandoff() { }

    static void install(XposedInterface xposed, ClassLoader loader, LsposedBackend.Reporter reporter,
                        boolean boundaryDiagnostics)
            throws Throwable {
        installTarget(xposed, loader, reporter, boundaryDiagnostics, HookTargets.MINI_LAUNCH_CLASS, false, "service");
    }

    static void installSdk(XposedInterface xposed, ClassLoader loader, LsposedBackend.Reporter reporter,
                           boolean boundaryDiagnostics) throws Throwable {
        installTarget(xposed, loader, reporter, boundaryDiagnostics, HookTargets.MINI_SDK_CLASS, true, "sdk");
    }

    private static void installTarget(XposedInterface xposed, ClassLoader loader, LsposedBackend.Reporter reporter,
                                      boolean boundaryDiagnostics, String className, boolean isStatic, String entry)
            throws Throwable {
        Class<?> service = Class.forName(className, false, loader);
        Class<?> model = Class.forName(HookTargets.MINI_INFO_CLASS, false, loader);
        Method method = service.getDeclaredMethod("startMiniApp", Activity.class, model, Bundle.class, ResultReceiver.class);
        if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers()) != isStatic) {
            throw new NoSuchMethodException("Unsupported mini launch signature");
        }
        boolean added = INSTALLATIONS.install(method, () -> xposed.hook(method).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> {
            if (boundaryDiagnostics) info(reporter, "[DEBUG-mini-boundary] enter " + method);
            Activity activity;
            Object info;
            boolean supported;
            try {
                Object arg = chain.getArgs().get(0);
                activity = arg instanceof Activity ? (Activity) arg : null;
                info = chain.getArgs().get(1);
                Bundle extras = (Bundle) chain.getArgs().get(2);
                String reason = activity == null || activity.isFinishing() || activity.isDestroyed()
                        ? "inactive-activity" : MiniAppDispatch.callerRejectionReason(activity.getClass().getName(),
                        chain.getArgs().get(3) != null, extras == null ? Set.of() : extras.keySet());
                supported = reason == null;
                if (boundaryDiagnostics) {
                    info(reporter, "[DEBUG-mini-boundary] caller="
                            + (activity == null ? "null" : activity.getClass().getName())
                            + " callerResult=" + (reason == null ? "supported" : reason)
                            + " " + MiniBoundarySummary.describe(info));
                }
                if (!supported && "1109937557".equals(Reflect.str(info, "appId"))) {
                    info(reporter, "HANDOFF mini pass reason=" + reason);
                }
            } catch (Throwable ignored) {
                info(reporter, "HANDOFF mini pass reason=unreadable-args");
                return chain.proceed();
            }
            return MiniAppDispatch.intercept(info, supported,
                    video -> launch(activity, video, reporter, entry), chain::proceed);
        }));
        if (added) info(reporter, "HANDOFF mini installed " + method);
    }

    static boolean launch(Activity activity, MiniAppVideo video, LsposedBackend.Reporter reporter, String entry) {
        long start = SystemClock.elapsedRealtimeNanos();
        String how = BiliLauncher.launchVideo(activity, video.url(), video.id());
        if (how == null) {
            info(reporter, "HANDOFF mini pass reason=launch-failed entry=" + entry + " id=" + video.id());
            return false;
        }
        // No lifecycle shortcuts or invented callback result; QQ still owns its caller's cleanup.
        info(reporter, "HANDOFF blocked QQ mini id=" + video.id() + " source=" + video.source()
                + " entry=" + entry + " via " + how
                + " launchUs=" + (SystemClock.elapsedRealtimeNanos() - start) / 1_000);
        return true;
    }

    private static void info(LsposedBackend.Reporter reporter, String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }
}
