package com.handoff.hook;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Narrow policy for the resolved SDK model, not for arbitrary links or probe events. */
record MiniAppVideo(String id, String source) {
    private static final String BILI_APP_ID = "1109937557";
    private static final Pattern BVID = Pattern.compile("BV[0-9A-Za-z]{10}");
    private static final Pattern AID = Pattern.compile("[1-9][0-9]{0,18}");

    String url() { return "https://www.bilibili.com/video/" + id; }

    static MiniAppVideo read(Object info) {
        if (!BILI_APP_ID.equals(string(info, "appId"))) return null;
        Object launch = Reflect.get(info, "launchParam");
        String launchId = string(launch, "miniAppId");
        if (launchId != null && !BILI_APP_ID.equals(launchId)) return null;
        String launchPath = string(launch, "entryPath");
        String firstPath = string(Reflect.get(info, "firstPage"), "pagePath");
        String firstId = parse(firstPath);
        // A populated entryPath is authoritative: don't replace an unsupported page with stale firstPage.
        if (launchPath != null) {
            String id = parse(launchPath);
            if (id == null || (firstId != null && !id.equals(firstId))) return null;
            return new MiniAppVideo(id, "launchParam.entryPath");
        }
        return firstId == null ? null : new MiniAppVideo(firstId, "firstPage.pagePath");
    }

    private static String string(Object object, String field) {
        Object value = Reflect.get(object, field);
        return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
    }

    private static String parse(String path) {
        if (path == null || path.length() > 4096) return null;
        try {
            URI uri = new URI(path);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null) return null;
            String page = uri.getRawPath();
            if (!"pages/video/video".equals(page) && !"pages/video/video.html".equals(page)) return null;
            String query = uri.getRawQuery();
            if (query == null) return null;
            String id = null;
            for (String part : query.split("&")) {
                int equals = part.indexOf('=');
                String key = decode(equals < 0 ? part : part.substring(0, equals));
                if (!key.equals("bvid") && !key.equals("aid")) continue;
                // Duplicate identifiers, including aid+bvid, are ambiguous without conversion.
                if (id != null || equals < 0) return null;
                String value = decode(part.substring(equals + 1));
                if (key.equals("bvid")) {
                    if (!BVID.matcher(value).matches()) return null;
                    id = value;
                } else {
                    if (!AID.matcher(value).matches() || Long.parseLong(value) <= 0) return null;
                    id = "av" + value;
                }
            }
            return id;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String decode(String value) throws java.io.UnsupportedEncodingException {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
    }
}
