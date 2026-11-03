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
import com.handoff.executor.BilibiliSpaceLauncher;
import com.handoff.executor.DouyinLauncher;
import com.handoff.executor.KuaishouLauncher;

import io.github.libxposed.api.XposedInterface;

/**
 * 在 QQ／TIM 跳板页发出内置浏览器 Intent 之前打开 B 站视频或个人空间。
 *
 * <p>不能在 onCreate 的 proceed() 之后 finish：QQ 已在 doOnCreate 里启动浏览器。
 * 只阻断该跳板页的 startActivity，不跳过生命周期；QQ 原代码负责 finish 跳板页。
 */
final class BrowserHandoff {

    private static final String BILI_PACKAGE = "tv.danmaku.bili";
    private static final MethodInstallGuard INSTALLATIONS = new MethodInstallGuard();
    private final LsposedBackend.Reporter reporter;

    private BrowserHandoff(LsposedBackend.Reporter reporter) {
        this.reporter = reporter;
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        new BrowserHandoff(reporter).install(xposed, cl);
    }

    private void install(XposedInterface xposed, ClassLoader cl) {
        Class<?> dispatchClass;
        try {
            dispatchClass = Class.forName(reporter.profile().contract().browserDispatch(), false, cl);
        } catch (Throwable t) {
            warn(reporter, "HANDOFF 找不到浏览器派发类", t);
            reportInstallation(HealthEvent.Kind.INSTALL_FAILED);
            return;
        }
        int installed = 0;
        // QQ 9.3.75 declares both overloads on this QQ-owned class; never hook a shared onCreate.
        for (Class<?>[] signature : new Class<?>[][]{
                {Intent.class}, {Intent.class, Bundle.class}}) {
            if (installOverload(xposed, dispatchClass, signature)) installed++;
        }
        if (installed == 0) warn(reporter, "HANDOFF 没有可用派发点，保留宿主原行为", null);
    }

    private boolean installOverload(XposedInterface xposed, Class<?> dispatchClass, Class<?>[] signature) {
        try {
            Method method = dispatchClass.getDeclaredMethod("startActivity", signature);
            if (method.getReturnType() != void.class) return false;
            boolean added = INSTALLATIONS.install(method, () -> xposed.hook(method)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(this::intercept));
            if (added) info(reporter, "HANDOFF dispatch installed " + method);
            reportInstallation(HealthEvent.Kind.INSTALLED);
            return true;
        } catch (Throwable t) {
            warn(reporter, "HANDOFF startActivity/" + signature.length + " 挂载失败", t);
            reportInstallation(HealthEvent.Kind.INSTALL_FAILED);
            return false;
        }
    }

    private void reportInstallation(HealthEvent.Kind kind) {
        HealthEvent.Reason reason = kind == HealthEvent.Kind.INSTALL_FAILED
                ? HealthEvent.Reason.SIGNATURE : HealthEvent.Reason.NONE;
        HealthReports.emit(reporter, kind, HealthEvent.Entry.BROWSER, reason);
        HealthReports.emit(reporter, kind, HealthEvent.Entry.DOUYIN, reason);
        HealthReports.emit(reporter, kind, HealthEvent.Entry.KUAISHOU, reason);
    }

    private Object intercept(XposedInterface.Chain chain) throws Throwable {
        Object self = chain.getThisObject();
        // Unrelated QBaseActivity calls exit before allocating redirect closures.
        if (!(self instanceof Activity activity)
                || !BrowserDispatch.delegationCaller(self.getClass(), reporter.profile().contract())) {
            return chain.proceed();
        }
        // Only extraction/launch errors fail open; the original invocation stays outside that catch.
        return BrowserDispatch.intercept(() -> redirect(activity, chain.getArgs().get(0)), chain::proceed);
    }

