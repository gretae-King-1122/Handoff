package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import com.handoff.api.health.HealthEvent;
import java.util.function.Consumer;

/** Testable final-dispatch boundary. An Intent is not trusted merely because it has KEY_APPINFO. */
final class MiniContainerDispatch {
    record Caller(String className, boolean finishing, boolean destroyed) { }
    record Envelope(Caller caller, String targetPackage, String targetClass, int requestCode,
                    boolean forwardsResult, boolean hasSelector) { }
    record Payload(boolean sdkMode, Object receiver, Object model) { }
    interface Reader { Payload read() throws Throwable; }

    private MiniContainerDispatch() { }

    static boolean supported(Envelope envelope) {
        return supported(envelope, QqAdapters.STRICT);
    }
    static boolean supported(Envelope envelope, QqAdapters.Contract contract) {
        // This is an outgoing launch, not a lifecycle callback. QQ can finish its preload
        // Activity before a posted launch executes; finishing alone must not hide it.
        // Keep the early SDK/service entry's stricter active-Activity policy separate.
        if (envelope == null || envelope.caller() == null
                || !contract.miniCaller().equals(envelope.caller().className())
                || envelope.caller().destroyed()
                || !"com.tencent.mobileqq".equals(envelope.targetPackage())
                || envelope.requestCode() >= 0 || envelope.forwardsResult() || envelope.hasSelector()) return false;
        return contract.containers().contains(envelope.targetClass());
    }

    static Object intercept(Envelope envelope, Reader reader, MiniAppDispatch.Launcher launcher,
                            MiniAppDispatch.Original original) throws Throwable {
        return intercept(envelope, reader, launcher, original, QqAdapters.STRICT);
    }
    static Object intercept(Envelope envelope, Reader reader, MiniAppDispatch.Launcher launcher,
                            MiniAppDispatch.Original original, QqAdapters.Contract contract) throws Throwable {
        return intercept(envelope, reader, launcher, original, contract, ignored -> { });
    }

    static Object intercept(Envelope envelope, Reader reader, MiniAppDispatch.Launcher launcher,
                            MiniAppDispatch.Original original, QqAdapters.Contract contract,
                            Consumer<HealthEvent.Reason> rejected) throws Throwable {
        // Most Activity calls aren't our route. Don't inspect or unmarshal their extras.
        if (!supported(envelope, contract)) return original.proceed();
        MiniAppVideo video = null;
        boolean supported = false;
        HealthEvent.Reason reason = HealthEvent.Reason.PAYLOAD;
        try {
            Payload payload = reader.read();
            Object model = payload == null ? null : payload.model();
            // The SDK normally writes receiver=null; that key alone isn't a callback.
            supported = payload != null && payload.sdkMode() && payload.receiver() == null
                    && model != null && contract.miniModel().equals(model.getClass().getName());
            if (supported) video = MiniAppVideo.read(model);
        } catch (Throwable ignored) { reason = HealthEvent.Reason.UNREADABLE; }
        // Logging observes the single validation result, never parses the mutable QQ model again.
        if (video == null) {
            try { rejected.accept(reason); } catch (Throwable ignored) { }
        }
        return MiniAppDispatch.interceptResolved(video, supported, launcher, original);
    }
}
