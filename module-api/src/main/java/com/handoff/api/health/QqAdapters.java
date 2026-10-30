package com.handoff.api.health;

import java.util.List;

/** Source-controlled contracts, never downloaded code or user-supplied reflection targets. */
public final class QqAdapters {
    public record Contract(String browserDispatch, String browserCaller, List<String> browsers,
                           String miniSdk, String miniService, String miniModel, String miniCaller,
                           List<String> containers) {
        public Contract { browsers = List.copyOf(browsers); containers = List.copyOf(containers); }
    }
    public record Profile(String id, String title, String evidence, boolean observed, Contract contract) { }
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
    private QqAdapters() { }
    public static Profile resolve(long versionCode) {
        return versionCode == 16580 ? CURRENT : versionCode == 14540 ? LEGACY : PROBE;
    }
    public static List<Profile> profiles() { return List.of(CURRENT, LEGACY, PROBE); }
}
