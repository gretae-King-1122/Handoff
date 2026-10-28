package com.handoff.hook;

import java.util.Collections;
import java.util.List;

/**
 * 候选表：每个 hook 点登记多组候选（类名 + 方法名 + 参数个数），按序探测，命中即用。
 *
 * <p>QQ 改版后应核对实际签名并更新候选，再做设备回归；不能仅凭类名不变判断兼容。
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

    /** BrowserHandoff: exact startActivity(Intent[, Bundle]) declarations, scoped to this receiver. */
    public static final String BROWSER_DISPATCH_CLASS = "com.tencent.mobileqq.app.QBaseActivity";
    public static final String BROWSER_DELEGATION_CLASS = "com.tencent.mobileqq.activity.QQBrowserDelegationActivity";

    /** P2 只读观测：精确列出已验证的 AppBrandUI 系列，不 Hook 共享 Activity 基类。 */
    public static final String P2_CLASS = "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI";

    public static final String[] P2_CLASSES = {
            P2_CLASS, P2_CLASS + "1", P2_CLASS + "2", P2_CLASS + "3"
    };

    /** M2.1 只读探针：仅安装 APK 已确认的目标类，不扫描整个进程的类。 */
    public static final String[] RUNTIME_CLASSES = {
            "com.tencent.mobileqq.mini.sdk.MiniAppController",
            "com.tencent.mobileqq.mini.sdk.MiniAppLauncher",
            "com.tencent.mobileqq.mini.launch.MiniSdkLauncher",
            "com.tencent.mobileqq.mini.launch.AppBrandLaunchManager",
            "com.tencent.qqmini.sdk.server.LaunchManagerService",
            "com.tencent.qqmini.sdk.ipc.AppBrandProxy",
            "com.tencent.qqmini.sdk.launcher.dynamic.MiniAppDexLoader",
            P2_CLASS, P2_CLASS + "1", P2_CLASS + "2", P2_CLASS + "3"
    };

    static final class RuntimeTarget {
        final String className;
        final String methodName;
        final String[] parameterTypes;

        RuntimeTarget(String className, String methodName, String... parameterTypes) {
            this.className = className;
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
        }
    }

    private static final String ACTIVITY = "android.app.Activity";
    private static final String CONFIG = "com.tencent.mobileqq.mini.apkg.MiniAppConfig";
    private static final String SDK_INFO = "com.tencent.qqmini.sdk.launcher.model.MiniAppInfo";
    private static final String RECEIVER = "android.os.ResultReceiver";
    private static final String BUNDLE = "android.os.Bundle";

    static final RuntimeTarget[] RUNTIME_METHODS = {
            new RuntimeTarget("com.tencent.mobileqq.mini.sdk.MiniAppController", "startApp",
                    ACTIVITY, CONFIG, RECEIVER),
            new RuntimeTarget("com.tencent.mobileqq.mini.sdk.MiniAppLauncher", "launchAppByAppConfigWithoutCheck",
                    ACTIVITY, CONFIG, RECEIVER),
            new RuntimeTarget("com.tencent.mobileqq.mini.launch.MiniSdkLauncher", "startMiniApp",
                    ACTIVITY, CONFIG, BUNDLE, RECEIVER),
            new RuntimeTarget("com.tencent.mobileqq.mini.launch.AppBrandLaunchManager", "startMiniApp",
                    ACTIVITY, CONFIG, RECEIVER),
            new RuntimeTarget("com.tencent.qqmini.sdk.server.LaunchManagerService", "startMiniApp",
                    ACTIVITY, SDK_INFO, BUNDLE, RECEIVER),
            new RuntimeTarget("com.tencent.qqmini.sdk.ipc.AppBrandProxy", "onAppShowOrHideWithPath",
                    "int", "java.lang.String", SDK_INFO, "java.lang.String")
    };

    private HookTargets() {
    }
}
