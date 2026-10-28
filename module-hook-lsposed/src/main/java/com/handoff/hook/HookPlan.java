package com.handoff.hook;

import java.util.List;

/** Keep discovery-only work out of the normal handoff path; shared by installation and tests. */
final class HookPlan {
    enum Step { BROWSER, CONTROLLER, LAUNCHER, MINI_RUNTIME, ARK_CLICK, MINI_ENTRY, ARK_MESSAGE, TEMPLATE, STRUCT }

    private static final List<Step> NORMAL_MAIN = List.of(Step.BROWSER);
    private static final List<Step> DIAGNOSTIC_MAIN = List.of(Step.BROWSER, Step.CONTROLLER, Step.LAUNCHER,
            Step.MINI_RUNTIME, Step.ARK_CLICK, Step.MINI_ENTRY, Step.ARK_MESSAGE, Step.TEMPLATE, Step.STRUCT);
    private static final List<Step> DIAGNOSTIC_AUX = List.of(Step.CONTROLLER, Step.LAUNCHER, Step.MINI_RUNTIME);

    private HookPlan() { }

    static List<Step> steps(String process, boolean diagnostics) {
        boolean main = "com.tencent.mobileqq".equals(process);
        if (main) return diagnostics ? DIAGNOSTIC_MAIN : NORMAL_MAIN;
        // Unknown / unrelated processes aren't assumed to be QQ's main process.
        if (diagnostics && process != null && process.startsWith("com.tencent.mobileqq:")) return DIAGNOSTIC_AUX;
        return List.of();
    }
}
