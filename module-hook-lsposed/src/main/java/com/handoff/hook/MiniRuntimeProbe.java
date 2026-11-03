package com.handoff.hook;

import android.app.Activity;
import android.content.Intent;
import android.os.Process;
import android.os.SystemClock;

import com.handoff.api.EventSink;
import com.handoff.api.MiniAppEvent;
import com.handoff.api.Source;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;

/** 解析后/容器/热复用观测。所有新 Hook 都只记录，永远执行一次原方法且不接受阻断结果。 */
final class MiniRuntimeProbe {
    private static final Set<Method> INSTALLED = ConcurrentHashMap.newKeySet();
    private static final Set<ClassLoader> LOADERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<Boolean> INSTALLING = ThreadLocal.withInitial(() -> false);
    private static final String DEX_LOADER = "com.tencent.qqmini.sdk.launcher.dynamic.MiniAppDexLoader";

    private final XposedInterface xposed;
    private final String process;
    private final EventSink sink;
    private final LsposedBackend.Reporter reporter;

    private MiniRuntimeProbe(XposedInterface xposed, String process, EventSink sink, LsposedBackend.Reporter reporter) {
        this.xposed = xposed;
        this.process = process;
        this.sink = sink;
        this.reporter = reporter;
    }

    static void install(XposedInterface xposed, ClassLoader loader, String process,
                        EventSink sink, LsposedBackend.Reporter reporter) {
        new MiniRuntimeProbe(xposed, process, sink, reporter).installLoader(loader);
    }

    private void installLoader(ClassLoader loader) {
        if (loader == null || INSTALLING.get()) return;
        synchronized (LOADERS) {
            if (!LOADERS.add(loader)) return;
        }
        INSTALLING.set(true);
        try {
            for (String name : HookTargets.RUNTIME_CLASSES) {
                installNamedClass(name, loader);
            }
        } finally {
            INSTALLING.remove();
        }
    }

    private void installNamedClass(String name, ClassLoader loader) {
        try {
            installClass(Class.forName(name, false, loader));
        } catch (ClassNotFoundException ignored) {
            info(reporter, "[MINI-PROBE] deferred " + name + " loader=" + ResolvedAppData.loaderId(loader));
        } catch (Throwable t) {
            warn(reporter, "[MINI-PROBE] class install failed " + name, t);
        }
    }

    private void installClass(Class<?> cls) {
        String name = cls.getName();
        boolean container = false;
        for (String candidate : HookTargets.P2_CLASSES) if (candidate.equals(name)) container = true;
        int matched = 0;
        for (Method method : cls.getDeclaredMethods()) {
            if (installMethod(method, container)) matched++;
        }
        info(reporter, "[MINI-PROBE] class=" + name + " matched=" + matched
                + " loader=" + ResolvedAppData.loaderId(cls.getClassLoader()));
    }

    private boolean installMethod(Method method, boolean container) {
        if (method.isSynthetic()) return false;
        if (method.getDeclaringClass().getName().equals(DEX_LOADER) && isLoaderMethod(method)) {
            hookLoader(method);
        } else if (container && isLifecycleMethod(method)) {
            hookContainer(method);
        } else if (isResolvedMethod(method)) {
            hookResolved(method);
        } else {
            return false;
        }
        return true;
    }

    private static boolean isLoaderMethod(Method m) {
        Class<?>[] p = m.getParameterTypes();
        return ((m.getName().equals("findClass") || m.getName().equals("create"))
                && p.length == 1 && p[0] == String.class)
                || (m.getName().equals("loadDex") && p.length == 0);
    }

    private static boolean isLifecycleMethod(Method m) {
        Class<?>[] p = m.getParameterTypes();
        return (m.getName().equals("doOnCreate") && p.length == 1 && p[0] == android.os.Bundle.class)
                || (m.getName().equals("doOnNewIntent") && p.length == 1 && p[0] == Intent.class)
                || (m.getName().equals("doOnResume") && p.length == 0);
    }

