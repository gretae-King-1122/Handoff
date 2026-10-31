package com.handoff.hook;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.SystemClock;

import java.lang.reflect.Method;
import com.handoff.api.health.HealthEvent;
import com.handoff.api.links.ParsedLink;
import com.handoff.executor.DouyinLauncher;
import com.handoff.executor.KuaishouLauncher;

import io.github.libxposed.api.XposedInterface;

/**
 * M2：在 QQ 跳板页发出内置浏览器 Intent 之前交接 B 站视频。
 *
 * <p>不能在 onCreate 的 proceed() 之后 finish：QQ 已在 doOnCreate 里启动浏览器。
 * 只阻断该跳板页的 startActivity，不跳过生命周期；QQ 原代码负责 finish 跳板页。
 */
final class BrowserHandoff {

    private static final String BILI_PACKAGE = "tv.danmaku.bili";
    private static final MethodInstallGuard INSTALLATIONS = new MethodInstallGuard();

    private BrowserHandoff() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        Class<?> dispatchClass;
        try {
            dispatchClass = Class.forName(reporter.profile().contract().browserDispatch(), false, cl);
        } catch (Throwable t) {
            warn(reporter, "HANDOFF 找不到浏览器派发类", t);
            HealthReports.emit(reporter, HealthEvent.Kind.INSTALL_FAILED, HealthEvent.Entry.BROWSER, HealthEvent.Reason.SIGNATURE);
            HealthReports.emit(reporter, HealthEvent.Kind.INSTALL_FAILED, HealthEvent.Entry.DOUYIN, HealthEvent.Reason.SIGNATURE);
            HealthReports.emit(reporter, HealthEvent.Kind.INSTALL_FAILED, HealthEvent.Entry.KUAISHOU, HealthEvent.Reason.SIGNATURE);
            return;
        }
        int installed = 0;
        // QQ 9.3.75 declares both overloads on this QQ-owned class; never hook a shared onCreate.
        for (Class<?>[] signature : new Class<?>[][]{
                {Intent.class}, {Intent.class, Bundle.class}}) {
            try {
                Method method = dispatchClass.getDeclaredMethod("startActivity", signature);
                if (method.getReturnType() != void.class) continue;
                boolean added = INSTALLATIONS.install(method, () -> xposed.hook(method)
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .intercept(chain -> {
                            Object self = chain.getThisObject();
                            // Most calls on QBaseActivity aren't browser delegation; pass through immediately.
                            if (!(self instanceof Activity) || !BrowserDispatch.delegationCaller(self.getClass(), reporter.profile().contract())) {
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
                                // Independent opt-in pilot. Do not change Bilibili matching, launch order or fallback.
                                ParsedLink douyin = DouyinBrowserDispatch.parse(self.getClass().getName(),
                                        target.getPackageName(), target.getClassName(), url, reporter.profile().contract());
                                if (douyin != null) {
                                    if (((Activity) self).isDestroyed() || outgoing.getSelector() != null
                                            || (outgoing.getFlags() & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0) return false;
                                    return DouyinHandoff.redirect(() -> DouyinLauncher.launch((Activity) self, douyin), reporter);
                                }
                                ParsedLink kuaishou = KuaishouBrowserDispatch.parse(self.getClass().getName(),
                                        target.getPackageName(), target.getClassName(), url, reporter.profile().contract());
                                if (kuaishou != null) {
                                    if (((Activity) self).isDestroyed() || outgoing.getSelector() != null
                                            || (outgoing.getFlags() & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0) return false;
                                    if (kuaishou.kind() == ParsedLink.Kind.SHORT_LINK) {
                                        return KuaishouHandoff.redirectShort(completion -> KuaishouLauncher.resolveShort(
                                                (Activity) self, kuaishou, outgoing,
                                                new ResultReceiver(new Handler(Looper.getMainLooper())) {
                                                    @Override protected void onReceiveResult(int code, Bundle data) {
                                                        completion.complete(switch (code) {
                                                            case KuaishouLauncher.STARTED -> KuaishouHandoff.ShortResult.ACCEPTED;
                                                            case KuaishouLauncher.CANCELLED -> KuaishouHandoff.ShortResult.CANCELLED;
                                                            case KuaishouLauncher.FALLBACK_FAILED -> KuaishouHandoff.ShortResult.FALLBACK_FAILED;
                                                            default -> KuaishouHandoff.ShortResult.FAILED;
                                                        });
                                                    }
                                                }), reporter);
                                    }
                                    return KuaishouHandoff.redirect(() -> KuaishouLauncher.launch((Activity) self, kuaishou), reporter);
                                }
                                String id = BrowserDispatch.videoId(self.getClass().getName(),
                                        target.getPackageName(), target.getClassName(), url, reporter.profile().contract());
                                if (id == null) return false;
                                HealthReports.emit(reporter, HealthEvent.Kind.ENTERED, HealthEvent.Entry.BROWSER);
                                if (!HealthReports.enabled(reporter, HealthEvent.Entry.BROWSER)) return false;
                                long matched = SystemClock.elapsedRealtimeNanos();
                                String how = launch((Activity) self, url, id);
                                long accepted = SystemClock.elapsedRealtimeNanos();
                                if (how == null) {
                                    warn(reporter, "HANDOFF 外跳失败，放行 QQ 浏览器 id=" + id, null);
                                    HealthReports.emit(reporter, HealthEvent.Kind.LAUNCH_FAILED, HealthEvent.Entry.BROWSER, HealthEvent.Reason.START_FAILED);
                                    return false;
                                }
                                // Logging must not turn an accepted launch into fail-open. No query tokens in logs.
                                info(reporter, "HANDOFF blocked QQ browser id=" + id + " via " + how
                                        + " matchUs=" + (matched - start) / 1_000
                                        + " launchUs=" + (accepted - matched) / 1_000
                                        + " totalUs=" + (accepted - start) / 1_000);
                                HealthReports.emit(reporter, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER);
                                return true;
                            }, chain::proceed);
                        }));
                installed++;
                if (added) info(reporter, "HANDOFF dispatch installed " + method);
                HealthReports.emit(reporter, HealthEvent.Kind.INSTALLED, HealthEvent.Entry.BROWSER);
                HealthReports.emit(reporter, HealthEvent.Kind.INSTALLED, HealthEvent.Entry.DOUYIN);
                HealthReports.emit(reporter, HealthEvent.Kind.INSTALLED, HealthEvent.Entry.KUAISHOU);
            } catch (Throwable t) {
                warn(reporter, "HANDOFF startActivity/" + signature.length + " 挂载失败", t);
                HealthReports.emit(reporter, HealthEvent.Kind.INSTALL_FAILED, HealthEvent.Entry.BROWSER, HealthEvent.Reason.SIGNATURE);
                HealthReports.emit(reporter, HealthEvent.Kind.INSTALL_FAILED, HealthEvent.Entry.DOUYIN, HealthEvent.Reason.SIGNATURE);
                HealthReports.emit(reporter, HealthEvent.Kind.INSTALL_FAILED, HealthEvent.Entry.KUAISHOU, HealthEvent.Reason.SIGNATURE);
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
