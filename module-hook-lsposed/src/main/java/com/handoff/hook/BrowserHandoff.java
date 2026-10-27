package com.handoff.hook;

import android.app.Activity;
import android.content.ActivityNotFoundException;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface;

/**
 * M2（第一刀）：B 站分享卡片在被 QQ 内置浏览器接走之前截下来。
 *
 * <p>链路已验证：{@code QQBrowserDelegationActivity}（主进程）→ 400ms 后
 * {@code QQBrowserActivity}（:tool 进程）。跳板页的 intent 里带明文 {@code url} extra，
 * 在这里拦最划算：命中 B 站视频就直接交给 B 站 App，没命中就原样放行。
 */
final class BrowserHandoff {

    private static final String BILI_PACKAGE = "tv.danmaku.bili";

    private static final String DELEGATION_ACTIVITY = "com.tencent.mobileqq.activity.QQBrowserDelegationActivity";

    /** bilibili.com/video/av123… 或 /video/BV1xx411c7mD */
    private static final Pattern BILI_VIDEO =
            Pattern.compile("bilibili\\.com/video/(av\\d+|BV[0-9A-Za-z]{10})", Pattern.CASE_INSENSITIVE);

    private BrowserHandoff() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        Class<?> cls;
        try {
            cls = Class.forName(DELEGATION_ACTIVITY, false, cl);
        } catch (Throwable t) {
            reporter.warn("HANDOFF 找不到跳板页", t);
            return;
        }
        Method onCreate = Reflect.method(cls, "onCreate", 1);
        if (onCreate == null) {
            reporter.warn("HANDOFF 找不到 onCreate", null);
            return;
        }
        try {
            xposed.hook(onCreate)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object self = chain.getThisObject();
                            if (self instanceof Activity) {
                                Activity activity = (Activity) self;
                                String url = activity.getIntent() == null
                                        ? null
                                        : activity.getIntent().getStringExtra("url");
                                if (url != null) {
                                    Matcher m = BILI_VIDEO.matcher(url);
                                    if (m.find()) {
                                        String id = m.group(1);
                                        String how = launch(activity, url, id);
                                        if (how != null) {
                                            reporter.info("HANDOFF hit " + id + " via " + how + " → " + url);
                                            activity.finish();
                                        } else {
                                            reporter.warn("HANDOFF 拉起失败，放行浏览器", null);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                            // 任何异常都放行，绝不挡用户
                        }
                        return result;
                    });
            reporter.info("HANDOFF BrowserHandoff installed");
        } catch (Throwable t) {
            reporter.warn("HANDOFF 挂载失败", t);
        }
    }

    /**
     * 按降级链尝试拉起 B 站 App，返回命中的方式（null = 全失败）。
     *
     * <p>本机实测：B 站的 https App Links **没有** verified，直接 ACTION_VIEW 会落到默认浏览器，
     * 所以必须 `setPackage` 指定包，再不行就用 {@code bilibili://} scheme。
     */
    private static String launch(Context context, String originalUrl, String id) {
        boolean isAv = id.startsWith("av") || id.chars().allMatch(Character::isDigit);
        String aid = id.startsWith("av") ? id.substring(2) : id;

        // ① 原 https URL，但指定 package —— 不依赖 App Links 验证
        if (tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl)), "http+setPackage")) {
            return "http+setPackage";
        }
        // ② bilibili://video/<aid>
        if (isAv) {
            if (tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://video/" + aid)), "scheme:aid")) {
                return "scheme:aid";
            }
            if (tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://video/av" + aid)), "scheme:av")) {
                return "scheme:av";
            }
        } else {
            if (tryStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://video/" + id)), "scheme:bvid")) {
                return "scheme:bvid";
            }
        }
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

    private static boolean tryStart(Context context, Intent intent, String label) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(BILI_PACKAGE);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
