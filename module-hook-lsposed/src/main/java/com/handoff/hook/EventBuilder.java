package com.handoff.hook;

import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

import com.handoff.api.MiniAppEvent;
import com.handoff.api.Source;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

/**
 * 把 QQ 的类型（LaunchParam / Intent）翻译成中立的 {@link MiniAppEvent}。
 *
 * <p>参数一律按 {@code instanceof} 认，不按下标取（SPEC §6.4）。
 */
public final class EventBuilder {

    private EventBuilder() {
    }

    /** P0：startAppByLink(Context, String link, int linkType, LaunchParam, MiniAppLaunchListener) */
    public static MiniAppEvent fromController(XposedInterface.Chain chain, String processName) {
        Object lp = null;
        String link = null;
        Integer linkType = null;
        for (Object arg : chain.getArgs()) {
            if (arg == null) continue;
            if (arg.getClass().getName().endsWith("LaunchParam")) lp = arg;
            else if (arg instanceof String) link = (String) arg;
            else if (arg instanceof Integer) linkType = (Integer) arg;
        }
        return base(Source.CONTROLLER, processName, lp)
                .link(link)
                .linkType(linkType)
                .build();
    }

    /** P1：openMiniApp(Context, LaunchParam, MiniAppLaunchListener) */
    public static MiniAppEvent fromLauncher(XposedInterface.Chain chain, String processName) {
        Object lp = null;
        for (Object arg : chain.getArgs()) {
            if (arg != null && arg.getClass().getName().endsWith("LaunchParam")) lp = arg;
        }
        return base(Source.LAUNCHER, processName, lp).build();
    }

    /** P2：只读白名单字段；不能把登录等无关 extras 转成日志。 */
    public static MiniAppEvent fromContainer(Intent intent, String processName) {
        return fromContainer(intent, processName, null);
    }

    public static MiniAppEvent fromContainer(Intent intent, String processName, ClassLoader loader) {
        MiniAppEvent.Builder b = MiniAppEvent.builder()
                .source(Source.CONTAINER)
                .processName(processName);
        if (intent == null) return b.build();
        Map<String, String> raw = new HashMap<>();
        try {
            if (loader != null) intent.setExtrasClassLoader(loader);
            Bundle extras = intent.getExtras();
            if (extras != null) {
                for (String key : new String[]{"miniAppId", "appId", "mini_app_id", "miniAppid",
                        "mini_appid", "entryPath", "mini_entryPath", "path", "pagePath", "url",
                        "scene", "sceneId", "appName", "miniAppName", "name", "arkApp", "sourceApp"}) {
                    Object value = extras.get(key);
                    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                        raw.put(key, String.valueOf(value));
                    }
                }
                Object appInfo = extras.get("KEY_APPINFO");
                if (appInfo != null) {
                    raw.put("__payload", "KEY_APPINFO");
                    return ResolvedAppData.read(appInfo, Source.CONTAINER, processName, null, raw);
                }
            }
        } catch (Throwable t) {
            raw.put("__appInfoError", t.getClass().getSimpleName());
        }
        b.rawExtras(raw);
        try {
            if (intent.getDataString() != null) raw.put("__data", intent.getDataString());
            if (intent.getAction() != null) raw.put("__action", intent.getAction());
            raw.put("__flags", Integer.toHexString(intent.getFlags()));
        } catch (Throwable ignored) {
            // 忽略
        }
        b.miniAppId(first(raw, "miniAppId", "appId", "mini_app_id", "miniAppid", "mini_appid"));
        b.entryPath(first(raw, "entryPath", "mini_entryPath", "path", "pagePath", "url"));
        b.scene(intOrNull(first(raw, "scene", "sceneId")));
        b.name(first(raw, "appName", "miniAppName", "name"));
        b.arkApp(first(raw, "appid", "arkApp", "sourceApp"));
        b.meta(first(raw, "meta", "metaData"));
        return b.build();
    }

    private static MiniAppEvent.Builder base(Source source, String processName, Object lp) {
        MiniAppEvent.Builder b = MiniAppEvent.builder()
                .source(source)
                .processName(processName);
        if (lp == null) return b;

        b.miniAppId(Reflect.str(lp, "miniAppId"));
        b.entryPath(Reflect.str(lp, "entryPath"));
        b.fakeUrl(Reflect.str(lp, "fakeUrl"));
        b.scene(Reflect.integer(lp, "scene"));
        b.via(Reflect.str(lp, "via"));

        Object info = Reflect.get(lp, "miniAppInfo");
        if (info != null) {
            b.name(Reflect.str(info, "name"));
        }
        // M1.1 字段普查：确认 LaunchParam 里哪些字段真被填过
        b.rawExtras(Collections.singletonMap("__launchParam", Dump.describe(lp, 2)));
        return b;
    }

    private static String first(Map<String, String> map, String... keys) {
        for (String k : keys) {
            String v = map.get(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static Integer intOrNull(String s) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 当前进程名；取不到时退回包名。 */
    public static String currentProcessName(String fallback) {
        try {
            String name = Process.myProcessName();
            return name == null ? fallback : name;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
