package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicBrowserDispatchTest {
    private String parse(String caller, String pkg, String target, String url) {
        return DynamicBrowserDispatch.parse(caller, pkg, target, url, QqAdapters.STRICT);
    }
    @Test void requiresExistingExactQqBrowserBoundary() {
        String caller = QqAdapters.STRICT.browserCaller(), target = QqAdapters.STRICT.browsers().iterator().next();
        assertEquals("https://example.com/a", parse(caller, "com.tencent.mobileqq", target, "https://example.com/a"));
        assertNull(parse(caller + "Subclass", "com.tencent.mobileqq", target, "https://example.com/a"));
        assertNull(parse(caller, "example.other", target, "https://example.com/a"));
        assertNull(parse(caller, "com.tencent.mobileqq", "example.Browser", "https://example.com/a"));
        for (String url : new String[]{"https://b23.tv/a", "https://v.douyin.com/a", "https://v.kuaishou.com/a", "javascript:alert(1)"})
            assertNull(parse(caller, "com.tencent.mobileqq", target, url));
    }
}
