package com.handoff.hook;

import com.handoff.api.EventSink;
import com.handoff.api.MiniAppEvent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * P1 补充：{@code MiniAppLauncher#openMiniApp(Context, LaunchParam, Listener)} → boolean。
 *
 * <p>私有收口方法，兜 P0 漏网。
 */
final class LauncherHook {

    private LauncherHook() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, String processName, EventSink sink)
            throws ClassNotFoundException {
        Method method = null;
        for (HookTargets.Candidate c : HookTargets.P1) {
            try {
                Class<?> cls = Class.forName(c.className, false, cl);
                Method m = Reflect.method(cls, c.methodName, c.paramCount);
                if (m != null) {
                    method = m;
                    break;
                }
            } catch (Throwable ignored) {
                // 试下一个候选
            }
        }
        if (method == null) {
            throw new IllegalStateException("P1 未命中任何候选");
        }

        final Method target = method;
        xposed.hook(target)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    try {
                        MiniAppEvent event = EventBuilder.fromLauncher(chain, processName);
                        if (sink.onEvent(event)) {
                            // boolean 方法：返回 false 让 QQ 走自己的失败分支
                            return Boolean.FALSE;
                        }
                    } catch (Throwable ignored) {
                        // 放行
                    }
                    return chain.proceed();
                });
    }
}
