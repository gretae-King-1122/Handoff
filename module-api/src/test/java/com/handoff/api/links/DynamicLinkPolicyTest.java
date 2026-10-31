package com.handoff.api.links;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicLinkPolicyTest {
    @Test void normalizesOnlyAuthorityAndPreservesContentState() {
        assertEquals("https://share.example.com/path%2Fpart?token=a%2Fb&x=1#state",
                DynamicLinkPolicy.normalize("HTTPS://SHARE.EXAMPLE.COM:443/path%2Fpart?token=a%2Fb&x=1#state"));
        assertEquals("https://example.com/", DynamicLinkPolicy.normalize("https://example.com"));
        assertNotNull(DynamicLinkPolicy.normalize("https://bilibili.com.example.com/a"));
    }
    @Test void refusesExistingPlatformFamiliesEvenIfTheirSwitchesAreOff() {
        for (String host : new String[]{"bilibili.com", "www.bilibili.com", "b23.tv", "bili2233.cn",
                "v.douyin.com", "www.iesdouyin.com", "v.kuaishou.com", "v.m.chenzhongtech.com", "gifshow.com"})
            assertNull(DynamicLinkPolicy.normalize("https://" + host + "/content"), host);
        for (String pkg : new String[]{"com.tencent.mobileqq", "com.handoff.module", "tv.danmaku.bili",
                "com.ss.android.ugc.aweme", "com.smile.gifmaker"}) assertFalse(DynamicLinkPolicy.allowedPackage(pkg));
        assertTrue(DynamicLinkPolicy.allowedPackage("example.app"));
        assertFalse(DynamicLinkPolicy.allowedPackage(null));
    }
    @Test void rejectsNonWebAmbiguousAndLocalInputs() {
        for (String input : new String[]{"http://example.com/a", "intent://example.com", "javascript:alert(1)",
                "https://u:p@example.com/", "https://example.com:444/", "https://example.com./",
                "https://example.com\\@evil.com/", "https://127.0.0.1/", "https://2130706433/",
                "https://[::1]/", "https://router.local/", "https://router.internal/", "https://router.lan/",
                "https://localhost/", " https://example.com/", "https://example.com/\n", "https://-a.example/",
                "https://example.invalid/", "https://" + "a".repeat(64) + ".com/"})
            assertNull(DynamicLinkPolicy.normalize(input), input);
        assertNull(DynamicLinkPolicy.normalize(null));
        assertNull(DynamicLinkPolicy.normalize("https://example.com/" + "a".repeat(4096)));
    }
}
