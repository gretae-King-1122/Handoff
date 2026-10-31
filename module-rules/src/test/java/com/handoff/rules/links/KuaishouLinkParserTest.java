package com.handoff.rules.links;

import com.handoff.api.links.ParsedLink;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KuaishouLinkParserTest {
    private static final String ID = "3xSampleVideo123";
    private static final String PAGE = "https://v.m.chenzhongtech.com/fw/photo/" + ID;
    private static final String CANONICAL = PAGE + "?photoId=" + ID;
    private final KuaishouLinkParser parser = new KuaishouLinkParser();

    @Test void recognizesTheUserSuppliedShareShapeWithoutRetainingPrivateShareParameters() {
        // Same structure as the supplied QQ share; all account IDs and tokens are synthetic fixtures.
        String shared = PAGE + "?cc=share_qqms&followRefer=151&shareMethod=CARD&docId=9&kpn=KUAISHOU"
                + "&subBiz=BROWSE_SLIDE_PHOTO&photoId=" + ID + "&shareId=fixture&shareToken=private"
                + "&shareResourceType=PHOTO_OTHER&userId=fixture&shareType=1&et=1_a%2Ffixture_bs%24s"
                + "&shareMode=APP&efid=fixture&originShareId=fixture&appType=21"
                + "&shareObjectId=123&shareUrlOpened=0&timestamp=123&qq_aio_chat_type=2";
        ParsedLink link = parser.parseUrl(shared);
        assertNotNull(link);
        assertEquals(ParsedLink.Platform.KUAISHOU, link.platform());
        assertEquals(ParsedLink.Kind.VIDEO, link.kind());
        assertEquals(ID, link.contentId());
        assertEquals(CANONICAL, link.canonicalUrl());
        assertFalse(link.toString().contains("private"));
        assertEquals(link, parser.parseUrl(CANONICAL));
    }

    @Test void canonicalizesHostPortAndPathWithoutDependingOnTrackingParameters() {
        assertEquals(CANONICAL, parser.parseUrl(PAGE).canonicalUrl());
        assertEquals(CANONICAL, parser.parseUrl(PAGE + "/#reply").canonicalUrl());
        assertEquals(CANONICAL, parser.parseUrl("HTTP://V.M.CHENZHONGTECH.COM:80/fw/photo/" + ID).canonicalUrl());
        assertEquals(CANONICAL, parser.parseUrl("https://v.m.chenzhongtech.com:443/fw/photo/" + ID).canonicalUrl());
        assertEquals(CANONICAL, parser.parseUrl(PAGE + "?photo%49d=3xSampleVideo%31%32%33").canonicalUrl());
    }

    @Test void refusesConflictingOrDuplicatePhotoIdsIncludingEncodedParameterNames() {
        for (String query : new String[]{"photoId=other", "photoId=", "photoId", "photoId=" + ID + "&photoId=" + ID,
                "photoId=" + ID + "&photo%49d=other", "photoId=" + ID + "%2Fextra", "photoId=" + ID + "+"})
            assertNull(parser.parseUrl(PAGE + "?" + query), query);
    }

    @Test void rejectsSpoofedUrlsOtherContentAndUnknownShortLinkHosts() {
        String[] inputs = {null, "", "invalid", " " + PAGE, PAGE + " ",
                "https://v.m.chenzhongtech.com.evil.example/fw/photo/" + ID,
                "https://evil.example/?url=" + PAGE, "https://evil.example/#" + PAGE,
                "https://v.m.chenzhongtech.com@evil.example/fw/photo/" + ID,
                "https://evil.example@v.m.chenzhongtech.com/fw/photo/" + ID,
                "https://v.m.chenzhongtech.com:444/fw/photo/" + ID,
                "http://v.m.chenzhongtech.com:443/fw/photo/" + ID,
                "https://v.m.chenzhongtech.com./fw/photo/" + ID,
                "https://another.chenzhongtech.com/fw/photo/" + ID,
                "javascript://v.m.chenzhongtech.com/fw/photo/" + ID,
                "intent://v.m.chenzhongtech.com/fw/photo/" + ID,
                "https://v.m.chenzhongtech.com/fw/photo/%33xSampleVideo123",
                "https://v.m.chenzhongtech.com/fw/photo/id%2fextra",
                "https://v.m.chenzhongtech.com/fw/photo/../id", PAGE + "/extra",
                "https://v.m.chenzhongtech.com/fw/photo/", "https://v.m.chenzhongtech.com/fw/user/" + ID,
                "https://v.m.chenzhongtech.com/fw/photo/" + "a".repeat(129),
                "https://k.ksurl.cn/AbC123",
                "https://www.kuaishou.com/short-video/" + ID,
                "https://www.bilibili.com/video/BV1xx411c7mD", "https://v.douyin.com/G7dxP6TRhw4/",
                PAGE + "?token=" + "a".repeat(4096), PAGE + "?photoId=%invalid"};
        for (String input : inputs) assertNull(parser.parseUrl(input), String.valueOf(input));
    }

    @Test void recognizesShortLinksWithoutInventingAContentIdOrKeepingTracking() {
        for (String url : new String[]{"https://v.kuaishou.com/AbC123", "HTTP://V.KUAISHOU.COM:80/AbC123/?token=private#fragment"}) {
            ParsedLink link = parser.parseUrl(url);
            assertEquals(ParsedLink.Kind.SHORT_LINK, link.kind());
            assertEquals("https://v.kuaishou.com/AbC123", link.canonicalUrl());
            assertNull(link.contentId());
        }
        for (String url : new String[]{"https://v.kuaishou.com/", "https://v.kuaishou.com/a/b",
                "https://v.kuaishou.com/%41bC123", "https://v.kuaishou.com.evil.test/AbC123",
                "https://evil@v.kuaishou.com/AbC123", "https://v.kuaishou.com:444/AbC123"})
            assertNull(parser.parseUrl(url), url);
    }

    @Test void preservesTheObservedLongVideoRouteWithTheSameIdValidation() {
        String page = PAGE.replace("/photo/", "/long-video/");
        assertEquals(page + "?photoId=" + ID, parser.parseUrl(page + "?shareToken=private").canonicalUrl());
        assertNull(parser.parseUrl(page + "?photoId=other"));
        assertNull(parser.parseUrl(page + "/extra"));
    }

    @Test void explicitlyParsesSingleUrlShareTextButNeverSearchesNestedUrlsAtTheQqBoundary() {
        assertEquals(CANONICAL, parser.parseShareText("看看这个作品\n（" + PAGE + "），").canonicalUrl());
        assertEquals(CANONICAL, parser.parseShareText("快手\u3000" + CANONICAL + "\u3000打开看看").canonicalUrl());
        assertNull(parser.parseUrl("快手分享 " + PAGE));
        assertNull(parser.parseShareText(PAGE + " https://example.com/"));
        assertNull(parser.parseShareText(PAGE + " " + PAGE));
        assertNull(parser.parseShareText("https://example.com/?url=" + PAGE));
        assertNull(parser.parseShareText("没有链接"));
        assertNull(parser.parseShareText(null));
        assertNull(parser.parseShareText("a".repeat(16385)));
    }
}
