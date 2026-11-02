package com.handoff.api;

import java.util.List;

/** Exact host identities. Shared class names do not make QQ and TIM interchangeable. */
public final class HostApps {
    public static final String QQ = "com.tencent.mobileqq";
    public static final String TIM = "com.tencent.tim";
    public static final List<String> PACKAGES = List.of(QQ, TIM);
    private HostApps() { }

    public static boolean isSupported(String packageName) { return PACKAGES.contains(packageName == null ? "" : packageName); }
    public static boolean isMainProcess(String process) { return isSupported(process); }
    public static String packageForProcess(String process) {
        if (process == null) return null;
        int colon = process.indexOf(':');
        String name = colon < 0 ? process : process.substring(0, colon);
        return isSupported(name) ? name : null;
    }
    public static String label(String packageName) {
        return TIM.equals(packageName) ? "TIM" : QQ.equals(packageName) ? "QQ" : "未知宿主";
    }
}
