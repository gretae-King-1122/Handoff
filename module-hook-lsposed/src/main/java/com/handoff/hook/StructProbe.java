package com.handoff.hook;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * M1.8 探针：猜错了两次之后，转向 {@code MessageForStructing}（结构化消息）。
 *
 * <p>小程序分享卡片在 QQ 里通常是 struct msg（XML 里带 brief/url/appid），
 * 这条探针会把解析结果与关键类的签名打出来。
 */
final class StructProbe {

    private static final String[] CLASSES = {
            "com.tencent.mobileqq.data.MessageForStructing",
            "com.tencent.mobileqq.structmsg.AbsStructMsg",
            "com.tencent.mobileqq.structmsg.AbsStructMsgItem",
    };

    private StructProbe() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        for (String name : CLASSES) {
            dumpSignatures(cl, name, reporter);
        }
        hookParse(xposed, cl, reporter);
        reporter.info("StructProbe installed");
    }

    private static void hookParse(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        try {
            Class<?> cls = Class.forName("com.tencent.mobileqq.data.MessageForStructing", false, cl);
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals("doParse") || m.getParameterCount() != 0) continue;
                xposed.hook(m)
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object struct = Reflect.get(chain.getThisObject(), "mStructMsg");
                                if (struct != null) {
                                    reporter.info("STRUCT doParse | " + truncate(Dump.describe(struct, 2), 1200));
                                }
                            } catch (Throwable ignored) {
                                // 探针失败不影响 QQ
                            }
                            return result;
                        });
                reporter.info("STRUCT hooked MessageForStructing#doParse");
            }
        } catch (Throwable t) {
            reporter.warn("STRUCT 挂载失败", t);
        }
    }

    private static void dumpSignatures(ClassLoader cl, String className, LsposedBackend.Reporter reporter) {
        try {
            Class<?> c = Class.forName(className, false, cl);
            int n = 0;
            for (Method m : c.getDeclaredMethods()) {
                if (++n > 25) break;
                reporter.info("SIG " + c.getSimpleName() + "#" + m.getName() + "/" + m.getParameterCount()
                        + " : " + m.getReturnType().getSimpleName());
            }
        } catch (Throwable t) {
            reporter.warn("SIG 类缺失: " + className, null);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
