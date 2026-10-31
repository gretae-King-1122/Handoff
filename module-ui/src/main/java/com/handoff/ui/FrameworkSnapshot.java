package com.handoff.ui;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;
import java.util.List;

/** A worker-thread snapshot. Metadata, scope and running-target failures remain independent. */
record FrameworkSnapshot(boolean connected, String name, String version, long versionCode, int api,
                         boolean scopeKnown, boolean scoped, RuntimeState runtime) {
    enum RuntimeState { UNKNOWN, NOT_RUNNING, CURRENT, OUTDATED, RELOADING }
    record Target(String process, long moduleCode, HookedTarget.State state) { }
    interface Source {
        String name(); String version(); long versionCode(); int api();
        List<String> scope(); List<Target> targets();
    }

    static FrameworkSnapshot unavailable() {
        return new FrameworkSnapshot(false, "未知", "未知", 0, 0, false, false, RuntimeState.UNKNOWN);
    }
    String label() { return connected ? name + " " + version : "unknown"; }
    String scopeLabel() { return !scopeKnown ? "未知" : scoped ? "已勾选 QQ" : "未勾选 QQ"; }

    static FrameworkSnapshot read(XposedService service, long moduleCode) {
        if (service == null) return unavailable();
        return read(new Source() {
            public String name() { return service.getFrameworkName(); }
            public String version() { return service.getFrameworkVersion(); }
            public long versionCode() { return service.getFrameworkVersionCode(); }
            public int api() { return service.getApiVersion(); }
            public List<String> scope() { return service.getScope(); }
            public List<Target> targets() {
                return service.getRunningTargets().stream().map(t ->
                        new Target(t.getProcessName(), t.getLoadedVersionCode(), t.getState())).toList();
            }
        }, moduleCode);
    }
    static FrameworkSnapshot read(Source source, long moduleCode) {
        String name, version; long versionCode; int api;
        try {
            name = source.name(); version = source.version(); versionCode = source.versionCode(); api = source.api();
        } catch (RuntimeException | LinkageError error) { return unavailable(); }
        boolean known = false, scoped = false;
        try { scoped = source.scope().contains("com.tencent.mobileqq"); known = true; }
        catch (RuntimeException | LinkageError ignored) { }
        RuntimeState runtime = RuntimeState.UNKNOWN;
        if (api >= 102) try { runtime = runtime(source.targets(), moduleCode); }
        catch (RuntimeException | LinkageError ignored) { }
        return new FrameworkSnapshot(true, name, version, versionCode, api, known, scoped, runtime);
    }
    static RuntimeState runtime(List<Target> targets, long moduleCode) {
        RuntimeState result = RuntimeState.NOT_RUNNING;
        for (Target t : targets) {
            // The mini-app subprocess does not prove the main-process hooks are loaded.
            if (!"com.tencent.mobileqq".equals(t.process())) continue;
            if (moduleCode > 0 && t.moduleCode() == moduleCode && t.state() == HookedTarget.State.UP_TO_DATE)
                return RuntimeState.CURRENT;
            result = t.state() == HookedTarget.State.RELOADING ? RuntimeState.RELOADING : RuntimeState.OUTDATED;
        }
        return result;
    }
}
