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

    private MiniRuntimeProbe() { }

    static void install(XposedInterface xposed, ClassLoader loader, String process,
                        EventSink sink, LsposedBackend.Reporter reporter) {
        installLoader(xposed, loader, process, sink, reporter);
    }

    private static void installLoader(XposedInterface xposed, ClassLoader loader, String process,
                                      EventSink sink, LsposedBackend.Reporter reporter) {
        if (loader == null || INSTALLING.get()) return;
        synchronized (LOADERS) {
            if (!LOADERS.add(loader)) return;
        }
        INSTALLING.set(true);
        try {
            for (String name : HookTargets.RUNTIME_CLASSES) {
                try {
                    installClass(xposed, Class.forName(name, false, loader), process, sink, reporter);
                } catch (ClassNotFoundException ignored) {
                    info(reporter, "[MINI-PROBE] deferred " + name + " loader=" + ResolvedAppData.loaderId(loader));
                } catch (Throwable t) {
                    warn(reporter, "[MINI-PROBE] class install failed " + name, t);
                }
            }
        } finally {
            INSTALLING.remove();
        }
    }

    private static void installClass(XposedInterface xposed, Class<?> cls, String process,
                                     EventSink sink, LsposedBackend.Reporter reporter) {
        String name = cls.getName();
        boolean container = false;
        for (String candidate : HookTargets.P2_CLASSES) if (candidate.equals(name)) container = true;
        int matched = 0;
        for (Method method : cls.getDeclaredMethods()) {
            if (method.isSynthetic()) continue;
            if (name.equals(DEX_LOADER) && isLoaderMethod(method)) {
                matched++;
                hookLoader(xposed, method, process, sink, reporter);
            } else if (container && isLifecycleMethod(method)) {
                matched++;
                hookContainer(xposed, method, process, sink, reporter);
            } else if (isResolvedMethod(method)) {
                matched++;
                hookResolved(xposed, method, process, sink, reporter);
            }
        }
        info(reporter, "[MINI-PROBE] class=" + name + " matched=" + matched
                + " loader=" + ResolvedAppData.loaderId(cls.getClassLoader()));
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
            Class<?>[] parameters = m.getParameterTypes();
            if (parameters.length != target.parameterTypes.length || m.getReturnType() != void.class) continue;
            boolean matches = true;
            for (int i = 0; i < parameters.length; i++) {
                if (!parameters[i].getName().equals(target.parameterTypes[i])) matches = false;
            }
            if (matches) return true;
        }
        return false;
    }

    private interface Observation {
        void record(XposedInterface.Chain chain) throws Throwable;
    }

    private static void observe(XposedInterface xposed, Method method, boolean before,
                                Observation observation, LsposedBackend.Reporter reporter) {
        if (!INSTALLED.add(method)) return;
        AtomicBoolean warned = new AtomicBoolean();
        try {
            xposed.hook(method).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> {
                if (before) record(observation, chain, method, reporter, warned);
                // 必须在探针异常捕获之外：原方法抛异常时不能重试 proceed，也不能吞异常。
                Object result = chain.proceed();
                if (!before) record(observation, chain, method, reporter, warned);
                return result;
            });
            info(reporter, "[MINI-PROBE] installed " + method.toGenericString()
                    + " loader=" + ResolvedAppData.loaderId(method.getDeclaringClass().getClassLoader()));
        } catch (Throwable t) {
            INSTALLED.remove(method);
            warn(reporter, "[MINI-PROBE] hook failed " + method.getName(), t);
        }
    }

    private static void record(Observation observation, XposedInterface.Chain chain, Method method,
                               LsposedBackend.Reporter reporter, AtomicBoolean warned) {
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

    private static void hookResolved(XposedInterface xposed, Method method, String process,
                                     EventSink sink, LsposedBackend.Reporter reporter) {
        // Immutable method metadata: resolve once at install, not for each launch/page event.
        Class<?>[] parameters = method.getParameterTypes();
        boolean page = method.getName().equals("onAppShowOrHideWithPath");
        observe(xposed, method, true, chain -> {
            Object model = null;
            Activity activity = null;
            for (int i = 0; i < parameters.length; i++) {
                Object arg = chain.getArgs().get(i);
                if (arg instanceof Activity) activity = (Activity) arg;
                // 使用已验证的声明类型，而不是运行时子类名。
                if (parameters[i].getName().equals("com.tencent.mobileqq.mini.apkg.MiniAppConfig")
                        || parameters[i].getName().equals("com.tencent.qqmini.sdk.launcher.model.MiniAppInfo")) model = arg;
            }
            if (model == null) return;
            Map<String, String> raw = diagnostics(method, page ? "page-notification" : "before-launch");
            if (activity != null) addActivity(raw, activity);
            String path = null;
            if (page) {
                // 此处只有签名精确匹配后才会挂载，不能按“最后一个 String”猜参数。
                raw.put("__status", String.valueOf(chain.getArgs().get(0)));
                Object reportedProcess = chain.getArgs().get(1);
                if (reportedProcess != null) raw.put("__reportedProcess", (String) reportedProcess);
                path = (String) chain.getArgs().get(3);
            }
            // sink 返回值故意忽略；来源/status/路径优先级尚未通过真机验收。
            sink.onEvent(ResolvedAppData.read(model, page ? Source.PAGE : Source.RESOLVED, process, path, raw));
        }, reporter);
    }

    private static void hookContainer(XposedInterface xposed, Method method, String process,
                                      EventSink sink, LsposedBackend.Reporter reporter) {
        observe(xposed, method, false, chain -> {
            if (!(chain.getThisObject() instanceof Activity)) return;
            Activity activity = (Activity) chain.getThisObject();
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
        }, reporter);
    }

    private static void addActivity(Map<String, String> raw, Activity activity) {
        raw.put("__activity", activity.getClass().getName());
        raw.put("__activityIdentity", Integer.toHexString(System.identityHashCode(activity)));
        raw.put("__taskId", String.valueOf(activity.getTaskId()));
    }

    private static void hookLoader(XposedInterface xposed, Method method, String process,
                                   EventSink sink, LsposedBackend.Reporter reporter) {
        // loadDex/create/findClass 的返回值由专用 hook 处理，不能在普通 Observation 中二次调用原方法。
        if (!INSTALLED.add(method)) return;
        try {
            xposed.hook(method).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    if (!INSTALLING.get()) {
                        Class<?> actual = result instanceof Class<?> ? (Class<?>) result
                                : result == null ? null : result.getClass();
                        if (actual != null) {
                            for (String target : HookTargets.RUNTIME_CLASSES) {
                                if (actual.getName().equals(target)) installClass(xposed, actual, process, sink, reporter);
                            }
                        }
                        Object dynamicLoader = Reflect.get(chain.getThisObject(), "mNewClassLoader");
                        if (dynamicLoader instanceof ClassLoader) {
                            installLoader(xposed, (ClassLoader) dynamicLoader, process, sink, reporter);
                        }
                    }
                } catch (Throwable t) {
                    warn(reporter, "[MINI-PROBE] dynamic install failed", t);
                }
                return result;
            });
            info(reporter, "[MINI-PROBE] installed loader observer " + method.toGenericString());
        } catch (Throwable t) {
            INSTALLED.remove(method);
            warn(reporter, "[MINI-PROBE] loader observer failed " + method.getName(), t);
        }
    }
}
