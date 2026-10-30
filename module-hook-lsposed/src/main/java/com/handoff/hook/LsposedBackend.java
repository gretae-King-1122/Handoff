package com.handoff.hook;

import android.os.SystemClock;
import android.app.Application;
import android.app.Instrumentation;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import com.handoff.api.EventSink;
import com.handoff.api.HookBackend;

import io.github.libxposed.api.XposedInterface;
import com.handoff.api.health.HealthEvent;
import com.handoff.api.health.QqAdapters;

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
        default void applicationReady(Application app) { }
        default void event(HealthEvent event) { }
        default boolean enabled(HealthEvent.Entry entry) { return true; }
        default QqAdapters.Profile profile() { return QqAdapters.resolve(-1); }
    }

    private final XposedInterface xposed;
    private final Reporter reporter;
    private final boolean diagnostics;
    private final boolean boundaryDiagnostics;
    private static final RuntimeLoaderBinding BINDINGS = new RuntimeLoaderBinding();
    private static final AtomicBoolean APPLICATION_HOOK = new AtomicBoolean();

    public LsposedBackend(XposedInterface xposed, Reporter reporter) {
        this(xposed, reporter, false);
    }

    public LsposedBackend(XposedInterface xposed, Reporter reporter, boolean diagnostics) {
        this(xposed, reporter, diagnostics, false);
    }

    public LsposedBackend(XposedInterface xposed, Reporter reporter, boolean diagnostics, boolean boundaryDiagnostics) {
        this.xposed = xposed;
        this.reporter = reporter;
        this.diagnostics = diagnostics;
        this.boundaryDiagnostics = boundaryDiagnostics;
    }

    @Override
    public void install(ClassLoader classLoader, String processName, EventSink sink) {
        if ("com.tencent.mobileqq".equals(processName)) {
            step("RuntimeApplicationBinding", () -> observeApplication(processName, sink));
        }
        step("PackageLoaderBinding", () -> BINDINGS.install(classLoader,
                loader -> installForLoader(loader, processName, sink)));
    }

    private void observeApplication(String processName, EventSink sink) throws Throwable {
        if (!APPLICATION_HOOK.compareAndSet(false, true)) return;
        try {
            Method method = Instrumentation.class.getDeclaredMethod("callApplicationOnCreate", Application.class);
            xposed.hook(method).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain ->
                    BINDINGS.beforeApplication(() -> {
                        Application app = chain.getArgs().get(0) instanceof Application
                                ? (Application) chain.getArgs().get(0) : null;
                        if (app != null && "com.tencent.mobileqq".equals(app.getPackageName())) {
                            try { reporter.applicationReady(app); } catch (Throwable ignored) { }
                        }
                        return app != null && "com.tencent.mobileqq".equals(app.getPackageName())
                                ? app.getClassLoader() : null;
                    }, loader -> {
                        info("HANDOFF binding actual Application loader=" + loader.getClass().getName());
                        installForLoader(loader, processName, sink);
                        HealthReports.emit(reporter, HealthEvent.Kind.LOADER_BOUND, HealthEvent.Entry.SYSTEM);
                    }, error -> {
                        try { reporter.warn("HANDOFF runtime loader binding failed", error); } catch (Throwable ignored) { }
                    }, chain::proceed));
            info("HANDOFF Application loader binding installed");
        } catch (Throwable error) {
            APPLICATION_HOOK.set(false);
            throw error;
        }
    }

    private void installForLoader(ClassLoader classLoader, String processName, EventSink sink) {
        long start = SystemClock.elapsedRealtimeNanos();
        // Browser is always first. Normal mode must not resolve any historical probe's QQ classes.
        for (HookPlan.Step target : HookPlan.steps(processName, diagnostics)) {
            switch (target) {
                case BROWSER -> step("M2 BrowserHandoff", () -> BrowserHandoff.install(xposed, classLoader, reporter));
                case MINI_SDK -> step("MiniSdkHandoff", () -> MiniAppHandoff.installSdk(xposed, classLoader, reporter, boundaryDiagnostics));
                case MINI_HANDOFF -> step("M2 MiniAppHandoff", () -> MiniAppHandoff.install(xposed, classLoader, reporter, boundaryDiagnostics));
                case MINI_CONTAINER -> step("MiniContainerHandoff", () -> MiniContainerHandoff.install(xposed, reporter));
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
        if (boundaryDiagnostics && "com.tencent.mobileqq".equals(processName)) {
            step("MiniBoundaryProbe", () -> MiniBoundaryProbe.install(xposed, classLoader, reporter));
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
            HealthEvent.Entry entry = name.contains("Browser") ? HealthEvent.Entry.BROWSER
                    : name.contains("MiniSdk") ? HealthEvent.Entry.SDK
                    : name.contains("MiniAppHandoff") ? HealthEvent.Entry.SERVICE
                    : name.contains("MiniContainer") ? HealthEvent.Entry.CONTAINER : HealthEvent.Entry.SYSTEM;
            HealthReports.emit(reporter, HealthEvent.Kind.INSTALL_FAILED, entry, HealthEvent.Reason.SIGNATURE);
        }
    }

    private void info(String message) {
        try { reporter.info(message); } catch (Throwable ignored) { }
    }
}
