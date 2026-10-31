package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DouyinBrowserDispatchTest {
    private static final QqAdapters.Contract CONTRACT = QqAdapters.STRICT;
    private static final String URL = "https://v.douyin.com/G7dxP6TRhw4/";

    @Test void requiresTheExistingExactQqDelegationRoute() {
        for (String browser : CONTRACT.browsers()) {
            assertNotNull(DouyinBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", browser, URL, CONTRACT));
            assertNull(DouyinBrowserDispatch.parse("other.Activity", "com.tencent.mobileqq", browser, URL, CONTRACT));
            assertNull(DouyinBrowserDispatch.parse(CONTRACT.browserCaller(), "other.package", browser, URL, CONTRACT));
        }
        assertNull(DouyinBrowserDispatch.parse(null, "com.tencent.mobileqq", CONTRACT.browsers().get(0), URL, CONTRACT));
        assertNull(DouyinBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", null, URL, CONTRACT));
        assertNull(DouyinBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq", "other.Browser", URL, CONTRACT));
    }

    @Test void doesNotCaptureBilibiliOrSearchInsideAnUnrelatedUrl() {
        for (String url : new String[]{null, "https://www.bilibili.com/video/BV1xx411c7mD", "https://b23.tv/abc",
                "https://example.com/?url=" + URL, "分享文案 " + URL}) {
            assertNull(DouyinBrowserDispatch.parse(CONTRACT.browserCaller(), "com.tencent.mobileqq",
                    CONTRACT.browsers().get(0), url, CONTRACT));
        }
    }
}
