package com.handoff.hook;

import com.handoff.api.MiniAppEvent;
import com.handoff.api.Source;

import java.util.LinkedHashMap;
import java.util.Map;

/** 定向读取 QQ/SDK 启动模型；不遍历对象、不读登录信息、不拼接跨调用状态。 */
final class ResolvedAppData {
    private ResolvedAppData() { }

    static MiniAppEvent read(Object value, Source source, String processName,
                             String currentPath, Map<String, String> diagnostics) {
        Object config = Reflect.get(value, "config");
        Object info = config == null ? value : config;
        Object lp = Reflect.get(value, "launchParam");
        if (lp == null) lp = Reflect.get(info, "launchParam");

        String infoId = Reflect.str(info, "appId");
        String launchId = Reflect.str(lp, "miniAppId");
        String launchPath = Reflect.str(lp, "entryPath");
        String firstPage = Reflect.str(Reflect.get(info, "firstPage"), "pagePath");
        String configPath = config == null ? null : Reflect.str(value, "entryPath");
        String firstPath = Reflect.str(info, "firstPath");
        String link = first(Reflect.str(value, "link"), Reflect.str(info, "link"));

        Map<String, String> raw = new LinkedHashMap<>(diagnostics);
        put(raw, "__infoAppId", infoId);
        put(raw, "__launchAppId", launchId);
        put(raw, "__currentPath", currentPath);
        put(raw, "__launchPath", launchPath);
        put(raw, "__firstPagePath", firstPage);
        put(raw, "__configPath", configPath);
        put(raw, "__firstPath", firstPath);
        put(raw, "__launchClickTimeMillis", Reflect.str(lp, "launchClickTimeMillis"));
        put(raw, "__timestamp", Reflect.str(lp, "timestamp"));
        if (infoId != null && launchId != null && !infoId.equals(launchId)) {
            raw.put("__identityConflict", "true");
        }
        if (value != null) {
            raw.put("__model", value.getClass().getName());
            raw.put("__modelIdentity", Integer.toHexString(System.identityHashCode(value)));
            raw.put("__modelLoader", loaderId(value.getClass().getClassLoader()));
        }
        // 仅探针展示用：保留全部候选，后续决策不得把此顺序当成已验证的 QQ 选页规则。
        String path = first(currentPath, launchPath, firstPage, configPath, firstPath);
        String origin = currentPath != null && !currentPath.isEmpty() ? "currentPath"
                : launchPath != null ? "launchParam.entryPath"
                : firstPage != null ? "firstPage.pagePath"
                : configPath != null ? "config.entryPath" : firstPath != null ? "firstPath" : null;
        put(raw, "__pathOrigin", origin);
        return MiniAppEvent.builder().source(source).processName(processName)
                .miniAppId(first(infoId, launchId)).name(Reflect.str(info, "name"))
                .entryPath(path).fakeUrl(Reflect.str(lp, "fakeUrl")).link(link)
                .scene(Reflect.integer(lp, "scene")).via(Reflect.str(lp, "via"))
                .rawExtras(raw).build();
    }

    static String loaderId(ClassLoader loader) {
        return loader == null ? "bootstrap" : loader.getClass().getName() + '@'
                + Integer.toHexString(System.identityHashCode(loader));
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) map.put(key, value);
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isEmpty()) return value;
        return null;
    }
}
