package com.handoff.api.health;

/** Deliberately no arbitrary message, URL, account, video ID or exception field. */
public record HealthEvent(Kind kind, Entry entry, Reason reason) {
    public enum Kind { SESSION, LOADER_BOUND, INSTALLED, INSTALL_FAILED, ENTERED, PASSED, ACCEPTED, LAUNCH_FAILED }
    public enum Entry {
        SYSTEM("系统", "system"), BROWSER("网页跳板", "browser"), SDK("小程序 SDK", "sdk"),
        SERVICE("小程序服务", "service"), CONTAINER("容器兜底", "container");
        public final String title;
        public final String key;
        Entry(String title, String key) { this.title = title; this.key = key; }
    }
    public enum Reason {
        NONE("无"), DISABLED("该入口已关闭"), CONFIG_UNAVAILABLE("设置不可读取，保守放行"),
        CALLBACK("回调启动，保留 QQ 行为"), CALLER("调用方不在适配范围"),
        INACTIVE("调用方已结束或销毁"), MODEL("身份或视频路径不满足规则"),
        PAYLOAD("容器参数不满足安全条件"), UNREADABLE("参数读取失败"),
        START_FAILED("未能请求打开 B 站"), SIGNATURE("目标签名无法挂载");
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
