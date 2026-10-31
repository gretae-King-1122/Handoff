package com.handoff.rules.links;

import com.handoff.api.links.LinkParser;
import com.handoff.api.links.ParsedLink;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local parser for the concrete QQ share format supplied by the user; not a generic URL resolver. */
public final class KuaishouLinkParser implements LinkParser {
    private static final Pattern PHOTO = Pattern.compile("/fw/(photo|long-video)/([A-Za-z0-9_-]{1,128})/?");
    private static final Pattern SHORT = Pattern.compile("/([A-Za-z0-9_-]{1,128})/?");

    @Override public ParsedLink parseUrl(String url) {
        if (url == null || url.isEmpty() || url.length() > 4096) return null;
        try {
            URI uri = new URI(url);
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if (!https && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            if (uri.getRawUserInfo() != null) return null;
            if (uri.getPort() != -1 && uri.getPort() != (https ? 443 : 80)) return null;
            String path = uri.getRawPath();
            if (path == null) return null;
            if ("v.kuaishou.com".equalsIgnoreCase(uri.getHost())) {
                Matcher shortLink = SHORT.matcher(path);
                return shortLink.matches() ? new ParsedLink(ParsedLink.Platform.KUAISHOU, ParsedLink.Kind.SHORT_LINK,
                        "https://v.kuaishou.com/" + shortLink.group(1), null) : null;
            }
            if (!"v.m.chenzhongtech.com".equalsIgnoreCase(uri.getHost())) return null;
            Matcher photo = PHOTO.matcher(path);
            if (!photo.matches() || !photoIdMatches(uri.getRawQuery(), photo.group(2))) return null;
            String id = photo.group(2);
            // Keep only the explicit work ID. Never forward shareToken, userId, shareId, efid, etc.
            return new ParsedLink(ParsedLink.Platform.KUAISHOU, ParsedLink.Kind.VIDEO,
                    "https://v.m.chenzhongtech.com/fw/" + photo.group(1) + "/" + id + "?photoId=" + id, id);
        } catch (URISyntaxException ignored) { }
        return null;
    }

    private static boolean photoIdMatches(String query, String id) {
        if (query == null) return true;
        boolean seen = false;
        try {
            for (String pair : query.split("&")) {
                int separator = pair.indexOf('=');
                String key = URLDecoder.decode(separator < 0 ? pair : pair.substring(0, separator), "UTF-8");
                if (!"photoId".equals(key)) continue;
                if (seen || separator < 0) return false;
                seen = true;
                if (!id.equals(URLDecoder.decode(pair.substring(separator + 1), "UTF-8"))) return false;
            }
        } catch (UnsupportedEncodingException | IllegalArgumentException ignored) { return false; }
        return true;
    }

    /** Explicit share-text API only. QQ dispatch still parses just its clicked URL extra. */
    public ParsedLink parseShareText(String text) {
        if (text == null || text.length() > 16384) return null;
        ParsedLink result = null;
        int urls = 0;
        for (String token : text.split("(?U)\\s+")) {
            int start = 0, end = token.length();
            while (start < end && "（([【<\"'".indexOf(token.charAt(start)) >= 0) start++;
            while (end > start && "）)]】>\"'，。！？；、,.;!?".indexOf(token.charAt(end - 1)) >= 0) end--;
            String candidate = token.substring(start, end);
            if (!candidate.regionMatches(true, 0, "https://", 0, 8)
                    && !candidate.regionMatches(true, 0, "http://", 0, 7)) continue;
            if (++urls > 1) return null;
            result = parseUrl(candidate);
        }
        return result;
    }
}
