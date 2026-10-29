package com.handoff.hook;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * M2：在 QQ 跳板页发出内置浏览器 Intent 之前交接 B 站视频。
 *
 * <p>不能在 onCreate 的 proceed() 之后 finish：QQ 已在 doOnCreate 里启动浏览器。
 * 只阻断该跳板页的 startActivity，不跳过生命周期；QQ 原代码负责 finish 跳板页。
 */
final class BrowserHandoff {

    private static final String BILI_PACKAGE = "tv.danmaku.bili";

    private BrowserHandoff() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        Class<?> delegationClass;
        Class<?> dispatchClass;
        try {
            delegationClass = Class.forName(HookTargets.BROWSER_DELEGATION_CLASS, false, cl);
            dispatchClass = Class.forName(HookTargets.BROWSER_DISPATCH_CLASS, false, cl);
        } catch (Throwable t) {
            warn(reporter, "HANDOFF 找不到浏览器跳板/派发类", t);
            return;
        }
        int installed = 0;
        // QQ 9.3.75 declares both overloads on this QQ-owned class; never hook a shared onCreate.
        for (Class<?>[] signature : new Class<?>[][]{
                {Intent.class}, {Intent.class, Bundle.class}}) {
            try {
                Method method = dispatchClass.getDeclaredMethod("startActivity", signature);
                if (method.getReturnType() != void.class) continue;
                xposed.hook(method)
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .intercept(chain -> {
                            Object self = chain.getThisObject();
                            // Most calls on QBaseActivity aren't browser delegation; pass through immediately.
                            if (!(self instanceof Activity) || self.getClass() != delegationClass) {
                                return chain.proceed();
                            }
                            return BrowserDispatch.intercept(() -> {
                                long start = SystemClock.elapsedRealtimeNanos();
                                Object arg = chain.getArgs().get(0);
                                if (!(arg instanceof Intent)) return false;
                                Intent outgoing = (Intent) arg;
                                ComponentName target = outgoing.getComponent();
                                if (target == null) return false;
                                String url = outgoing.getStringExtra("url");
                                String id = BrowserDispatch.videoId(self.getClass().getName(),
                                        target.getPackageName(), target.getClassName(), url);
                                if (id == null) return false;
                                long matched = SystemClock.elapsedRealtimeNanos();
                                String how = launch((Activity) self, url, id);
                                long accepted = SystemClock.elapsedRealtimeNanos();
                                if (how == null) {
                                    warn(reporter, "HANDOFF 外跳失败，放行 QQ 浏览器 id=" + id, null);
                                    return false;
                                }
                                // Logging must not turn an accepted launch into fail-open. No query tokens in logs.
                                info(reporter, "HANDOFF blocked QQ browser id=" + id + " via " + how
                                        + " matchUs=" + (matched - start) / 1_000
                                        + " launchUs=" + (accepted - matched) / 1_000
                                        + " totalUs=" + (accepted - start) / 1_000);
                                return true;
                            }, chain::proceed);
                        });
                installed++;
                info(reporter, "HANDOFF dispatch installed " + method);
            } catch (Throwable t) {
                warn(reporter, "HANDOFF startActivity/" + signature.length + " 挂载失败", t);
            }
        }
        if (installed == 0) warn(reporter, "HANDOFF 没有可用派发点，保留 QQ 原行为", null);
    }

    private static void info(LsposedBackend.Reporter reporter, String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }

    private static void warn(LsposedBackend.Reporter reporter, String message, Throwable cause) {
        try { reporter.warn(message, cause); } catch (Throwable ignored) { }
    }

    /**
     * 按降级链尝试拉起 B 站 App，返回命中的方式（null = 全失败）。
     *
     * <p>本机实测：B 站的 https App Links **没有** verified，直接 ACTION_VIEW 会落到默认浏览器，
     * 所以必须 `setPackage` 指定包，再不行就用 {@code bilibili://} scheme。
     */
    private static String launch(Context context, String originalUrl, String id) {
        String videoLaunch = BiliLauncher.launchVideo(context, originalUrl, id);
        if (videoLaunch != null) return videoLaunch;
        // ③ 兜底：包启动（B 站首页），至少把用户送进 App
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(BILI_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return "package";
            }
        } catch (Throwable ignored) {
            // 继续失败
        }
        return null;
    }

}
