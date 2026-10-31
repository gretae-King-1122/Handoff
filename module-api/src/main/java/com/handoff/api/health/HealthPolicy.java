package com.handoff.api.health;

import java.util.List;

public final class HealthPolicy {
    public record Environment(long module, long qq, int android, String model, String framework) {
        public String key() { return module + ":" + qq + ":" + android + ":" + safe(model) + ":" + safe(framework); }
    }
    public record Evidence(long at, long module, long qq, HealthEvent event, String framework) { }
    public enum Case {
        WEB("网页视频", "点击普通 B 站视频卡片；确认只交给 B 站，不并行打开 QQ 网页。", true),
        MINI("小程序视频", "点击 B 站小程序视频卡片；确认进入 B 站，而不是 QQ 小程序。", true),
        DOUYIN("抖音明文链接", "开启抖音试验，点击 QQ 中的抖音短链；确认进入对应作品，而非首页，且未并行打开 QQ 网页。", true),
        KUAISHOU("快手作品链接", "开启快手试验，点击 QQ 中的快手作品分享地址；确认进入快手对应作品，且未并行打开 QQ 网页。", true),
        DYNAMIC("动态链接交接", "开启动态交接，点击其他已安装 App 声明接收的 HTTPS 内容链接；确认进入对应内容且未并行打开 QQ 网页。通用短链需另开联网展开。", true),
        OTHER("普通链接放行", "点击没有目标 App 接收的普通网页；确认最终回到 QQ 原流程。动态联网展开关闭时不应为此联网。", false),
        UNKNOWN("未知内容放行", "使用非视频内容或不支持的小程序页面；确认不会被错误拦截。", false);
        public final String title, instruction;
        public final boolean needsEvidence;
        Case(String title, String instruction, boolean needsEvidence) {
            this.title = title; this.instruction = instruction; this.needsEvidence = needsEvidence;
        }
    }
    private HealthPolicy() { }
    public static boolean mayConfirm(Case test, long started, Environment environment, List<Evidence> evidence) {
        if (!test.needsEvidence) return true;
        if (environment.qq() <= 0 || started <= 0 || "unknown".equals(environment.framework())) return false;
        return evidence.stream().anyMatch(e -> e.at() >= started && e.module() == environment.module()
                && e.qq() == environment.qq() && e.event().kind() == HealthEvent.Kind.ACCEPTED
                && safe(e.framework()).equals(safe(environment.framework()))
                && (test == Case.DOUYIN ? e.event().entry() == HealthEvent.Entry.DOUYIN
                : test == Case.KUAISHOU ? e.event().entry() == HealthEvent.Entry.KUAISHOU
                : test == Case.DYNAMIC ? e.event().entry() == HealthEvent.Entry.DYNAMIC
                : test == Case.WEB ? e.event().entry() == HealthEvent.Entry.BROWSER
                : List.of(HealthEvent.Entry.SDK, HealthEvent.Entry.SERVICE, HealthEvent.Entry.CONTAINER).contains(e.event().entry())));
    }
    public static boolean mayReport(int callerUid, int ownUid, String[] packages) {
        if (callerUid == ownUid) return true;
        if (packages == null) return false;
        for (String name : packages) if ("com.tencent.mobileqq".equals(name)) return true;
        return false;
    }
    public static String safe(String value) {
        if (value == null) return "unknown";
        String cleaned = value.replaceAll("[^A-Za-z0-9 ._:/()-]", "_");
        return cleaned.substring(0, Math.min(80, cleaned.length()));
    }
}
