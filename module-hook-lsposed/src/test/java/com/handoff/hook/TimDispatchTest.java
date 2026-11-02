package com.handoff.hook;

import com.handoff.api.HostApps;
import com.handoff.api.health.QqAdapters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimDispatchTest {
    private static final QqAdapters.Contract TIM = QqAdapters.TIM_STRICT;
    private static final String BILI = "https://www.bilibili.com/video/BV1xx411c7mD";
    private static final String DOUYIN = "https://v.douyin.com/G7dxP6TRhw4/";
    private static final String KUAISHOU = "https://v.kuaishou.com/Fixture123";

    @Test void timGetsTheSameNormalHookPlanOnlyInItsMainProcess() {
        assertEquals(HookPlan.steps(HostApps.QQ, false), HookPlan.steps(HostApps.TIM, false));
        assertTrue(HookPlan.steps(HostApps.TIM + ":mini", false).isEmpty());
        assertFalse(HookPlan.steps(HostApps.TIM + ":mini", true).isEmpty());
        assertTrue(HookPlan.steps(HostApps.TIM + ".fake", true).isEmpty());
    }
    @Test void everySupportedBrowserRouteIsBoundToItsContractPackage() {
        for (String browser : TIM.browsers()) {
            assertEquals("BV1xx411c7mD", BrowserDispatch.videoId(TIM.browserCaller(), HostApps.TIM, browser, BILI, TIM));
            assertNotNull(DouyinBrowserDispatch.parse(TIM.browserCaller(), HostApps.TIM, browser, DOUYIN, TIM));
            assertNotNull(KuaishouBrowserDispatch.parse(TIM.browserCaller(), HostApps.TIM, browser, KUAISHOU, TIM));
            for (String wrong : new String[]{HostApps.QQ, "com.tencent.tim.fake", null}) {
                assertNull(BrowserDispatch.videoId(TIM.browserCaller(), wrong, browser, BILI, TIM));
                assertNull(DouyinBrowserDispatch.parse(TIM.browserCaller(), wrong, browser, DOUYIN, TIM));
                assertNull(KuaishouBrowserDispatch.parse(TIM.browserCaller(), wrong, browser, KUAISHOU, TIM));
            }
            assertNull(BrowserDispatch.videoId(TIM.browserCaller(), HostApps.TIM, browser, BILI, QqAdapters.STRICT));
        }
        assertNull(BrowserDispatch.videoId("other.Activity", HostApps.TIM, TIM.browsers().get(0), BILI, TIM));
        assertNull(BrowserDispatch.videoId(TIM.browserCaller(), HostApps.TIM, "other.Browser", BILI, TIM));
    }
    @Test void timMiniContainersDoNotBroadenTheQqBoundary() {
        var caller = new MiniContainerDispatch.Caller(TIM.miniCaller(), false, false);
        for (String container : TIM.containers()) {
            var request = new MiniContainerDispatch.Envelope(caller, HostApps.TIM, container, -1, false, false);
            assertTrue(MiniContainerDispatch.supported(request, TIM));
            assertFalse(MiniContainerDispatch.supported(request, QqAdapters.STRICT));
            assertFalse(MiniContainerDispatch.supported(new MiniContainerDispatch.Envelope(
                    caller, HostApps.QQ, container, -1, false, false), TIM));
            assertFalse(MiniContainerDispatch.supported(new MiniContainerDispatch.Envelope(
                    caller, HostApps.TIM, container, 1, false, false), TIM));
        }
    }
}
