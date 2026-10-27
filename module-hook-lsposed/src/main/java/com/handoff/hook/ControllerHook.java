package com.handoff.hook;

import com.handoff.api.EventSink;
import com.handoff.api.MiniAppEvent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * P0 主攻：{@code MiniAppController#startAppByLink(Context, String, int, LaunchParam, Listener)}。
 *
 * <p>唯一能同时拿到 {@code link} / {@code linkType} / 完整 {@code LaunchParam} 的点。
 */
final class ControllerHook {

    private ControllerHook() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, String processName, EventSink sink,
                        LsposedBackend.Reporter reporter) throws ClassNotFoundException {
        Method method = null;
        for (HookTargets.Candidate c : HookTargets.P0) {
            try {
                Class<?> cls = Class.forName(c.className, false, cl);
                Method m = Reflect.method(cls, c.methodName, c.paramCount);
                if (m != null) {
                    method = m;
                    break;
                }
            } catch (Throwable ignored) {
                // 该候选不可用，试下一个
            }
        }
        if (method == null) {
            throw new IllegalStateException("P0 未命中任何候选");
        }

        final Method target = method;
        final boolean probeStack = !processName.contains(":");
        xposed.hook(target)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    try {
                        MiniAppEvent event = EventBuilder.fromController(chain, processName);
                        if (probeStack) {
                            // M1.3 探针：打出 QQ 侧的调用栈，找出 Ark 卡片点击的真实入口
                            reporter.info("STACK " + stack());
                        }
                        if (sink.onEvent(event)) {
                            // void 方法：不调 proceed() 即等于不执行原方法体；返回值被框架忽略，返回 null
                            return null;
                        }
                    } catch (Throwable ignored) {
                        // 提取失败 → 放行
                    }
                    return chain.proceed();
                });
    }

    /** 只保留 QQ 侧的栈帧，最多 12 层，够看清调用链。 */
    private static String stack() {
        StringBuilder sb = new StringBuilder(512);
        int n = 0;
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String cls = e.getClassName();
            if (cls.startsWith("com.handoff.")) continue;
            if (!cls.startsWith("com.tencent.")) continue;
            if (n++ > 0) sb.append(" < ");
            sb.append(cls).append('#').append(e.getMethodName());
            if (n >= 12) break;
        }
        return sb.toString();
    }
}