    private static boolean isResolvedMethod(Method m) {
        for (HookTargets.RuntimeTarget target : HookTargets.RUNTIME_METHODS) {
            if (!m.getDeclaringClass().getName().equals(target.className)
                    || !m.getName().equals(target.methodName)) continue;
            if (matchesSignature(m, target.parameterTypes)) return true;
        }
        return false;
    }

    private static boolean matchesSignature(Method method, String[] expected) {
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != expected.length || method.getReturnType() != void.class) return false;
        for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].getName().equals(expected[i])) return false;
        }
        return true;
    }

    private interface Observation {
        void record(XposedInterface.Chain chain) throws Throwable;
    }

    private void observe(Method method, boolean before, Observation observation) {
        if (!INSTALLED.add(method)) return;
        AtomicBoolean warned = new AtomicBoolean();
        try {
            xposed.hook(method).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> {
                if (before) record(observation, chain, method, warned);
                // 必须在探针异常捕获之外：原方法抛异常时不能重试 proceed，也不能吞异常。
                Object result = chain.proceed();
                if (!before) record(observation, chain, method, warned);
                return result;
            });
            info(reporter, "[MINI-PROBE] installed " + method.toGenericString()
                    + " loader=" + ResolvedAppData.loaderId(method.getDeclaringClass().getClassLoader()));
        } catch (Throwable t) {
            INSTALLED.remove(method);
            warn(reporter, "[MINI-PROBE] hook failed " + method.getName(), t);
        }
    }

    private void record(Observation observation, XposedInterface.Chain chain, Method method, AtomicBoolean warned) {
        try {
            observation.record(chain);
        } catch (Throwable t) {
            if (warned.compareAndSet(false, true)) {
                warn(reporter, "[MINI-PROBE] observation failed " + method.getName(), t);
            }
        }
    }

    private static void info(LsposedBackend.Reporter reporter, String message) {
        try {
            reporter.info(message);
        } catch (Throwable ignored) {
            // 日志通道失效不能改变已安装状态，也不能让已执行的原方法被框架重试。
        }
    }

    private static void warn(LsposedBackend.Reporter reporter, String message, Throwable cause) {
        try {
            reporter.warn(message, cause);
        } catch (Throwable ignored) {
            // 同上，尤其不能从 proceed() 之后的探针泄漏异常。
        }
    }

    private static Map<String, String> diagnostics(Method method, String phase) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("__hook", method.getDeclaringClass().getName() + '#' + method.getName());
        raw.put("__phase", phase);
        raw.put("__pid", String.valueOf(Process.myPid()));
        raw.put("__elapsedMs", String.valueOf(SystemClock.elapsedRealtime()));
        raw.put("__hookLoader", ResolvedAppData.loaderId(method.getDeclaringClass().getClassLoader()));
        return raw;
    }

    private void hookResolved(Method method) {
        // Immutable method metadata: resolve once at install, not for each launch/page event.
        Class<?>[] parameters = method.getParameterTypes();
        boolean page = method.getName().equals("onAppShowOrHideWithPath");
        observe(method, true, chain -> recordResolved(chain, method, parameters, page));
    }

    private record ResolvedCall(Activity activity, Object model) { }

    private static ResolvedCall readResolvedCall(XposedInterface.Chain chain, Class<?>[] parameters) {
        Object model = null;
        Activity activity = null;
        for (int i = 0; i < parameters.length; i++) {
            Object arg = chain.getArgs().get(i);
            if (arg instanceof Activity) activity = (Activity) arg;
            // Use the verified declared type, never a runtime subclass name.
            if (parameters[i].getName().equals("com.tencent.mobileqq.mini.apkg.MiniAppConfig")
                    || parameters[i].getName().equals("com.tencent.qqmini.sdk.launcher.model.MiniAppInfo")) model = arg;
        }
        return new ResolvedCall(activity, model);
    }

    private void recordResolved(XposedInterface.Chain chain, Method method, Class<?>[] parameters, boolean page) {
        ResolvedCall call = readResolvedCall(chain, parameters);
        if (call.model() == null) return;
        Map<String, String> raw = diagnostics(method, page ? "page-notification" : "before-launch");
        if (call.activity() != null) addActivity(raw, call.activity());
        String path = page ? pagePath(chain, raw) : null;
        // Deliberately ignore sink's return: these observations must never block a host call.
        sink.onEvent(ResolvedAppData.read(call.model(), page ? Source.PAGE : Source.RESOLVED, process, path, raw));
    }

    private static String pagePath(XposedInterface.Chain chain, Map<String, String> raw) {
        // Signature already matched exactly; do not guess using the last String argument.
        raw.put("__status", String.valueOf(chain.getArgs().get(0)));
        Object reportedProcess = chain.getArgs().get(1);
        if (reportedProcess != null) raw.put("__reportedProcess", (String) reportedProcess);
        return (String) chain.getArgs().get(3);
    }

    private void hookContainer(Method method) {
        observe(method, false, chain -> recordContainer(chain, method));
    }

    private void recordContainer(XposedInterface.Chain chain, Method method) {
        if (!(chain.getThisObject() instanceof Activity activity)) return;
        Intent intent = method.getName().equals("doOnNewIntent")
                ? (Intent) chain.getArgs().get(0) : activity.getIntent();
        MiniAppEvent event = EventBuilder.fromContainer(intent, process, activity.getClass().getClassLoader());
        Map<String, String> raw = diagnostics(method,
                method.getName().equals("doOnResume") ? "resume-snapshot-not-click" : "after-lifecycle");
        raw.putAll(event.getRawExtras());
        addActivity(raw, activity);
        sink.onEvent(event.copy(event.getSource(), event.getProcessName(), event.getMiniAppId(), event.getName(),
                event.getEntryPath(), event.getFakeUrl(), event.getLink(), event.getLinkType(), event.getScene(),
                event.getVia(), event.getArkApp(), event.getMeta(), raw));
    }

    private static void addActivity(Map<String, String> raw, Activity activity) {
        raw.put("__activity", activity.getClass().getName());
        raw.put("__activityIdentity", Integer.toHexString(System.identityHashCode(activity)));
        raw.put("__taskId", String.valueOf(activity.getTaskId()));
    }

    private void hookLoader(Method method) {
        // loadDex/create/findClass 的返回值由专用 hook 处理，不能在普通 Observation 中二次调用原方法。
        if (!INSTALLED.add(method)) return;
        try {
            xposed.hook(method).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> {
                Object result = chain.proceed();
                afterLoader(chain, result);
                return result;
            });
            info(reporter, "[MINI-PROBE] installed loader observer " + method.toGenericString());
        } catch (Throwable t) {
            INSTALLED.remove(method);
            warn(reporter, "[MINI-PROBE] loader observer failed " + method.getName(), t);
        }
    }

    private void afterLoader(XposedInterface.Chain chain, Object result) {
        // Called after proceed: observer failures cannot escape or cause the host call to repeat.
        try {
            if (INSTALLING.get()) return;
            installResolvedClass(result);
            Object dynamicLoader = Reflect.get(chain.getThisObject(), "mNewClassLoader");
            if (dynamicLoader instanceof ClassLoader loader) installLoader(loader);
        } catch (Throwable t) {
            warn(reporter, "[MINI-PROBE] dynamic install failed", t);
        }
    }

    private void installResolvedClass(Object result) {
        Class<?> actual = result instanceof Class<?> ? (Class<?>) result : result == null ? null : result.getClass();
        if (actual == null) return;
        for (String target : HookTargets.RUNTIME_CLASSES) {
            if (actual.getName().equals(target)) installClass(actual);
        }
    }
}
