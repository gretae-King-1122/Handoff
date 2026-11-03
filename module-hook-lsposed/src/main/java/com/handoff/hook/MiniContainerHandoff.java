package com.handoff.hook;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import com.handoff.api.health.HealthEvent;

/** Main-process final dispatch fallback; independent of QQ's internal class definitions/loaders. */
final class MiniContainerHandoff {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private final LsposedBackend.Reporter reporter;

    private MiniContainerHandoff(LsposedBackend.Reporter reporter) {
        this.reporter = reporter;
    }

    static void install(XposedInterface xposed, LsposedBackend.Reporter reporter) throws Throwable {
        new MiniContainerHandoff(reporter).install(xposed);
    }

    private void install(XposedInterface xposed) throws Throwable {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Method method = Activity.class.getDeclaredMethod("startActivityForResult", Intent.class, int.class, Bundle.class);
            xposed.hook(method).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(this::intercept);
            log(reporter, "HANDOFF mini container installed " + method);
            HealthReports.emit(reporter, HealthEvent.Kind.INSTALLED, HealthEvent.Entry.CONTAINER);
        } catch (Throwable error) {
            INSTALLED.set(false);
            throw error;
        }
    }

    private record LaunchCall(Activity activity, Intent outgoing, MiniContainerDispatch.Envelope envelope) { }

    private Object intercept(XposedInterface.Chain chain) throws Throwable {
        LaunchCall call = readCall(chain);
        // Unrelated calls allocate neither payload-reader closures nor Intent copies.
        if (call == null) return chain.proceed();
        if (!MiniContainerDispatch.supported(call.envelope(), reporter.profile().contract())) {
            if (reporter.profile().contract().containers().contains(call.envelope().targetClass()))
                HealthReports.emit(reporter, HealthEvent.Kind.PASSED, HealthEvent.Entry.CONTAINER, HealthEvent.Reason.PAYLOAD);
            return chain.proceed();
        }
        HealthReports.emit(reporter, HealthEvent.Kind.ENTERED, HealthEvent.Entry.CONTAINER);
        if (!HealthReports.enabled(reporter, HealthEvent.Entry.CONTAINER)) return chain.proceed();
        return MiniContainerDispatch.intercept(call.envelope(), () -> readPayload(call),
                video -> MiniAppHandoff.launch(call.activity(), video, reporter, "container-dispatch"),
                chain::proceed, reporter.profile().contract());
    }

    private LaunchCall readCall(XposedInterface.Chain chain) {
        try {
            if (!(chain.getThisObject() instanceof Activity activity)
                    || !reporter.profile().contract().miniCaller().equals(activity.getClass().getName())) return null;
            Object argument = chain.getArgs().get(0);
            if (!(argument instanceof Intent outgoing)) return null;
            ComponentName target = outgoing.getComponent();
            MiniContainerDispatch.Caller caller = new MiniContainerDispatch.Caller(
                    activity.getClass().getName(), activity.isFinishing(), activity.isDestroyed());
            if (target == null) return null;
            MiniContainerDispatch.Envelope envelope = new MiniContainerDispatch.Envelope(caller,
                    target.getPackageName(), target.getClassName(), (Integer) chain.getArgs().get(1),
                    (outgoing.getFlags() & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0, outgoing.getSelector() != null);
            return new LaunchCall(activity, outgoing, envelope);
        } catch (Throwable ignored) {
            // Extraction errors fail open. Never catch and retry the original invocation.
            return null;
        }
    }

    private MiniContainerDispatch.Payload readPayload(LaunchCall call) throws Throwable {
        try {
            // Work on a copy: do not change the host Intent, parcel loader or original extras.
            Intent snapshot = new Intent(call.outgoing());
            snapshot.setExtrasClassLoader(call.activity().getClass().getClassLoader());
            Bundle extras = snapshot.getExtras();
            if (extras == null) {
                HealthReports.emit(reporter, HealthEvent.Kind.PASSED, HealthEvent.Entry.CONTAINER, HealthEvent.Reason.PAYLOAD);
                return null;
            }
            MiniContainerDispatch.Payload payload = new MiniContainerDispatch.Payload(extras.getBoolean("sdk_mode", false),
                    extras.get("receiver"), extras.get("KEY_APPINFO"));
            if (!payload.sdkMode() || payload.receiver() != null || MiniAppVideo.read(payload.model()) == null)
                HealthReports.emit(reporter, HealthEvent.Kind.PASSED, HealthEvent.Entry.CONTAINER, HealthEvent.Reason.PAYLOAD);
            return payload;
        } catch (Throwable error) {
            HealthReports.emit(reporter, HealthEvent.Kind.PASSED, HealthEvent.Entry.CONTAINER, HealthEvent.Reason.UNREADABLE);
            throw error;
        }
    }

    private static void log(LsposedBackend.Reporter reporter, String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }
}
