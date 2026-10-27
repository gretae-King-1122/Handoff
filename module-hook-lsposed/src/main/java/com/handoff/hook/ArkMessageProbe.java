package com.handoff.hook;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * M1.5 探针：直接在 Ark 卡片消息对象上取数据。
 *
 * <p>入口实参已经证明 {@code launchMiniApp(ctx, url, 1036, null)} 里只有短链，
 * 所以卡片 JSON 只可能还在消息对象上。这里挂三个点，确认哪个是「点击」：
 * <ul>
 *   <li>{@code doParse()} —— 消息解析，确认 {@code getJumpUrl()} 与 meta 的对应关系</li>
 *   <li>{@code doOnEvent(int)} —— Ark 事件回调，最可能是点击</li>
 *   <li>{@code openThirdApp(QQAppInterface, Context)} —— 名字最像「打开第三方 App」</li>
 * </ul>
 */
final class ArkMessageProbe {

    private static final String CLASS = "com.tencent.mobileqq.data.MessageForArkApp";

    private ArkMessageProbe() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        Class<?> cls;
        try {
            cls = Class.forName(CLASS, false, cl);
        } catch (Throwable t) {
            reporter.warn("ArkMessageProbe 类缺失", t);
            return;
        }

        Method getJumpUrl = Reflect.method(cls, "getJumpUrl", 0);
        hook(xposed, cls, "doParse", 0, getJumpUrl, reporter);
        hook(xposed, cls, "doOnEvent", 1, getJumpUrl, reporter);
        hook(xposed, cls, "openThirdApp", 2, getJumpUrl, reporter);
        // getJumpUrl 很可能是「点击时把卡片换成 URL」的那一跳，命中即为关联键
        hook(xposed, cls, "getJumpUrl", 0, getJumpUrl, reporter);

        // 顺手把两个 Ark UI 类的签名打进日志，省得再来回扒 dex
        dumpSignatures(cl, "com.tencent.mobileqq.activity.aio.item.ArkAppContainer", reporter);
        dumpSignatures(cl, "com.tencent.mobileqq.ark.i", reporter);
        hookCardEvent(xposed, cl, reporter);
        reporter.info("ArkMessageProbe installed");
    }

    /**
     * M1.6：{@code ArkAppContainer#doOpenCardViewEvent(String, String)} —— 从签名看就是
     * 「卡片被点了，把 view + meta 交给上层」的回调，最可能的点击入口。
     */
    private static void hookCardEvent(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        final String container = "com.tencent.mobileqq.activity.aio.item.ArkAppContainer";
        try {
            Class<?> c = Class.forName(container, false, cl);
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("doOpenCardViewEvent")) continue;
                xposed.hook(m)
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                StringBuilder sb = new StringBuilder("ARK_CARD doOpenCardViewEvent");
                                for (Object a : chain.getArgs()) {
                                    sb.append(" | ").append(a instanceof String ? truncate((String) a, 1500) : String.valueOf(a));
                                }
                                reporter.info(sb.toString());
                            } catch (Throwable ignored) {
                                // 探针失败不影响 QQ
                            }
                            return result;
                        });
                reporter.info("ARK_CARD hooked doOpenCardViewEvent");
            }
        } catch (Throwable t) {
            reporter.warn("ARK_CARD 挂载失败", t);
        }
    }

    /** 打印一个类的全部方法签名（只打 public/protected，最多 40 条）。 */
    private static void dumpSignatures(ClassLoader cl, String className, LsposedBackend.Reporter reporter) {
        try {
            Class<?> c = Class.forName(className, false, cl);
            int n = 0;
            for (Method m : c.getDeclaredMethods()) {
                if (++n > 40) break;
                StringBuilder sb = new StringBuilder("SIG ").append(c.getSimpleName()).append('#').append(m.getName()).append('(');
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(ps[i].getSimpleName());
                }
                sb.append(") : ").append(m.getReturnType().getSimpleName());
                reporter.info(sb.toString());
            }
        } catch (Throwable t) {
            reporter.warn("SIG 类缺失: " + className, null);
        }
    }

    private static void hook(XposedInterface xposed, Class<?> cls, String name, int paramCount,
                             Method getJumpUrl, LsposedBackend.Reporter reporter) {
        Method m = Reflect.method(cls, name, paramCount);
        if (m == null) {
            reporter.warn("ArkMessageProbe 未找到 " + name, null);
            return;
        }
        try {
            xposed.hook(m)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object self = chain.getThisObject();
                            StringBuilder sb = new StringBuilder(768);
                            sb.append("ARK_MSG ").append(name);
                            for (Object a : chain.getArgs()) {
                                sb.append(' ').append(a instanceof String
                                        ? '"' + truncate((String) a, 120) + '"'
                                        : String.valueOf(a));
                            }
                            if (getJumpUrl != null && self != null) {
                                try {
                                    Object url = getJumpUrl.invoke(self);
                                    sb.append(" | jumpUrl=").append(url);
                                } catch (Throwable ignored) {
                                    // 有的实例没有 meta
                                }
                            }
                            Object ark = Reflect.get(self, "ark_app_message");
                            if (ark != null) {
                                sb.append(" | appName=").append(Reflect.str(ark, "appName"));
                                sb.append(" | appView=").append(Reflect.str(ark, "appView"));
                                sb.append(" | meta=").append(truncate(Reflect.str(ark, "meta"), 400));
                            }
                            reporter.info(sb.toString());
                        } catch (Throwable ignored) {
                            // 探针失败不影响 QQ
                        }
                        return result;
                    });
        } catch (Throwable t) {
            reporter.warn("ArkMessageProbe hook 失败: " + name, t);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
