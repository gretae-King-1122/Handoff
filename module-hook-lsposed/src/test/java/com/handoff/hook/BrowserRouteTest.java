package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import com.handoff.api.links.ParsedLink;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrowserRouteTest {
    private static final String CALLER = "com.tencent.mobileqq.activity.QQBrowserDelegationActivity";
    private static final String QQ = "com.tencent.mobileqq";
    private static final String BROWSER = "com.tencent.mobileqq.activity.QQBrowserActivity";

    private BrowserRoute route(String url) { return BrowserRoute.parse(CALLER, QQ, BROWSER, url, QqAdapters.STRICT); }

    @Test void sharesUriButPreservesEveryPlatformsOriginalRoutingPolicy() {
        for (String host : List.of("www.bilibili.com", "v.douyin.com", "www.douyin.com",
                "v.kuaishou.com", "v.m.chenzhongtech.com", "app.example.com", "sub.douyin.com")) {
            for (String path : List.of("/", "/video/BV1xx411c7mD", "/video/123", "/Fixture123/",
                    "/fw/photo/3abc?photoId=3abc", "/fw/photo/3abc?photoId=other", "/video/%42V1xx411c7mD")) {
                for (String scheme : List.of("https", "http", "javascript")) {
                    for (String auth : List.of("", "evil@")) {
                        String url = scheme + "://" + auth + host + path;
                        ParsedLink dy = DouyinBrowserDispatch.parse(CALLER, QQ, BROWSER, url, QqAdapters.STRICT);
                        ParsedLink ks = KuaishouBrowserDispatch.parse(CALLER, QQ, BROWSER, url, QqAdapters.STRICT);
                        String bili = BrowserDispatch.videoId(CALLER, QQ, BROWSER, url);
                        String dynamic = DynamicBrowserDispatch.parse(CALLER, QQ, BROWSER, url, QqAdapters.STRICT);
                        BrowserRoute expected = dy != null ? new BrowserRoute(dy, null, null)
                                : ks != null ? new BrowserRoute(ks, null, null)
                                : bili != null ? new BrowserRoute(null, bili, null)
                                : dynamic != null ? new BrowserRoute(null, null, dynamic) : null;
                        assertEquals(expected, route(url), url);
                    }
                }
            }
        }
    }

    @Test void onlyExplicitCallerAndBrowserTargetMayEnterTheSharedRoute() {
        String url = "https://v.douyin.com/Fixture123/";
        assertNotNull(route(url));
        assertNull(BrowserRoute.parse("other", QQ, BROWSER, url, QqAdapters.STRICT));
        assertNull(BrowserRoute.parse(CALLER, "other", BROWSER, url, QqAdapters.STRICT));
        assertNull(BrowserRoute.parse(CALLER, QQ, "other", url, QqAdapters.STRICT));
        assertNull(BrowserRoute.parse(CALLER, QQ, null, url, QqAdapters.STRICT));
    }

    @Test void malformedAndReservedDomainsNeverFallThroughToDynamicHandoff() {
        for (String url : new String[]{null, "", "not a url", "https://user@www.douyin.com/video/123",
                "https://www.bilibili.com/unsupported", "https://sub.bilibili.com/video/BV1xx411c7mD",
                "https://v.kuaishou.com:444/Fixture123", "https://v.m.chenzhongtech.com/fw/photo/3abc?photoId=other",
                "https://127.0.0.1/path", "https://router.local/path"}) assertNull(route(url), url);
    }

    @Test void casingPortsFragmentsAndLegacyBilibiliLongQueriesKeepTheirMeaning() {
        assertEquals("av170001", route("HTTP://BILIBILI.COM:80/video/av170001").biliId());
        assertEquals("BV1xx411c7mD", route("https://www.bilibili.com/video/BV1xx411c7mD?x=" + "a".repeat(5000)).biliId());
        assertEquals("https://app.example.com/item?token=private#state",
                route("HTTPS://APP.EXAMPLE.COM:443/item?token=private#state").dynamicUrl());
        assertNull(route("https://www.douyin.com/video/123?x=" + "a".repeat(4096)));
        assertNull(route("https://v.kuaishou.com/Fixture123?x=" + "a".repeat(4096)));
    }
}
