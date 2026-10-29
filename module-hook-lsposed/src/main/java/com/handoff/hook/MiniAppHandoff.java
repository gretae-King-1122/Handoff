package com.handoff.hook;

import android.app.Activity;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/** Intercepts the verified resolved-model entry, before SDK strategy / container dispatch. */
final class MiniAppHandoff {
    private MiniAppHandoff() { }

    static void install(XposedInterface xposed, ClassLoader loader, LsposedBackend.Reporter reporter)
            throws ReflectiveOperationException {
        Class<?> service = Class.forName(HookTargets.MINI_LAUNCH_CLASS, false, loader);
        Class<?> model = Class.forName(HookTargets.MINI_INFO_CLASS, false, loader);
        Method method = service.getDeclaredMethod("startMiniApp", Activity.class, model, Bundle.class, ResultReceiver.class);
        if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("Unsupported mini launch signature");
        }
        xposed.hook(method).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> {
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
                if (!supported && "1109937557".equals(Reflect.str(info, "appId"))) {
                    info(reporter, "HANDOFF mini pass reason=" + reason);
                }
            } catch (Throwable ignored) {
                info(reporter, "HANDOFF mini pass reason=unreadable-args");
                return chain.proceed();
            }
            return MiniAppDispatch.intercept(info, supported, video -> {
                long start = SystemClock.elapsedRealtimeNanos();
                String how = BiliLauncher.launchVideo(activity, video.url(), video.id());
                if (how == null) {
                    info(reporter, "HANDOFF mini pass reason=launch-failed id=" + video.id());
                    return false;
                }
                // Logging cannot undo an accepted launch; no tracking queries or object dumps.
                info(reporter, "HANDOFF blocked QQ mini id=" + video.id() + " source=" + video.source()
                        + " via " + how + " launchUs=" + (SystemClock.elapsedRealtimeNanos() - start) / 1_000);
                // The original PreloadingFragment caller continues to run its quit()/result handling.
                // Don't finish Activities here or invent a ResultReceiver success contract.
                return true;
            }, chain::proceed);
        });
        info(reporter, "HANDOFF mini installed " + method);
    }

    private static void info(LsposedBackend.Reporter reporter, String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }
}
