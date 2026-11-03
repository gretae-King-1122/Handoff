package com.handoff.hook;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Replayable mini launch decision; keeps proceed outside the fail-open exception boundary. */
final class MiniAppDispatch {
    private static final ThreadLocal<DispatchState> IN_DISPATCH = new ThreadLocal<>();

    private static final class DispatchState {
        final Map<String, Boolean> outcomes = new HashMap<>();
        boolean launching;
    }

    private MiniAppDispatch() { }

    interface Launcher { boolean launch(MiniAppVideo video) throws Throwable; }
    interface Original { Object proceed() throws Throwable; }

    static boolean supportedCaller(String activityClass, boolean hasReceiver, Set<String> startExtraKeys) {
        return callerRejectionReason(activityClass, hasReceiver, startExtraKeys) == null;
    }

    static String callerRejectionReason(String activityClass, boolean hasReceiver, Set<String> startExtraKeys) {
        return callerRejectionReason(activityClass, hasReceiver, startExtraKeys, HookTargets.MINI_CALLER_CLASS);
    }
    static String callerRejectionReason(String activityClass, boolean hasReceiver, Set<String> startExtraKeys, String callerClass) {
        // Ordinary metadata is not a different launch contract. Only model/callback overrides matter.
        if (!callerClass.equals(activityClass)) return "unknown-activity";
        if (startExtraKeys == null) return "unreadable-extras";
        if (hasReceiver || startExtraKeys.contains("receiver")) return "callback";
        if (startExtraKeys.contains("KEY_APPINFO")) return "overridden-model";
        return null;
    }

    static Object intercept(Object model, boolean supported, Launcher launcher, Original original) throws Throwable {
        if (!supported) return original.proceed();
        DispatchState state = IN_DISPATCH.get();
        boolean root = state == null;
        if (root) {
            state = new DispatchState();
            IN_DISPATCH.set(state);
        }
        try {
            boolean accepted = tryLaunch(model, launcher, state);
            return accepted ? null : original.proceed(); // exact hooked signature returns void
        } finally {
            if (root) IN_DISPATCH.remove();
        }
    }

    private static boolean tryLaunch(Object model, Launcher launcher, DispatchState state) {
        try {
            if (state.launching) return false;
            MiniAppVideo video = MiniAppVideo.read(model);
            if (video == null) return false;
            Boolean previous = state.outcomes.get(video.id());
            if (previous != null) return previous;
            return launchOnce(video, launcher, state);
        } catch (Throwable ignored) {
            // Only extraction/launch errors fail open. Host errors propagate through intercept.
            return false;
        }
    }

    private static boolean launchOnce(MiniAppVideo video, Launcher launcher, DispatchState state) throws Throwable {
        // Record failure before external code: throwing/reentrant launchers must not repeat attempts.
        state.outcomes.put(video.id(), false);
        state.launching = true;
        try {
            boolean accepted = launcher.launch(video);
            if (accepted) state.outcomes.put(video.id(), true);
            return accepted;
        } finally {
            state.launching = false;
        }
    }
}
