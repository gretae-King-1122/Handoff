package com.handoff.rules.links;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrdinaryWebLinkTest {
    @Test void ordinaryPagesKeepTheirQueriesFragmentsPortsAndUnicodePaths() {
        for (String url : new String[]{
                "https://example.com/", "http://example.com:8080/path?q=a%2Fb&lang=中文#part-2",
                "HTTPS://WWW.EXAMPLE.COM/文档", "http://192.168.1.1/", "https://[2001:db8::1]/",
                "https://github.com/gretae-King-1122/Handoff", "https://www.baidu.com/s?wd=android",
                "https://notbilibili.com/", "https://bilibili.com.example.org/",
                "https://example.org/?url=https://www.bilibili.com/video/BV1xx411c7mD"
        }) assertTrue(OrdinaryWebLink.matches(url), url);
    }

    @Test void socialPagesShortSharesAndMiniAppLinksRemainOnTheirExistingRoutes() {
        for (String host : new String[]{
                "bilibili.com", "b23.tv", "bili2233.cn", "v.douyin.com", "iesdouyin.com", "amemv.com",
                "v.kuaishou.com", "v.m.chenzhongtech.com", "gifshow.com", "m.q.qq.com", "c.pc.qq.com",
                "mp.weixin.qq.com", "url.cn", "weibo.com", "t.cn", "xiaohongshu.com", "xhslink.com",
                "zhihu.com", "zhihu.link", "douban.com", "tieba.baidu.com", "x.com", "t.co",
                "facebook.com", "instagram.com", "threads.net", "reddit.com", "youtu.be", "tiktok.com"
        }) {
            assertFalse(OrdinaryWebLink.matches("https://" + host + "/unrecognized-page?x=1"), host);
            assertFalse(OrdinaryWebLink.matches("http://SUB." + host.toUpperCase(java.util.Locale.ROOT) + ".:8080/"), host);
        }
    }

    @Test void malformedAmbiguousAndNonWebUrlsFailOpen() {
        for (String url : new String[]{null, "", "example.com", "//example.com/", "https://", "https:///example.com",
                "file:///sdcard/test.html", "intent://example.com", "javascript:alert(1)", "mailto:a@example.com",
                "https://qq.com@example.com/", "https://example.com@qq.com/", "https://example.com:99999/",
                "https://example.com\\@qq.com/", "https://example.com/%ZZ", " https://example.com/",
                "https://example.com/\n", "https://example.com/" + "x".repeat(16384)
        }) assertFalse(OrdinaryWebLink.matches(url), String.valueOf(url));
    }
}
