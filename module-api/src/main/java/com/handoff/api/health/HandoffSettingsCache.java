package com.handoff.api.health;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** Nonblocking click-path reads. Refresh failures and in-flight changes are never permission to launch. */
public final class HandoffSettingsCache {
    public interface Source { Map<String, ?> load() throws Exception; }
    private final Executor worker;
    private final Source source;
    private volatile Map<String, Boolean> snapshot;
    private long revision;
    private boolean scheduled;

    public HandoffSettingsCache(Executor worker, Source source) { this.worker = worker; this.source = source; }

    public synchronized void refresh() {
        revision++;
        snapshot = null;
        schedule();
    }

    private void schedule() {
        if (scheduled) return;
        scheduled = true;
        long expected = revision;
        try { worker.execute(() -> load(expected)); }
        catch (RuntimeException ignored) { scheduled = false; }
    }

    private void load(long expected) {
        Map<String, Boolean> next = null;
        try {
            Map<String, ?> values = source.load();
            Map<String, Boolean> checked = new HashMap<>();
            checked.put("enabled", flag(values, "enabled", true));
            checked.put("dynamic_network", flag(values, "dynamic_network", false));
            for (HealthEvent.Entry entry : HealthEvent.Entry.values())
                checked.put(entry.key, flag(values, entry.key, entry.defaultEnabled()));
            next = Map.copyOf(checked);
        } catch (Throwable ignored) { /* No stale or default-on snapshot on an unreadable source. */ }
        synchronized (this) {
            if (expected == revision) snapshot = next;
            scheduled = false;
            // A concurrent invalidation wins over a slow earlier read; coalesce intermediate changes.
            if (expected != revision) schedule();
        }
    }

    private static boolean flag(Map<String, ?> values, String key, boolean fallback) {
        if (!values.containsKey(key)) return fallback;
        Object value = values.get(key);
        if (!(value instanceof Boolean)) throw new IllegalArgumentException("Invalid boolean setting");
        return (Boolean) value;
    }

    private Map<String, Boolean> current() {
        Map<String, Boolean> value = snapshot;
        if (value == null) {
            synchronized (this) { if (snapshot == null) schedule(); }
            throw new IllegalStateException("Settings unavailable");
        }
        return value;
    }

    public boolean enabled(HealthEvent.Entry entry) {
        Map<String, Boolean> value = current();
        return value.get("enabled") && value.get(entry.key);
    }
    public boolean dynamicNetworkEnabled() {
        Map<String, Boolean> value = current();
        return value.get("enabled") && value.get(HealthEvent.Entry.DYNAMIC.key) && value.get("dynamic_network");
    }
}
