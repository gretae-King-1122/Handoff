package com.handoff.hook;

import java.util.Collections;
import java.util.List;

/**
 * 候选表：每个 hook 点登记多组候选（类名 + 方法名 + 参数个数），按序探测，命中即用。
 *
 * <p>QQ 小版本升级通常只动实现不动类名；真改了，往这里加一条候选即可 —— 细节真源是
 * {@code docs/HOOKS.md}。
 */
public final class HookTargets {

    /** 一个 hook 点的候选集合。 */
    public static final class Candidate {
        public final String className;
        public final String methodName;
        public final int paramCount;

        Candidate(String className, String methodName, int paramCount) {
            this.className = className;
            this.methodName = methodName;
            this.paramCount = paramCount;
        }
    }

    /** P0 主攻：static void startAppByLink(Context, String, int, LaunchParam, MiniAppLaunchListener) */
    public static final List<Candidate> P0 = Collections.singletonList(
            new Candidate("com.tencent.mobileqq.mini.sdk.MiniAppController", "startAppByLink", 5)
    );

    /** P1 补充：static boolean openMiniApp(Context, LaunchParam, MiniAppLaunchListener) */
    public static final List<Candidate> P1 = Collections.singletonList(
            new Candidate("com.tencent.mobileqq.mini.sdk.MiniAppLauncher", "openMiniApp", 3)
    );

    /** P2 兜底：AppBrandUI#onCreate(Bundle)；按类名前缀匹配，覆盖 AppBrandUI1~3 */
    public static final String P2_CLASS = "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI";
    public static final String P2_CLASS_PREFIX = "AppBrandUI";
    public static final String P2_METHOD = "onCreate";

    private HookTargets() {
    }
}
