package com.handoff.ui;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.lang.ref.WeakReference;

final class FrameworkConnection {
    static volatile XposedService service;
    private static WeakReference<Runnable> observer = new WeakReference<>(null);
    static {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService value) { service = value; changed(); }
            @Override public void onServiceDied(XposedService value) { if (service == value) service = null; changed(); }
        });
    }
    static void observe(Runnable callback) { observer = new WeakReference<>(callback); }
    private static void changed() { Runnable callback = observer.get(); if (callback != null) callback.run(); }
    private FrameworkConnection() { }
}
