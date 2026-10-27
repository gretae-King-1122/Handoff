package com.handoff.hook;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedInterface;

/**
 * M2.0 探针：用户说得对 —— QQ 打开小程序卡片时必然知道 appId，只是不在 P0 的
 * {@code LaunchParam} 里。这里把 {@code MiniAppLauncher} / {@code MiniAppController}
 * 的**所有带参方法**都挂上，点击时把调用序列与实参打出来，appId 出现的那一跳就藏不住。
 */
final class MiniSdkSweep {

    private static final String[] CLASSES = {
            "com.tencent.mobileqq.mini.sdk.MiniAppLauncher",
            "com.tencent.mobileqq.mini.sdk.MiniAppController",
    };

    private MiniSdkSweep() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        int hooked = 0;
        for (String name : CLASSES) {
            Class<?> cls;
            try {
                cls = Class.forName(name, false, cl);
            } catch (Throwable t) {
                reporter.warn("SWEEP 类缺失: " + name, null);
                continue;
            }
            for (Method m : cls.getDeclaredMethods()) {
                if (m.isSynthetic() || Modifier.isAbstract(m.getModifiers())) continue;
                if (m.getParameterCount() == 0) continue;
                if (hook(xposed, m, reporter)) hooked++;
            }
            reporter.info("SWEEP " + cls.getSimpleName() + " done");
        }
        reporter.info("MiniSdkSweep hooked " + hooked);
    }

    private static boolean hook(XposedInterface xposed, Method method, LsposedBackend.Reporter reporter) {
        try {
            xposed.hook(method)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            String args = args(chain);
                            // 只打「可能是 appId / 链接」的调用，减少噪音
                            if (interesting(args)) {
                                StringBuilder sb = new StringBuilder(640);
                                sb.append("SWEEP ").append(method.getDeclaringClass().getSimpleName())
                                        .append('#').append(method.getName()).append(args);
                                if (method.getName().startsWith("is")) {
                                    sb.append(" → ").append(result);
                                    sb.append("\n    STACK ").append(stack());
                                }
                                reporter.info(sb.toString());
                            }
                        } catch (Throwable ignored) {
                            // 探针失败不影响 QQ
                        }
                        return result;
                    });
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String args(XposedInterface.Chain chain) {
        StringBuilder sb = new StringBuilder(400);
        sb.append('(');
        int i = 0;
        for (Object a : chain.getArgs()) {
            if (i++ > 0) sb.append(", ");
            if (a == null) {
                sb.append("null");
            } else if (a instanceof String) {
                String s = (String) a;
                sb.append('"').append(s.length() > 200 ? s.substring(0, 200) + "…" : s).append('"');
            } else if (a instanceof Integer || a instanceof Long || a instanceof Boolean) {
                sb.append(a);
            } else {
                sb.append('<').append(a.getClass().getSimpleName()).append('>');
            }
        }
        return sb.append(')').toString();
    }

    private static boolean interesting(String args) {
        if (args.contains("http") || args.contains("m.q.qq.com")) return true;
        // appId 是 8~12 位数字（如 1109937557）
        return args.matches(".*\"1\\d{7,11}\".*");
    }

    /** QQ 侧调用栈，最多 10 层，用来找「谁决定这是不是小程序链接」。 */
    private static String stack() {
        StringBuilder sb = new StringBuilder(400);
        int n = 0;
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String cls = e.getClassName();
            if (cls.startsWith("com.handoff.")) continue;
            if (!cls.startsWith("com.tencent.")) continue;
            if (n > 0) sb.append(" < ");
            sb.append(cls).append('#').append(e.getMethodName());
            if (++n >= 10) break;
        }
        return sb.toString();
    }
}
