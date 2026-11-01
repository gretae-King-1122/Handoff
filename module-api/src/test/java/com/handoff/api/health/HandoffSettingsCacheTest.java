package com.handoff.api.health;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandoffSettingsCacheTest {
    @Test void clickReadsNeverInvokeSourceAndUnavailableReadsCoalesce() {
        ArrayDeque<Runnable> work = new ArrayDeque<>(); AtomicInteger loads = new AtomicInteger();
        var cache = new HandoffSettingsCache(work::add, () -> { loads.incrementAndGet(); return Map.of(); });
        for (int i = 0; i < 10; i++) assertThrows(IllegalStateException.class, () -> cache.enabled(HealthEvent.Entry.SDK));
        assertEquals(0, loads.get()); assertEquals(1, work.size());
        work.remove().run();
        for (int i = 0; i < 100; i++) assertTrue(cache.enabled(HealthEvent.Entry.SDK));
        assertEquals(1, loads.get()); assertTrue(work.isEmpty());
    }
    @Test void defaultsAndMasterAndIndependentPlatformSettingsArePreserved() {
        ArrayDeque<Runnable> work = new ArrayDeque<>(); Map<String, Object> flags = new HashMap<>();
        var cache = new HandoffSettingsCache(work::add, () -> new HashMap<>(flags));
        cache.refresh(); work.remove().run();
        for (var entry : HealthEvent.Entry.values()) assertEquals(entry.defaultEnabled(), cache.enabled(entry));
        assertFalse(cache.dynamicNetworkEnabled());
        flags.put("sdk", false); flags.put("douyin", true); flags.put("dynamic_network", true);
        cache.refresh(); work.remove().run();
        assertFalse(cache.enabled(HealthEvent.Entry.SDK)); assertTrue(cache.enabled(HealthEvent.Entry.DOUYIN));
        assertTrue(cache.enabled(HealthEvent.Entry.BROWSER)); assertFalse(cache.dynamicNetworkEnabled());
        flags.put("dynamic", true); cache.refresh(); work.remove().run(); assertTrue(cache.dynamicNetworkEnabled());
        flags.put("enabled", false); cache.refresh(); work.remove().run();
        for (var entry : HealthEvent.Entry.values()) assertFalse(cache.enabled(entry));
        assertFalse(cache.dynamicNetworkEnabled());
    }
    @Test void invalidationImmediatelyDropsPreviousPermissionAndFailedLoadsCannotRestoreIt() {
        ArrayDeque<Runnable> work = new ArrayDeque<>(); boolean[] fail = {false};
        var cache = new HandoffSettingsCache(work::add, () -> {
            if (fail[0]) throw new IllegalStateException(); return Map.of("dynamic", true);
        });
        cache.refresh(); work.remove().run(); assertTrue(cache.enabled(HealthEvent.Entry.DYNAMIC));
        fail[0] = true; cache.refresh();
        assertThrows(IllegalStateException.class, () -> cache.enabled(HealthEvent.Entry.DYNAMIC));
        work.remove().run();
        assertThrows(IllegalStateException.class, () -> cache.enabled(HealthEvent.Entry.SDK));
        fail[0] = false; work.remove().run(); assertTrue(cache.enabled(HealthEvent.Entry.SDK));
    }
    @Test void changeDuringLoadCannotPublishStalePermission() {
        ArrayDeque<Runnable> work = new ArrayDeque<>(); AtomicInteger calls = new AtomicInteger();
        HandoffSettingsCache[] reference = new HandoffSettingsCache[1];
        reference[0] = new HandoffSettingsCache(work::add, () -> {
            if (calls.incrementAndGet() == 1) { reference[0].refresh(); return Map.of("enabled", true); }
            return Map.of("enabled", false);
        });
        var cache = reference[0]; cache.refresh(); work.remove().run();
        assertThrows(IllegalStateException.class, () -> cache.enabled(HealthEvent.Entry.SDK));
        assertEquals(1, work.size()); work.remove().run(); assertFalse(cache.enabled(HealthEvent.Entry.SDK));
    }
    @Test void malformedSettingsAndRejectedWorkersAreFailOpenToQqNotDefaultOn() {
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        var cache = new HandoffSettingsCache(work::add, () -> Map.of("sdk", "true"));
        cache.refresh(); work.remove().run();
        assertThrows(IllegalStateException.class, () -> cache.enabled(HealthEvent.Entry.SDK));
        var rejected = new HandoffSettingsCache(task -> { throw new java.util.concurrent.RejectedExecutionException(); }, Map::of);
        assertDoesNotThrow(rejected::refresh);
        assertThrows(IllegalStateException.class, () -> rejected.enabled(HealthEvent.Entry.SDK));
    }
    @Test void backgroundReadCannotHoldUpClickThread() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var cache = new HandoffSettingsCache(worker, () -> {
                entered.countDown(); release.await(); return Map.of();
            });
            cache.refresh(); assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () ->
                    assertThrows(IllegalStateException.class, () -> cache.enabled(HealthEvent.Entry.SDK)));
        } finally { release.countDown(); worker.shutdownNow(); }
    }
}
