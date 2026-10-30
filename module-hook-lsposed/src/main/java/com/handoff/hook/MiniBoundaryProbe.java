package com.handoff.hook;

import android.app.Application;
import android.app.Instrumentation;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Opt-in entry breadcrumbs only; never read link arguments or change a launch result. */
final class MiniBoundaryProbe {
    private static final String[][] TARGETS = {
            {"com.tencent.mobileqq.mini.api.impl.MiniAppServiceImpl", "launchMiniApp"},
            {"com.tencent.mobileqq.mini.sdk.MiniAppLauncher", "startMiniApp", "openMiniApp"},
            {"com.tencent.mobileqq.mini.sdk.MiniAppController", "startAppByLink", "startApp"}
    };

    private MiniBoundaryProbe() { }

    static void install(XposedInterface xposed, ClassLoader loader, LsposedBackend.Reporter reporter) {
        observeRuntimeLoader(xposed, loader, reporter);
        for (String[] target : TARGETS) {
            try {
                Class<?> cls = Class.forName(target[0], false, loader);
                int count = 0;
                for (Method method : cls.getDeclaredMethods()) {
                    if (method.isSynthetic() || !matches(target, method.getName())) continue;
                    xposed.hook(method).intercept(chain -> {
                        log(reporter, "entry " + method);
                        // Keep original invocation outside diagnostic exception handling: exactly once.
                        return chain.proceed();
                    });
                    count++;
                }
                log(reporter, "installed class=" + target[0] + " count=" + count);
            } catch (Throwable error) {
                log(reporter, "unavailable class=" + target[0] + " error=" + error.getClass().getName());
            }
        }
    }

    private static void observeRuntimeLoader(XposedInterface xposed, ClassLoader initial,
                                             LsposedBackend.Reporter reporter) {
        try {
            Method method = Instrumentation.class.getDeclaredMethod("callApplicationOnCreate", Application.class);
            xposed.hook(method).intercept(chain -> {
                try {
                    Application app = (Application) chain.getArgs().get(0);
                    ClassLoader current = app.getClassLoader();
                    log(reporter, "runtime-loader initial=" + loaderId(initial) + " current=" + loaderId(current)
                            + " same=" + (initial == current));
                    for (String name : new String[]{HookTargets.BROWSER_DISPATCH_CLASS,
                            HookTargets.MINI_LAUNCH_CLASS, TARGETS[0][0]}) {
                        try {
                            Class<?> before = Class.forName(name, false, initial);
                            Class<?> after = Class.forName(name, false, current);
                            log(reporter, "runtime-class name=" + name + " same=" + (before == after)
                                    + " initial=" + loaderId(before.getClassLoader())
                                    + " current=" + loaderId(after.getClassLoader()));
                        } catch (Throwable error) {
                            log(reporter, "runtime-class unavailable name=" + name
                                    + " error=" + error.getClass().getName());
                        }
                    }
                } catch (Throwable error) {
                    log(reporter, "runtime-loader unavailable error=" + error.getClass().getName());
                }
                return chain.proceed();
            });
            log(reporter, "runtime-loader observation installed initial=" + loaderId(initial));
        } catch (Throwable error) {
            log(reporter, "runtime-loader observation unavailable error=" + error.getClass().getName());
        }
    }

    private static String loaderId(ClassLoader loader) {
        return loader == null ? "bootstrap" : loader.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(loader));
    }

    private static boolean matches(String[] target, String name) {
        for (int i = 1; i < target.length; i++) if (target[i].equals(name)) return true;
        return false;
    }

    private static void log(LsposedBackend.Reporter reporter, String message) {
        try { reporter.info("[DEBUG-mini-boundary] " + message); } catch (Throwable ignored) { }
    }
}
