package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultBrowserDispatchTest {
    private static final String URL = "https://example.com/article?id=42#section";

    @Test void ordinaryWebRoutingAcceptsBothHostsButRejectsCrossHostAndUnrelatedDispatch() {
        for (var contract : new QqAdapters.Contract[]{QqAdapters.STRICT, QqAdapters.TIM_STRICT}) {
            for (String browser : contract.browsers()) {
                assertTrue(match(contract.browserCaller(), contract.packageName(), browser, URL, contract));
                assertFalse(match(contract.browserCaller(), "unrelated.package", browser, URL, contract));
                String otherHost = contract == QqAdapters.STRICT ? "com.tencent.tim" : "com.tencent.mobileqq";
                assertFalse(match(contract.browserCaller(), otherHost, browser, URL, contract));
                assertFalse(match("other.Activity", contract.packageName(), browser, URL, contract));
                assertFalse(match(contract.browserCaller(), contract.packageName(), "other.Browser", URL, contract));
                assertFalse(match(contract.browserCaller(), contract.packageName(), null, URL, contract));
            }
        }
    }

    @Test void lifecycleSelectorAndResultForwardingAreNotConsumed() {
        var c = QqAdapters.STRICT;
        for (int condition = 0; condition < 3; condition++) {
            assertFalse(DefaultBrowserDispatch.matches(c.browserCaller(), c.packageName(), c.browsers().get(0), URL,
                    condition == 0, condition == 1, condition == 2, c));
        }
    }

    @Test void unsupportedSocialContentAndCardsNeverFallThroughToDefaultBrowser() {
        var c = QqAdapters.STRICT;
        for (String url : new String[]{"https://www.bilibili.com/video/BV1xx411c7mD", "https://b23.tv/abc",
                "https://space.bilibili.com/14606305",
                "https://www.bilibili.com/bangumi/play/ep1", "https://v.douyin.com/abc/",
                "https://v.kuaishou.com/abc", "https://m.q.qq.com/a/s/abc", "mqqapi://miniapp/open", null}) {
            assertFalse(match(c.browserCaller(), c.packageName(), c.browsers().get(0), url, c));
        }
    }

    private static boolean match(String caller, String pkg, String browser, String url, QqAdapters.Contract c) {
        return DefaultBrowserDispatch.matches(caller, pkg, browser, url, false, false, false, c);
    }
}
