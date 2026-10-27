package com.handoff.hook;

import com.handoff.api.EventSink;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * M1.2 探针：Ark 卡片点击路径（方案 B 的可行性验证）。
 *
 * <p>挂 {@code MessageForArkApp} 的三个 click 方法，命中时把 {@code this.ark_app_message}
 * 的全部字段（appName / meta / appView …）与实参一起打出来。**只观测，不改行为。**
 */
final class ArkClickProbe {

    private static final String MESSAGE_CLASS = "com.tencent.mobileqq.data.MessageForArkApp";
    private static final String[] CLICK_METHODS = {"clickAppMsg", "clickWebMsg", "click2YYB"};

    private ArkClickProbe() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter)
            throws ClassNotFoundException {
        Class<?> cls = Class.forName(MESSAGE_CLASS, false, cl);
        int hooked = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!isClickMethod(m.getName())) continue;
            hookOne(xposed, m, reporter);
            hooked++;
        }
        if (hooked == 0) {
            throw new IllegalStateException("MessageForArkApp 未找到 click* 方法");
        }
        reporter.info("ArkClickProbe hooked " + hooked + " method(s)");
    }

    private static boolean isClickMethod(String name) {
        for (String n : CLICK_METHODS) {
            if (n.equals(name)) return true;
        }
        return false;
    }

    private static void hookOne(XposedInterface xposed, Method method, LsposedBackend.Reporter reporter) {
        try {
            xposed.hook(method)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            StringBuilder sb = new StringBuilder(768);
                            sb.append("ARK_CLICK ").append(method.getName()).append('(');
                            Object[] args = chain.getArgs().toArray();
                            for (int i = 0; i < args.length; i++) {
                                if (i > 0) sb.append(", ");
                                Object a = args[i];
                                if (a instanceof String) {
                                    String s = (String) a;
                                    sb.append('"').append(s.length() > 300 ? s.substring(0, 300) + "…" : s).append('"');
                                } else {
                                    sb.append(a == null ? "null" : a.getClass().getSimpleName());
                                }
                            }
                            sb.append(')');
                            Object ark = Reflect.get(chain.getThisObject(), "ark_app_message");
                            if (ark != null) {
                                sb.append(" | ark=").append(Dump.describe(ark, 1));
                            }
                            reporter.info(sb.toString());
                        } catch (Throwable ignored) {
                            // 探针失败不影响 QQ
                        }
                        return result;
                    });
        } catch (Throwable t) {
            reporter.warn("ArkClickProbe hook " + method.getName() + " failed", t);
        }
    }
}
