package com.handoff.api.links;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Strict web-only input policy. Existing platform routes are never consumed by dynamic handoff. */
public final class DynamicLinkPolicy {
    private static final Set<String> RESERVED = Set.of("bilibili.com", "b23.tv", "bili2233.cn",
            "douyin.com", "iesdouyin.com", "kuaishou.com", "chenzhongtech.com", "gifshow.com");
    private static final Set<String> PACKAGES = Set.of("com.tencent.mobileqq", "com.handoff.module",
            "tv.danmaku.bili", "com.ss.android.ugc.aweme", "com.smile.gifmaker");
    private DynamicLinkPolicy() { }

    /** Keep path/query semantics (including share credentials); never log or persist this value. */
    public static String normalize(String input) {
        if (input == null || input.length() > 4096 || !input.equals(input.trim())) return null;
        try {
            URI uri = new URI(input);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
            host = host.toLowerCase(Locale.ROOT);
            // DNS names only; local names, IP literals and ambiguous numeric forms are not web shares.
            if (host.length() > 253 || !host.contains(".") || host.endsWith(".")
                    || !host.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+") || host.matches("[0-9.]+")
                    || host.endsWith(".local") || host.endsWith(".localhost") || host.endsWith(".internal")
                    || host.endsWith(".lan") || host.endsWith(".home") || host.endsWith(".invalid")) return null;
            for (String label : host.split("\\."))
                if (label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return null;
            for (String reserved : RESERVED)
                if (host.equals(reserved) || host.endsWith("." + reserved)) return null;
            // Fragments are local App Link state and must survive a direct handoff.
            return "https://" + host + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
        } catch (Exception ignored) { return null; }
    }

    public static boolean allowedPackage(String name) { return name != null && !PACKAGES.contains(name); }
}
