package com.handoff.hook;

import java.util.Set;

/** Replayable mini launch decision; keeps proceed outside the fail-open exception boundary. */
final class MiniAppDispatch {
    private static final ThreadLocal<Boolean> IN_DISPATCH = new ThreadLocal<>();

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
        if (!supported || Boolean.TRUE.equals(IN_DISPATCH.get())) return original.proceed();
        IN_DISPATCH.set(true);
        try {
            boolean accepted = false;
            try {
                MiniAppVideo video = MiniAppVideo.read(model);
                if (video != null) accepted = launcher.launch(video);
            } catch (Throwable ignored) {
                // Extraction / launch errors pass through. QQ's exception must never retry proceed().
            }
            return accepted ? null : original.proceed(); // exact hooked signature returns void
        } finally {
            IN_DISPATCH.remove();
        }
    }
}
