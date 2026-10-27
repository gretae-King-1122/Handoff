package com.handoff.hook;

import android.app.Activity;

import com.handoff.api.EventSink;
import com.handoff.api.MiniAppEvent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * P2 兜底：{@code AppBrandUI#onCreate(Bundle)}。
 *
 * <p>父类 {@code onCreate} 不能跳过 → 必须先 {@code proceed()}，再决定是否 {@code finish()}
 * （允许一次闪屏）。
 */
final class ContainerHook {

    private ContainerHook() {
    }

    static void install(XposedInterface xposed, ClassLoader cl, String processName, EventSink sink)
            throws ClassNotFoundException {
        Class<?> cls = Class.forName(HookTargets.P2_CLASS, false, cl);
        Method method = Reflect.method(cls, HookTargets.P2_METHOD, 1);
        if (method == null) {
            throw new IllegalStateException("P2 未命中 onCreate(Bundle)");
        }

        xposed.hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        Object self = chain.getThisObject();
                        if (self instanceof Activity) {
                            Activity activity = (Activity) self;
                            MiniAppEvent event = EventBuilder.fromContainer(activity.getIntent(), processName);
                            // 补充：Activity 类名，用来确认这类卡片落在哪个页面
                            java.util.Map<String, String> extras = new java.util.HashMap<>(event.getRawExtras());
                            extras.put("__activity", activity.getClass().getName());
                            event = event.copy(
                                    event.getSource(), event.getProcessName(), event.getMiniAppId(), event.getName(),
                                    event.getEntryPath(), event.getFakeUrl(), event.getLink(), event.getLinkType(),
                                    event.getScene(), event.getVia(), event.getArkApp(), event.getMeta(), extras);
                            if (sink.onEvent(event)) {
                                activity.finish();
                            }
                        }
                    } catch (Throwable ignored) {
                        // 放行：容器已经起来了，最多闪一下
                    }
                    return result;
                });
    }
}
