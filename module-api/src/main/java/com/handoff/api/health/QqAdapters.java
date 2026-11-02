package com.handoff.api.health;

import java.util.List;
import com.handoff.api.HostApps;

/** Source-controlled contracts, never downloaded code or user-supplied reflection targets. */
public final class QqAdapters {
    public record Contract(String browserDispatch, String browserCaller, List<String> browsers,
                           String miniSdk, String miniService, String miniModel, String miniCaller,
                           List<String> containers, String packageName) {
        public Contract { browsers = List.copyOf(browsers); containers = List.copyOf(containers); }
        public Contract(String browserDispatch, String browserCaller, List<String> browsers,
                        String miniSdk, String miniService, String miniModel, String miniCaller, List<String> containers) {
            this(browserDispatch, browserCaller, browsers, miniSdk, miniService, miniModel, miniCaller, containers, HostApps.QQ);
        }
    }
    public record Profile(String id, String title, String evidence, boolean observed, Contract contract, String packageName) {
        public Profile(String id, String title, String evidence, boolean observed, Contract contract) {
            this(id, title, evidence, observed, contract, contract.packageName());
        }
    }
    public static final Contract STRICT = new Contract(
            "com.tencent.mobileqq.app.QBaseActivity", "com.tencent.mobileqq.activity.QQBrowserDelegationActivity",
            List.of("com.tencent.mobileqq.activity.QQBrowserActivity", "com.tencent.mobileqq.activity.QQH5BrowserActivity",
                    "com.tencent.mobileqq.activity.QQTranslucentBrowserActivity"),
            "com.tencent.qqmini.sdk.MiniSDK", "com.tencent.qqmini.sdk.server.LaunchManagerService",
            "com.tencent.qqmini.sdk.launcher.model.MiniAppInfo", "com.tencent.mobileqq.activity.QPublicTransFragmentActivity",
            List.of("com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI", "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI1",
                    "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI2", "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI3"));
    private static final Profile CURRENT = new Profile("qq-9.3.75", "QQ 9.3.75 · 16580",
            "开发设备记录：网页及 SDK 小程序交接成功；不代表当前设备已验证。", true, STRICT);
    private static final Profile LEGACY = new Profile("qq-9.3.5", "QQ 9.3.5 · 14540",
            "历史记录：观察到服务签名与视频字段；未完成当前版本的全流程回归。", false, STRICT);
    private static final Profile PROBE = new Profile("strict-probe", "未知版本 · 严格签名探测",
            "仅尝试已知契约，未知签名失败即放行；不能据版本号判断兼容。", false, STRICT);
    // TIM 4.1.0 / 4050 ships these same class names, but its explicit components belong to TIM.
    // APK SHA-256 and the independently checked signatures are recorded in TIM_SUPPORT.md.
    public static final Contract TIM_STRICT = new Contract(STRICT.browserDispatch(), STRICT.browserCaller(), STRICT.browsers(),
            STRICT.miniSdk(), STRICT.miniService(), STRICT.miniModel(), STRICT.miniCaller(), STRICT.containers(), HostApps.TIM);
    private static final Profile TIM_CURRENT = new Profile("tim-4.1.0", "TIM 4.1.0 · 4050",
            "本地 APK 静态契约适配；尚未完成真机交接回归。", false, TIM_STRICT);
    private static final Profile TIM_PROBE = new Profile("tim-strict-probe", "TIM 未知版本 · 严格签名探测",
            "仅尝试 TIM 已知契约，未知签名失败即放行；尚未验证此版本。", false, TIM_STRICT);
    private QqAdapters() { }
    public static Profile resolve(long versionCode) {
        return versionCode == 16580 ? CURRENT : versionCode == 14540 ? LEGACY : PROBE;
    }
    public static Profile resolve(String packageName, long versionCode) {
        if (HostApps.QQ.equals(packageName)) return resolve(versionCode);
        if (HostApps.TIM.equals(packageName)) return versionCode == 4050 ? TIM_CURRENT : TIM_PROBE;
        throw new IllegalArgumentException("Unsupported host");
    }
    public static List<Profile> profiles() { return List.of(CURRENT, LEGACY, PROBE, TIM_CURRENT, TIM_PROBE); }
}
