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
        // Ordinary metadata is not a different launch contract. Only model/callback overrides matter.
        if (!HookTargets.MINI_CALLER_CLASS.equals(activityClass)) return "unknown-activity";
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
            boolean accepted = false;
            try {
                if (!state.launching) {
                    MiniAppVideo video = MiniAppVideo.read(model);
                    if (video != null) {
                        Boolean previous = state.outcomes.get(video.id());
                        if (previous != null) accepted = previous;
                        else {
                            // Mark failure before invoking external code, including throwing launchers.
                            state.outcomes.put(video.id(), false);
                            state.launching = true;
                            try {
                                accepted = launcher.launch(video);
                                if (accepted) state.outcomes.put(video.id(), true);
                            } finally { state.launching = false; }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Extraction / launch errors pass through. QQ's exception must never retry proceed().
            }
            return accepted ? null : original.proceed(); // exact hooked signature returns void
        } finally {
            if (root) IN_DISPATCH.remove();
        }
    }
}
