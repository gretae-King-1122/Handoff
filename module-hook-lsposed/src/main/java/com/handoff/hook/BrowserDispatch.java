package com.handoff.hook;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Browser dispatch policy and mutually exclusive redirect / original branches (no Android types). */
final class BrowserDispatch {
    private static final Set<String> BROWSERS = Set.of(
            "com.tencent.mobileqq.activity.QQBrowserActivity",
            "com.tencent.mobileqq.activity.QQH5BrowserActivity",
            "com.tencent.mobileqq.activity.QQTranslucentBrowserActivity");
    private static final Set<String> HOSTS = Set.of("bilibili.com", "www.bilibili.com", "m.bilibili.com");
    private static final Pattern VIDEO_PATH = Pattern.compile("/video/(av[0-9]+|BV[0-9A-Za-z]{10})/?");
    private static final ThreadLocal<Boolean> IN_DISPATCH = new ThreadLocal<>();

    private BrowserDispatch() { }

    interface Attempt {
        boolean redirect() throws Throwable;
    }

    interface Original {
        Object proceed() throws Throwable;
    }

    static boolean delegationCaller(Class<?> runtimeClass) {
        return runtimeClass != null && HookTargets.BROWSER_DELEGATION_CLASS.equals(runtimeClass.getName());
    }

    static Object intercept(Attempt attempt, Original original) throws Throwable {
        // QQ overloads may call each other; the external start can also re-enter the hook.
        if (Boolean.TRUE.equals(IN_DISPATCH.get())) return original.proceed();
        IN_DISPATCH.set(true);
        try {
            boolean accepted = false;
            try {
                accepted = attempt.redirect();
            } catch (Throwable ignored) {
                // Only extraction / redirect errors fail open. Never catch and retry QQ's original.
            }
            if (accepted) return null; // hooked startActivity returns void
            return original.proceed();
        } finally {
            IN_DISPATCH.remove();
        }
    }

    static String videoId(String receiverClass, String targetPackage, String targetClass, String url) {
        if (!HookTargets.BROWSER_DELEGATION_CLASS.equals(receiverClass)
                || !"com.tencent.mobileqq".equals(targetPackage)
                || targetClass == null || !BROWSERS.contains(targetClass) || url == null) return null;
        try {
            URI uri = new URI(url);
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if (!https && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            String host = uri.getHost();
            if (host == null || !HOSTS.contains(host.toLowerCase(Locale.ROOT))
                    || uri.getRawUserInfo() != null) return null;
            int port = uri.getPort();
            if (port != -1 && port != (https ? 443 : 80)) return null;
            // Raw path prevents encoded separators / ID prefixes from broadening the allowlist.
            String path = uri.getRawPath();
            if (path == null) return null;
            Matcher matcher = VIDEO_PATH.matcher(path);
            return matcher.matches() ? matcher.group(1) : null;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }
}
