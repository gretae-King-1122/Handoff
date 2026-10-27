package com.handoff.hook;

import com.handoff.api.EventSink;
import com.handoff.api.HookBackend;

import io.github.libxposed.api.XposedInterface;

/**
 * libxposed 后端：注册三个 hook 点，每个点独立 try，任一失败不影响其它点。
 *
 * <p>只做「反射 + 框架调用 + 事件转换」，不含任何规则或执行逻辑（ADR-0003）。
 */
public final class LsposedBackend implements HookBackend {

    /** 日志与异常上报；由 `:app` 注入，便于统一控制 debug 开关。 */
    public interface Reporter {
        void info(String message);

        void warn(String message, Throwable cause);
    }

    private final XposedInterface xposed;
    private final Reporter reporter;

    public LsposedBackend(XposedInterface xposed, Reporter reporter) {
        this.xposed = xposed;
        this.reporter = reporter;
    }

    @Override
    public void install(ClassLoader classLoader, String processName, EventSink sink) {
        step("P0 ControllerHook", () -> ControllerHook.install(xposed, classLoader, processName, sink, reporter));
        step("P1 LauncherHook", () -> LauncherHook.install(xposed, classLoader, processName, sink));
        step("P2 ContainerHook", () -> ContainerHook.install(xposed, classLoader, processName, sink));
        // M1.2 / M1.4 探针：只在主进程挂，验证 Ark 卡片数据与启动链入参
        if (!processName.contains(":")) {
            step("M1.2 ArkClickProbe", () -> ArkClickProbe.install(xposed, classLoader, reporter));
            step("M1.4 MiniEntryProbe", () -> MiniEntryProbe.install(xposed, classLoader, reporter));
            step("M1.5 ArkMessageProbe", () -> ArkMessageProbe.install(xposed, classLoader, reporter));
            step("M1.7 TemplateProbe", () -> TemplateProbe.install(xposed, classLoader, reporter));
            step("M1.8 StructProbe", () -> StructProbe.install(xposed, classLoader, reporter));
        }
    }

    private interface Step {
        void run() throws Throwable;
    }

    private void step(String name, Step body) {
        try {
            body.run();
            reporter.info(name + " installed");
        } catch (Throwable t) {
            // 该点跳过，其它点继续；QQ 无感
            reporter.warn(name + " install failed", t);
        }
    }
}
