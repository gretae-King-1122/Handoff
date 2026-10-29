package com.handoff.hook;

import android.os.SystemClock;

import com.handoff.api.EventSink;
import com.handoff.api.HookBackend;

import io.github.libxposed.api.XposedInterface;

/**
 * libxposed 后端：正常模式安装浏览器与小程序交接，诊断模式再加只读发现探针。
 *
 * <p>QQ 类型保留在 Hook 边界，诊断事件以中立数据传给下游；浏览器交接仍是独立实现。
 */
public final class LsposedBackend implements HookBackend {

    /** 日志与异常上报；由 `:app` 注入，便于统一控制 debug 开关。 */
    public interface Reporter {
        void info(String message);

        void warn(String message, Throwable cause);
    }

    private final XposedInterface xposed;
    private final Reporter reporter;
    private final boolean diagnostics;
    private static final LoaderInstallGuard INSTALLATIONS = new LoaderInstallGuard();

    public LsposedBackend(XposedInterface xposed, Reporter reporter) {
        this(xposed, reporter, false);
    }

    public LsposedBackend(XposedInterface xposed, Reporter reporter, boolean diagnostics) {
        this.xposed = xposed;
        this.reporter = reporter;
        this.diagnostics = diagnostics;
    }

    @Override
    public void install(ClassLoader classLoader, String processName, EventSink sink) {
        if (!INSTALLATIONS.claim(classLoader)) return;
        long start = SystemClock.elapsedRealtimeNanos();
        // Browser is always first. Normal mode must not resolve any historical probe's QQ classes.
        for (HookPlan.Step target : HookPlan.steps(processName, diagnostics)) {
            switch (target) {
                case BROWSER -> step("M2 BrowserHandoff", () -> BrowserHandoff.install(xposed, classLoader, reporter));
                case MINI_HANDOFF -> step("M2 MiniAppHandoff", () -> MiniAppHandoff.install(xposed, classLoader, reporter));
                case CONTROLLER -> step("P0 ControllerHook", () -> ControllerHook.install(xposed, classLoader, processName, sink, reporter));
                case LAUNCHER -> step("P1 LauncherHook", () -> LauncherHook.install(xposed, classLoader, processName, sink));
                case MINI_RUNTIME -> step("M2.1 MiniRuntimeProbe", () -> MiniRuntimeProbe.install(xposed, classLoader, processName, sink, reporter));
                case ARK_CLICK -> step("M1.2 ArkClickProbe", () -> ArkClickProbe.install(xposed, classLoader, reporter));
                case MINI_ENTRY -> step("M1.4 MiniEntryProbe", () -> MiniEntryProbe.install(xposed, classLoader, reporter));
                case ARK_MESSAGE -> step("M1.5 ArkMessageProbe", () -> ArkMessageProbe.install(xposed, classLoader, reporter));
                case TEMPLATE -> step("M1.7 TemplateProbe", () -> TemplateProbe.install(xposed, classLoader, reporter));
                case STRUCT -> step("M1.8 StructProbe", () -> StructProbe.install(xposed, classLoader, reporter));
            }
        }
        info("HANDOFF runtime diagnostics=" + diagnostics + " installUs="
                + (SystemClock.elapsedRealtimeNanos() - start) / 1_000);
    }

    private interface Step {
        void run() throws Throwable;
    }

    private void step(String name, Step body) {
        try {
            body.run();
            // Steps may intentionally skip unavailable classes; this isn't proof of matched methods.
            info(name + " setup complete");
        } catch (Throwable t) {
            // 该点跳过，其它点继续；QQ 无感
            try { reporter.warn(name + " install failed", t); } catch (Throwable ignored) { }
        }
    }

    private void info(String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }
}
