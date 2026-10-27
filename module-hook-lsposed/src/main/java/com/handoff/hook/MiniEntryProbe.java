package com.handoff.hook;

import android.content.Intent;
import android.os.Bundle;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * M1.4 探针：小程序启动链的入口参数普查。
 *
 * <p>调用栈已经证明点击链路是：
 * {@code MiniAppServiceImpl#launchMiniApp → startMiniApp → MiniAppLauncher#startMiniApp
 * → launchAppByFakeLink → MiniAppController#startAppByLink}。
 * 这里把前三跳的实参打出来，确认 appId / 卡片 JSON 是否在其中某一跳出现过。
 */
final class MiniEntryProbe {

    private static final String[][] TARGETS = {
            {"com.tencent.mobileqq.mini.api.impl.MiniAppServiceImpl", "launchMiniApp"},
            {"com.tencent.mobileqq.mini.sdk.MiniAppLauncher", "startMiniApp"},
            {"com.tencent.mobileqq.mini.sdk.MiniAppLauncher", "launchAppByFakeLink"},
    };

    private MiniEntryProbe() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, LsposedBackend.Reporter reporter) {
        int hooked = 0;
        for (String[] target : TARGETS) {
            Class<?> cls;
            try {
                cls = Class.forName(target[0], false, cl);
            } catch (Throwable t) {
                reporter.warn("probe class missing: " + target[0], t);
                continue;
            }
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals(target[1])) continue;
                if (hookOne(xposed, m, reporter)) hooked++;
            }
        }
        reporter.info("MiniEntryProbe hooked " + hooked + " method(s)");
    }

    private static boolean hookOne(XposedInterface xposed, Method method, LsposedBackend.Reporter reporter) {
        try {
            xposed.hook(method)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            reporter.info("MINI_ENTRY " + method.getDeclaringClass().getSimpleName()
                                    + "#" + method.getName() + args(chain));
                        } catch (Throwable ignored) {
                            // 探针失败不影响 QQ
                        }
                        return result;
                    });
            return true;
        } catch (Throwable t) {
            reporter.warn("MiniEntryProbe hook failed: " + method, t);
            return false;
        }
    }

    private static String args(XposedInterface.Chain chain) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('(');
        int i = 0;
        for (Object a : chain.getArgs()) {
            if (i++ > 0) sb.append(", ");
            if (a == null) {
                sb.append("null");
            } else if (a instanceof String) {
                String s = (String) a;
                sb.append('"').append(s.length() > 300 ? s.substring(0, 300) + "…" : s).append('"');
            } else if (a instanceof Integer || a instanceof Long || a instanceof Boolean) {
                sb.append(a);
            } else if (a instanceof Intent) {
                Intent intent = (Intent) a;
                sb.append("Intent{").append(intent.getComponent() == null ? "-" : intent.getComponent().getClassName())
                        .append(" data=").append(intent.getDataString()).append(' ').append(bundle(intent.getExtras())).append('}');
            } else {
                sb.append('<').append(a.getClass().getSimpleName()).append('>');
            }
        }
        return sb.append(')').toString();
    }

    private static String bundle(Bundle b) {
        if (b == null) return "extras=null";
        StringBuilder sb = new StringBuilder("extras{");
        int n = 0;
        for (String k : b.keySet()) {
            if (n++ > 0) sb.append(", ");
            Object v = b.get(k);
            sb.append(k).append('=');
            if (v instanceof String) {
                String s = (String) v;
                sb.append(s.length() > 120 ? s.substring(0, 120) + "…" : s);
            } else if (v == null) {
                sb.append("null");
            } else {
                sb.append('<').append(v.getClass().getSimpleName()).append('>');
            }
            if (n >= 15) {
                sb.append(", …");
                break;
            }
        }
        return sb.append('}').toString();
    }
}
