package com.handoff.api;

import com.handoff.api.health.QqAdapters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HostAppsTest {
    @Test void onlyExactHostsAndTheirColonProcessesAreRecognized() {
        for (String host : HostApps.PACKAGES) {
            assertTrue(HostApps.isSupported(host));
            assertTrue(HostApps.isMainProcess(host));
            assertEquals(host, HostApps.packageForProcess(host));
            assertEquals(host, HostApps.packageForProcess(host + ":mini"));
            assertFalse(HostApps.isMainProcess(host + ":mini"));
        }
        for (String name : new String[]{null, "", "com.tencent.tim.fake", "com.tencent.mobileqq.fake", "evil:com.tencent.tim"}) {
            assertFalse(HostApps.isSupported(name));
            assertNull(HostApps.packageForProcess(name));
        }
    }
    @Test void versionCodesNeverCrossHostBoundaries() {
        assertEquals("tim-4.1.0", QqAdapters.resolve(HostApps.TIM, 4050).id());
        assertEquals("tim-strict-probe", QqAdapters.resolve(HostApps.TIM, 16580).id());
        assertEquals("strict-probe", QqAdapters.resolve(HostApps.QQ, 4050).id());
        assertEquals(HostApps.TIM, QqAdapters.resolve(HostApps.TIM, 4050).contract().packageName());
        assertEquals(HostApps.TIM, QqAdapters.resolve(HostApps.TIM, -1).packageName());
        assertFalse(QqAdapters.resolve(HostApps.TIM, 4050).observed());
        assertThrows(IllegalArgumentException.class, () -> QqAdapters.resolve("other", 4050));
    }
}