    private boolean redirect(Activity activity, Object argument) {
        long start = SystemClock.elapsedRealtimeNanos();
        if (!(argument instanceof Intent outgoing)) return false;
        ComponentName target = outgoing.getComponent();
        if (target == null) return false;
        String url = outgoing.getStringExtra("url");
        String caller = activity.getClass().getName();
        // Keep platform precedence and each platform's existing matching/fallback policy.
        ParsedLink douyin = DouyinBrowserDispatch.parse(caller,
                target.getPackageName(), target.getClassName(), url, reporter.profile().contract());
        if (douyin != null) {
            if (!pilotCallerSupported(activity, outgoing)) return false;
            return DouyinHandoff.redirect(() -> DouyinLauncher.launch(activity, douyin), reporter);
        }
        ParsedLink kuaishou = KuaishouBrowserDispatch.parse(caller,
                target.getPackageName(), target.getClassName(), url, reporter.profile().contract());
        if (kuaishou != null) return redirectKuaishou(activity, outgoing, kuaishou);
        BrowserDispatch.Target bili = BrowserDispatch.target(caller,
                target.getPackageName(), target.getClassName(), url, reporter.profile().contract());
        return bili != null && redirectBili(activity, url, bili, start);
    }

    private static boolean pilotCallerSupported(Activity activity, Intent outgoing) {
        return !activity.isDestroyed() && outgoing.getSelector() == null
                && (outgoing.getFlags() & Intent.FLAG_ACTIVITY_FORWARD_RESULT) == 0;
    }

    private boolean redirectKuaishou(Activity activity, Intent outgoing, ParsedLink link) {
        if (!pilotCallerSupported(activity, outgoing)) return false;
        if (link.kind() == ParsedLink.Kind.SHORT_LINK) {
            return KuaishouHandoff.redirectShort(completion -> KuaishouLauncher.resolveShort(
                    activity, link, outgoing, shortLinkReceiver(completion)), reporter);
        }
        return KuaishouHandoff.redirect(() -> KuaishouLauncher.launch(activity, link), reporter);
    }

    private static ResultReceiver shortLinkReceiver(KuaishouHandoff.Completion completion) {
        return new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override protected void onReceiveResult(int code, Bundle data) {
                completion.complete(switch (code) {
                    case KuaishouLauncher.STARTED -> KuaishouHandoff.ShortResult.ACCEPTED;
                    case KuaishouLauncher.CANCELLED -> KuaishouHandoff.ShortResult.CANCELLED;
                    case KuaishouLauncher.FALLBACK_FAILED -> KuaishouHandoff.ShortResult.FALLBACK_FAILED;
                    default -> KuaishouHandoff.ShortResult.FAILED;
                });
            }
        };
    }

    private boolean redirectBili(Activity activity, String url, BrowserDispatch.Target target, long start) {
        HealthReports.emit(reporter, HealthEvent.Kind.ENTERED, HealthEvent.Entry.BROWSER);
        if (!HealthReports.enabled(reporter, HealthEvent.Entry.BROWSER)) return false;
        long matched = SystemClock.elapsedRealtimeNanos();
        boolean space = target.kind() == BrowserDispatch.Kind.SPACE;
        String how = space ? (BilibiliSpaceLauncher.launch(activity, target.id()) ? "personal-space" : null)
                : launch(activity, url, target.id());
        long accepted = SystemClock.elapsedRealtimeNanos();
        String detail = space ? "personal-space" : "id=" + target.id();
        if (how == null) {
            warn(reporter, "HANDOFF 外跳失败，放行宿主浏览器 package=" + reporter.profile().packageName() + " " + detail, null);
            HealthReports.emit(reporter, HealthEvent.Kind.LAUNCH_FAILED, HealthEvent.Entry.BROWSER, HealthEvent.Reason.START_FAILED);
            return false;
        }
        // Logging must not turn an accepted launch into fail-open. No query tokens in logs.
        info(reporter, "HANDOFF blocked host browser package=" + reporter.profile().packageName() + " " + detail + " via " + how
                + " matchUs=" + (matched - start) / 1_000
                + " launchUs=" + (accepted - matched) / 1_000
                + " totalUs=" + (accepted - start) / 1_000);
        HealthReports.emit(reporter, HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER);
        return true;
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
