package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KuaishouBrowserDispatchTest {
    private static final QqAdapters.Contract CONTRACT = QqAdapters.STRICT;
    private static final String URL = "https://v.m.chenzhongtech.com/fw/photo/3xSampleVideo123?photoId=3xSampleVideo123";

    @Test void acceptsOnlyTheExistingExactQqBrowserDelegationRoute() {
        for (String browser : CONTRACT.browsers()) {
            assertNotNull(KuaishouBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", browser, URL, CONTRACT));
            assertNull(KuaishouBrowserDispatch.parse("other.Activity", "com.tencent.mobileqq", browser, URL, CONTRACT));
            assertNull(KuaishouBrowserDispatch.parse(CONTRACT.browserCaller(), "other.package", browser, URL, CONTRACT));
        }
        assertNull(KuaishouBrowserDispatch.parse(null, "com.tencent.mobileqq", CONTRACT.browsers().get(0), URL, CONTRACT));
        assertNull(KuaishouBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", null, URL, CONTRACT));
        assertNull(KuaishouBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", "other.Browser", URL, CONTRACT));
    }

    @Test void existingPlatformsAndNestedUrlsAreNeverCaptured() {
        for (String url : new String[]{null, "https://www.bilibili.com/video/BV1xx411c7mD", "https://v.douyin.com/G7dxP6TRhw4/",
                "https://example.com/?url=" + URL, "分享文案 " + URL})
            assertNull(KuaishouBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", CONTRACT.browsers().get(0), url, CONTRACT));
        assertNull(DouyinBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", CONTRACT.browsers().get(0), URL, CONTRACT));
        assertNull(BrowserDispatch.videoId(CONTRACT.browserCaller(), "com.tencent.mobileqq", CONTRACT.browsers().get(0), URL, CONTRACT));
    }

    @Test void shortLinksUseExactlyTheSameStrictQqRoute() {
        String shortUrl = "https://v.kuaishou.com/Fixture123?tracking=private";
        assertEquals(com.handoff.api.links.ParsedLink.Kind.SHORT_LINK,
                KuaishouBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq",
                        CONTRACT.browsers().get(0), shortUrl, CONTRACT).kind());
        assertNull(KuaishouBrowserDispatch.parse("other.Activity", "com.tencent.mobileqq", CONTRACT.browsers().get(0), shortUrl, CONTRACT));
        assertNull(KuaishouBrowserDispatch.parse(CONTRACT.browserCaller(), "other.package", CONTRACT.browsers().get(0), shortUrl, CONTRACT));
        assertNull(KuaishouBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", "other.Browser", shortUrl, CONTRACT));
        assertNull(DouyinBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", CONTRACT.browsers().get(0), shortUrl, CONTRACT));
        assertNull(BrowserDispatch.videoId(CONTRACT.browserCaller(), "com.tencent.mobileqq", CONTRACT.browsers().get(0), shortUrl, CONTRACT));
    }
}
