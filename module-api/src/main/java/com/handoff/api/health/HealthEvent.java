package com.handoff.api.health;

/** Deliberately no arbitrary message, URL, account, video ID or exception field. */
public record HealthEvent(Kind kind, Entry entry, Reason reason) {
    public enum Kind { SESSION, LOADER_BOUND, INSTALLED, INSTALL_FAILED, ENTERED, PASSED, ACCEPTED, LAUNCH_FAILED }
    public enum Entry {
        SYSTEM("系统", "system"), BROWSER("网页跳板", "browser"), SDK("小程序 SDK", "sdk"),
        SERVICE("小程序服务", "service"), CONTAINER("容器兜底", "container"),
        DOUYIN("抖音链接（试验）", "douyin"), KUAISHOU("快手链接（试验）", "kuaishou"),
        DYNAMIC("动态链接交接（试验）", "dynamic");
        public final String title;
        public final String key;
        Entry(String title, String key) { this.title = title; this.key = key; }
        /** The new pilot is opt-in; existing entries retain their original defaults. */
        public boolean defaultEnabled() { return this != DOUYIN && this != KUAISHOU && this != DYNAMIC; }
    }
    public enum Reason {
        NONE("无"), DISABLED("该入口已关闭"), CONFIG_UNAVAILABLE("设置不可读取，保守放行"),
        CALLBACK("回调启动，保留 QQ 行为"), CALLER("调用方不在适配范围"),
        INACTIVE("调用方已结束或销毁"), MODEL("身份或视频路径不满足规则"),
        PAYLOAD("容器参数不满足安全条件"), UNREADABLE("参数读取失败"),
        START_FAILED("未能请求打开 B 站"), DOUYIN_START_FAILED("未能请求打开抖音，保留 QQ 流程"),
        KUAISHOU_START_FAILED("未能请求打开快手，保留 QQ 流程"),
        KUAISHOU_RESOLVE_FAILED("快手短链解析或启动失败，已返回 QQ 浏览器"),
        KUAISHOU_CANCELLED("用户取消快手短链解析"),
        KUAISHOU_FALLBACK_FAILED("快手短链交接失败，QQ 浏览器回退也未成功"),
        DYNAMIC_START_FAILED("动态交接页未能启动，保留 QQ 流程"),
        DYNAMIC_RESOLVE_FAILED("未找到唯一目标或解析／启动失败，已返回 QQ 浏览器"),
        DYNAMIC_CANCELLED("用户取消动态链接交接"),
        DYNAMIC_FALLBACK_FAILED("动态交接失败，QQ 浏览器回退也未成功"),
        SIGNATURE("目标签名无法挂载");
        public final String title;
        Reason(String title) { this.title = title; }
    }
    public HealthEvent {
        if (kind == null || entry == null || reason == null) throw new IllegalArgumentException("Missing enum");
    }
    public static HealthEvent of(Kind kind, Entry entry) { return new HealthEvent(kind, entry, Reason.NONE); }
    public static Entry entry(String value) {
        if ("container-dispatch".equals(value)) return Entry.CONTAINER;
        for (Entry entry : Entry.values()) if (entry.key.equals(value)) return entry;
        return Entry.SYSTEM;
    }
}
