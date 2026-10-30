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
        OTHER("普通链接放行", "点击非 B 站的普通网页；确认 QQ 原流程仍可使用。", false),
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
                && (test == Case.WEB ? e.event().entry() == HealthEvent.Entry.BROWSER
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
