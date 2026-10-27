package com.handoff.hook;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * M1.7 探针：QQ 9.3.75 的 AIO 是新的模板消息架构（freesia），Ark 卡片大概率走
 * {@code TemplateMsgMiniAppLinkConfig} 这条「小程序链接模板」。
 *
 * <p>挂它的 parser：任何入参里带 http 的解析都会打印入参原文与解析结果。
 */
final class TemplateProbe {

    private static final String CONFIG = "com.tencent.mobileqq.aio.msglist.holder.component.template.freesia.TemplateMsgMiniAppLinkConfig";
    private static final String PARSER = "com.tencent.mobileqq.aio.msglist.holder.component.template.freesia.TemplateMsgMiniAppLinkConfigParser";

    private TemplateProbe() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        try {
            Class<?> cfg = Class.forName(CONFIG, false, cl);
            int n = 0;
            for (Method m : cfg.getDeclaredMethods()) {
                reporter.info("SIG " + cfg.getSimpleName() + "#" + m.getName() + "() : " + m.getReturnType().getSimpleName());
                if (++n >= 20) break;
            }
        } catch (Throwable t) {
            reporter.warn("TEMPLATE 配置类缺失", t);
        }

        try {
            Class<?> parser = Class.forName(PARSER, false, cl);
            int hooked = 0;
            for (Method m : parser.getDeclaredMethods()) {
                if (m.getParameterCount() == 0) continue;
                try {
                    xposed.hook(m)
                            .setPriority(XposedInterface.PRIORITY_HIGHEST)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                try {
                                    String text = join(chain.getArgs().toArray());
                                    if (looksInteresting(text)) {
                                        reporter.info("TEMPLATE " + m.getName() + "(" + truncate(text, 1200) + ") → "
                                                + truncate(Dump.describe(result, 1), 800));
                                    }
                                } catch (Throwable ignored) {
                                    // 探针失败不影响 QQ
                                }
                                return result;
                            });
                    hooked++;
                } catch (Throwable t) {
                    reporter.warn("TEMPLATE hook 失败: " + m.getName(), t);
                }
            }
            reporter.info("TemplateProbe hooked " + hooked + " method(s)");
        } catch (Throwable t) {
            reporter.warn("TEMPLATE parser 类缺失", t);
        }
    }

    private static String join(Object[] args) {
        StringBuilder sb = new StringBuilder(512);
        for (Object a : args) {
            if (a == null) {
                sb.append("null");
            } else if (a instanceof String) {
                sb.append((String) a);
            } else {
                sb.append('<').append(a.getClass().getSimpleName()).append('>');
            }
            sb.append(" | ");
        }
        return sb.toString();
    }

    private static boolean looksInteresting(String text) {
        return text.contains("http") || text.contains("appid") || text.contains("miniapp") || text.contains("app_id");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
