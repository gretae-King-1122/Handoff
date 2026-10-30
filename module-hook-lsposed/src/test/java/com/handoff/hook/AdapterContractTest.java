package com.handoff.hook;

import com.handoff.api.health.QqAdapters;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AdapterContractTest {
    private static final QqAdapters.Contract CUSTOM = new QqAdapters.Contract("new.Dispatch", "new.Caller",
            List.of("new.Browser"), "new.Sdk", "new.Service", "new.Model", "new.MiniCaller", List.of("new.Container"));

    @Test void browserRouteUsesSelectedContractRatherThanStaticCatalog() {
        String video = "https://www.bilibili.com/video/av170001";
        assertEquals("av170001", BrowserDispatch.videoId("new.Caller", "com.tencent.mobileqq", "new.Browser", video, CUSTOM));
        assertNull(BrowserDispatch.videoId("new.Caller", "com.tencent.mobileqq", "new.Browser", video));
        assertNull(BrowserDispatch.videoId(QqAdapters.STRICT.browserCaller(), "com.tencent.mobileqq",
                QqAdapters.STRICT.browsers().get(0), video, CUSTOM));
        assertFalse(BrowserDispatch.delegationCaller(Object.class, CUSTOM));
    }

    @Test void finalDispatchUsesSelectedCallerAndContainerWithoutBroadeningOtherChecks() {
        var caller = new MiniContainerDispatch.Caller("new.MiniCaller", true, false);
        var envelope = new MiniContainerDispatch.Envelope(caller, "com.tencent.mobileqq", "new.Container", -1, false, false);
        assertTrue(MiniContainerDispatch.supported(envelope, CUSTOM));
        assertFalse(MiniContainerDispatch.supported(envelope));
        assertFalse(MiniContainerDispatch.supported(new MiniContainerDispatch.Envelope(caller,
                "other.package", "new.Container", -1, false, false), CUSTOM));
        assertFalse(MiniContainerDispatch.supported(new MiniContainerDispatch.Envelope(caller,
                "com.tencent.mobileqq", "new.Container", 0, false, false), CUSTOM));
    }
}
