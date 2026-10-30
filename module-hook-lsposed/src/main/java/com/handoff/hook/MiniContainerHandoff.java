package com.handoff.hook;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;

/** Main-process final dispatch fallback; independent of QQ's internal class definitions/loaders. */
final class MiniContainerHandoff {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private MiniContainerHandoff() { }

    static void install(XposedInterface xposed, LsposedBackend.Reporter reporter) throws Throwable {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Method method = Activity.class.getDeclaredMethod("startActivityForResult", Intent.class, int.class, Bundle.class);
            xposed.hook(method).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> {
                Activity activity;
                Intent outgoing;
                MiniContainerDispatch.Envelope envelope;
                try {
                    activity = chain.getThisObject() instanceof Activity ? (Activity) chain.getThisObject() : null;
                    outgoing = null;
                    envelope = null;
                    if (activity != null && HookTargets.MINI_CALLER_CLASS.equals(activity.getClass().getName())) {
                        outgoing = chain.getArgs().get(0) instanceof Intent ? (Intent) chain.getArgs().get(0) : null;
                        ComponentName target = outgoing == null ? null : outgoing.getComponent();
                        MiniContainerDispatch.Caller caller = new MiniContainerDispatch.Caller(
                                activity.getClass().getName(), activity.isFinishing(), activity.isDestroyed());
                        envelope = target == null ? null : new MiniContainerDispatch.Envelope(caller,
                                target.getPackageName(), target.getClassName(), (Integer) chain.getArgs().get(1),
                                (outgoing.getFlags() & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0, outgoing.getSelector() != null);
                    }
                } catch (Throwable ignored) {
                    // Extraction errors fail open. Never catch and retry the original invocation.
                    envelope = null;
                    activity = null;
                    outgoing = null;
                }
                // Fast exit before allocating payload-reader / launcher closures on unrelated calls.
                if (!MiniContainerDispatch.supported(envelope)) return chain.proceed();
                Activity caller = activity;
                Intent originalIntent = outgoing;
                return MiniContainerDispatch.intercept(envelope, () -> {
                    // Work on a copy: do not change QQ's Intent, parcel loader or original extras.
                    Intent snapshot = new Intent(originalIntent);
                    snapshot.setExtrasClassLoader(caller.getClass().getClassLoader());
                    Bundle extras = snapshot.getExtras();
                    if (extras == null) return null;
                    return new MiniContainerDispatch.Payload(extras.getBoolean("sdk_mode", false),
                            extras.get("receiver"), extras.get("KEY_APPINFO"));
                }, video -> MiniAppHandoff.launch(caller, video, reporter, "container-dispatch"), chain::proceed);
            });
            log(reporter, "HANDOFF mini container installed " + method);
        } catch (Throwable error) {
            INSTALLED.set(false);
            throw error;
        }
    }

    private static void log(LsposedBackend.Reporter reporter, String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }
}
