package com.handoff.rules.links;

import com.handoff.api.links.LinkParser;
import com.handoff.api.links.ParsedLink;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** First parser pilot: mainland Douyin short shares and explicit video pages only. No networking. */
public final class DouyinLinkParser implements LinkParser {
    private static final int MAX_URL = 4096;
    private static final Set<String> VIDEO_HOSTS = Set.of("douyin.com", "www.douyin.com");
    private static final Pattern SHORT = Pattern.compile("/([A-Za-z0-9_-]{1,128})/?");
    private static final Pattern VIDEO = Pattern.compile("/video/([1-9][0-9]{0,19})/?");

    @Override public ParsedLink parseUrl(String url) {
        if (url == null || url.isEmpty() || url.length() > MAX_URL) return null;
        try {
            URI uri = new URI(url);
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if (!https && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            if (uri.getRawUserInfo() != null || uri.getHost() == null) return null;
            if (uri.getPort() != -1 && uri.getPort() != (https ? 443 : 80)) return null;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath(); // Never decode separators or IDs into the allowlist.
            if (path == null) return null;
            if ("v.douyin.com".equals(host)) {
                Matcher match = SHORT.matcher(path);
                if (match.matches()) return new ParsedLink(ParsedLink.Platform.DOUYIN, ParsedLink.Kind.SHORT_LINK,
                        "https://v.douyin.com/" + match.group(1) + "/", null);
            } else if (VIDEO_HOSTS.contains(host)) {
                Matcher match = VIDEO.matcher(path);
                if (match.matches()) return new ParsedLink(ParsedLink.Platform.DOUYIN, ParsedLink.Kind.VIDEO,
                        "https://www.douyin.com/video/" + match.group(1), match.group(1));
            }
        } catch (URISyntaxException ignored) { }
        return null;
    }

    /**
     * Explicit opt-in for pasted share text, not used on QQ's outgoing URL extra.
     * Accepts one whitespace-delimited URL (with common surrounding punctuation); ambiguous shares fail open.
     */
    public ParsedLink parseShareText(String text) {
        if (text == null || text.length() > 16384) return null;
        ParsedLink result = null;
        int urls = 0;
        for (String token : text.split("(?U)\\s+")) {
            String candidate = stripPunctuation(token);
            if (!candidate.regionMatches(true, 0, "https://", 0, 8)
                    && !candidate.regionMatches(true, 0, "http://", 0, 7)) continue;
            if (++urls > 1) return null;
            result = parseUrl(candidate);
        }
        return result;
    }

    private static String stripPunctuation(String token) {
        int start = 0, end = token.length();
        while (start < end && "（([【<\"'".indexOf(token.charAt(start)) >= 0) start++;
        while (end > start && "）)]】>\"'，。！？；、,.;!?".indexOf(token.charAt(end - 1)) >= 0) end--;
        return token.substring(start, end);
    }
}
