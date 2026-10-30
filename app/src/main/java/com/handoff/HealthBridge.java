package com.handoff;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import com.handoff.api.health.HealthEvent;
import com.handoff.api.health.QqAdapters;
import io.github.libxposed.api.XposedInterface;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One bounded, background, typed report channel per main QQ process. Failure never delays a launch. */
final class HealthBridge {
    private record Pending(HealthEvent event, long at) { }
    private final XposedInterface xposed;
    private final boolean main;
    private final String session = UUID.randomUUID().toString();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), task -> { Thread t = new Thread(task, "Handoff-health"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy());
    private volatile Context context;
    private volatile SharedPreferences settings;
    private volatile long qq;
    private volatile String qqName = "unknown";
    private volatile QqAdapters.Profile profile = QqAdapters.resolve(-1);

    HealthBridge(XposedInterface xposed, String process) {
        this.xposed = xposed; main = "com.tencent.mobileqq".equals(process);
        if (main) event(HealthEvent.of(HealthEvent.Kind.SESSION, HealthEvent.Entry.SYSTEM));
    }
    synchronized void bind(Application app) {
        if (!main || context != null) return;
        try {
            var info = app.getPackageManager().getPackageInfo("com.tencent.mobileqq", 0);
            qq = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            qqName = info.versionName; profile = QqAdapters.resolve(qq);
        } catch (Exception ignored) { }
        context = app.getApplicationContext();
        while (!pending.isEmpty()) send(pending.removeFirst());
    }
    synchronized void event(HealthEvent event) {
        if (!main) return;
        Pending message = new Pending(event, System.currentTimeMillis());
        if (context == null) { if (pending.size() < 64) pending.addLast(message); }
        else send(message);
    }
    QqAdapters.Profile profile() { return profile; }
    boolean enabled(HealthEvent.Entry entry) {
        try {
            if (settings == null) settings = xposed.getRemotePreferences("handoff_settings");
            return settings.getBoolean("enabled", true) && settings.getBoolean(entry.key, true);
        } catch (Throwable ignored) {
            // The hook boundary reports CONFIG_UNAVAILABLE, not a misleading user-disabled signal.
            throw new IllegalStateException("Settings unavailable");
        }
    }
    private void send(Pending message) {
        worker.execute(() -> {
            try {
                Bundle data = new Bundle();
                data.putInt("schema", 1); data.putString("session", session);
                data.putLong("at", message.at()); data.putLong("module", BuildConfig.VERSION_CODE);
                data.putLong("qq", qq); data.putString("qqName", qqName);
                data.putString("framework", xposed.getFrameworkName() + " " + xposed.getFrameworkVersion());
                data.putString("kind", message.event().kind().name()); data.putString("entry", message.event().entry().name());
                data.putString("reason", message.event().reason().name());
                context.getContentResolver().call(Uri.parse("content://com.handoff.module.health"), "report", null, data);
            } catch (Throwable ignored) { /* No retry loop, QQ file writes, raw log fallback or UI launch. */ }
        });
    }
}
