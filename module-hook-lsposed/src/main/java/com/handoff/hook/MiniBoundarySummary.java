package com.handoff.hook;

/** Debug-only model summary. Never include raw paths, identifiers, queries or object toString(). */
final class MiniBoundarySummary {
    private MiniBoundarySummary() { }

    static String describe(Object model) {
        try {
            Object launch = Reflect.get(model, "launchParam");
            Object appId = Reflect.get(model, "appId");
            Object launchId = Reflect.get(launch, "miniAppId");
            Object launchPath = Reflect.get(launch, "entryPath");
            Object firstPath = Reflect.get(Reflect.get(model, "firstPage"), "pagePath");
            String result;
            if (!"1109937557".equals(appId)) result = "app-identity";
            else if (launchId instanceof String && !((String) launchId).isEmpty()
                    && !"1109937557".equals(launchId)) result = "launch-identity-conflict";
            else if (MiniAppVideo.read(model) != null) result = "accepted";
            else if (launchPath instanceof String && !((String) launchPath).isEmpty()) {
                result = MiniAppVideo.parse((String) launchPath) == null
                        ? "authoritative-path-rejected" : "video-conflict";
            } else result = "first-page-rejected";
            return "appId=" + identity(appId) + " launchId=" + identity(launchId)
                    + " entryPath=" + path(launchPath) + " firstPage=" + path(firstPath)
                    + " modelResult=" + result;
        } catch (Throwable ignored) {
            return "modelResult=diagnostic-unavailable";
        }
    }

    private static String identity(Object value) {
        if (value == null) return "missing";
        if (!(value instanceof String)) return "not-string";
        if (((String) value).isEmpty()) return "empty";
        return "1109937557".equals(value) ? "bili" : "other";
    }

    private static String path(Object value) {
        if (value == null) return "missing";
        if (!(value instanceof String)) return "not-string";
        if (((String) value).isEmpty()) return "empty";
        return MiniAppVideo.parse((String) value) == null ? "rejected" : "valid-video";
    }
}
