package com.handoff.rules.links;

import com.handoff.api.links.ParsedLink;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DouyinLinkParserTest {
    private final DouyinLinkParser parser = new DouyinLinkParser();
    private static final String SHORT = "https://v.douyin.com/G7dxP6TRhw4/";

    @Test void extractsTheProvidedShareWithoutTreatingTheShortCodeAsAVideoId() {
        String share = "3.38 复制打开抖音，看看【望左的作品】求了校长一个星期才同意拍的 # 无耻之徒shame... "
                + SHORT + " xSy:/ :4pm U@l.CH 04/25";
        ParsedLink link = parser.parseShareText(share);
        assertNotNull(link);
        assertEquals(ParsedLink.Platform.DOUYIN, link.platform());
        assertEquals(ParsedLink.Kind.SHORT_LINK, link.kind());
        assertEquals(SHORT, link.canonicalUrl());
        assertNull(link.contentId());
        assertNull(parser.parseUrl(share)); // QQ boundary must not search arbitrary text.
    }

    @Test void canonicalizesOnlyWhitelistedUrlsAndDropsTracking() {
        assertEquals(SHORT, parser.parseUrl("HTTP://V.DOUYIN.COM:80/G7dxP6TRhw4?share_token=private#fragment").canonicalUrl());
        assertEquals(SHORT, parser.parseUrl("https://v.douyin.com:443/G7dxP6TRhw4/").canonicalUrl());
        assertEquals("https://v.douyin.com/a_b-C9/", parser.parseUrl("https://v.douyin.com/a_b-C9").canonicalUrl());
        ParsedLink video = parser.parseUrl("https://douyin.com/video/7421234567890123456/?from=qq");
        assertEquals(ParsedLink.Kind.VIDEO, video.kind());
        assertEquals("7421234567890123456", video.contentId());
        assertEquals("https://www.douyin.com/video/7421234567890123456", video.canonicalUrl());
        assertEquals(video, parser.parseUrl(video.canonicalUrl()));
    }

    @Test void rejectsSpoofedNestedEncodedUnsupportedOrOversizedInputs() {
        String[] urls = {null, "", "not a url", " " + SHORT, SHORT + " ",
                "https://v.douyin.com.evil.example/G7dxP6TRhw4/",
                "https://evil.example/?url=" + SHORT,
                "https://evil.example/#" + SHORT,
                "https://v.douyin.com@evil.example/G7dxP6TRhw4/",
                "https://evil.example@v.douyin.com/G7dxP6TRhw4/",
                "https://v.douyin.com:444/G7dxP6TRhw4/", "http://v.douyin.com:443/G7dxP6TRhw4/",
                "https://v.douyin.com./G7dxP6TRhw4/", "https://foo.douyin.com/video/123",
                "javascript://v.douyin.com/G7dxP6TRhw4/", "intent://v.douyin.com/G7dxP6TRhw4/",
                "https://v.douyin.com/%47G7dxP6TRhw4/", "https://v.douyin.com/a%2fb/",
                "https://v.douyin.com/a/b/", "https://v.douyin.com/", "https://v.douyin.com/../abc/",
                "https://v.douyin.com/" + "a".repeat(129), "https://www.douyin.com/video/%31123",
                "https://www.douyin.com/video/0", "https://www.douyin.com/video/00123",
                "https://www.douyin.com/video/123/extra", "https://www.douyin.com/video/" + "1".repeat(21),
                "https://www.douyin.com/user/123", "https://live.douyin.com/123",
                "https://www.bilibili.com/video/BV1xx411c7mD", "https://b23.tv/abc",
                SHORT + "?token=" + "a".repeat(4096), "https://v.douyin.com/abc\\def/"};
        for (String url : urls) assertNull(parser.parseUrl(url), String.valueOf(url));
    }

    @Test void shareTextSupportsCommonPunctuationButRefusesAmbiguousOrNestedLinks() {
        assertEquals(SHORT, parser.parseShareText("看看这个\n（" + SHORT + "），").canonicalUrl());
        assertEquals(SHORT, parser.parseShareText("作品\u3000" + SHORT + "\u3000口令").canonicalUrl());
        assertNull(parser.parseShareText(SHORT + " https://v.douyin.com/another/"));
        assertNull(parser.parseShareText(SHORT + " https://example.com/"));
        assertNull(parser.parseShareText("https://example.com/?url=" + SHORT));
        assertNull(parser.parseShareText("https://example.com/"));
        assertNull(parser.parseShareText("没有链接"));
        assertNull(parser.parseShareText(null));
        assertNull(parser.parseShareText("a".repeat(16385)));
    }
}
