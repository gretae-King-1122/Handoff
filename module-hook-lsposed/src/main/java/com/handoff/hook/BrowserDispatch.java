package com.handoff.hook;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.handoff.api.health.QqAdapters;

/** Browser dispatch policy and mutually exclusive redirect / original branches (no Android types). */
final class BrowserDispatch {
    private static final Set<String> HOSTS = Set.of("bilibili.com", "www.bilibili.com", "m.bilibili.com");
    private static final Pattern VIDEO_PATH = Pattern.compile("/video/(av[0-9]+|BV[0-9A-Za-z]{10})/?");
    private static final Pattern SPACE_PATH = Pattern.compile("/([1-9][0-9]{0,18})/?");
    private static final ThreadLocal<Boolean> IN_DISPATCH = new ThreadLocal<>();

    private BrowserDispatch() { }

    enum Kind { VIDEO, SPACE }
    record Target(Kind kind, String id) { }

    interface Attempt {
        boolean redirect() throws Throwable;
    }

    interface Original {
        Object proceed() throws Throwable;
    }

    static boolean delegationCaller(Class<?> runtimeClass) {
        return delegationCaller(runtimeClass, QqAdapters.STRICT);
    }
    static boolean delegationCaller(Class<?> runtimeClass, QqAdapters.Contract contract) {
        return runtimeClass != null && contract.browserCaller().equals(runtimeClass.getName());
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
        return videoId(receiverClass, targetPackage, targetClass, url, QqAdapters.STRICT);
    }
    static String videoId(String receiverClass, String targetPackage, String targetClass, String url, QqAdapters.Contract contract) {
        Target target = target(receiverClass, targetPackage, targetClass, url, contract);
        return target != null && target.kind() == Kind.VIDEO ? target.id() : null;
    }

    static Target target(String receiverClass, String targetPackage, String targetClass, String url,
                         QqAdapters.Contract contract) {
        if (!contract.browserCaller().equals(receiverClass)
                || !contract.packageName().equals(targetPackage)
                || targetClass == null || !contract.browsers().contains(targetClass) || url == null) return null;
        try {
            URI uri = new URI(url);
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if (!https && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            String host = uri.getHost();
            if (host == null || uri.getRawUserInfo() != null) return null;
            host = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port != -1 && port != (https ? 443 : 80)) return null;
            // Raw path prevents encoded separators / ID prefixes from broadening the allowlist.
            String path = uri.getRawPath();
            if (path == null) return null;
            if ("space.bilibili.com".equals(host)) {
                Matcher space = SPACE_PATH.matcher(path);
                if (!space.matches()) return null;
                // Accept only a positive signed 64-bit UID; do not decode arbitrary paths into schemes.
                Long.parseLong(space.group(1));
                return new Target(Kind.SPACE, space.group(1));
            }
            if (!HOSTS.contains(host)) return null;
            Matcher matcher = VIDEO_PATH.matcher(path);
            return matcher.matches() ? new Target(Kind.VIDEO, matcher.group(1)) : null;
        } catch (URISyntaxException | NumberFormatException ignored) {
            return null;
        }
    }
}
