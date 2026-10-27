package com.handoff;

import android.util.Log;

import com.handoff.api.Config;
import com.handoff.api.MiniAppEvent;
import com.handoff.hook.LsposedBackend;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

/**
 * Pipeline 的唯一实现（M1 探针版）。
 *
 * <p>M1 的职责只有一条：**把每一次小程序启动完整写进 Xposed 日志**，然后一律放行。
 * 规则匹配与执行在 M2 接入 `:module-rules` / `:module-executor` 后填进来。
 */
final class PipelineImpl {

    private static final String TAG = "Handoff";

    private final XposedInterface xposed;
    private final String processName;
    private final Config config = Config.M1_PROBE;
    // 启动回调和页面通知可能来自不同线程，SimpleDateFormat 本身不线程安全。
    private final ThreadLocal<SimpleDateFormat> clock = ThreadLocal.withInitial(
            () -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));

    PipelineImpl(XposedInterface xposed, String processName) {
        this.xposed = xposed;
        this.processName = processName;
    }

    /** @return M1 恒为 false —— 永不阻断，绝不改变 QQ 行为。 */
    boolean handle(MiniAppEvent event) {
        try {
            if (config.getDebug()) {
                xposed.log(Log.INFO, TAG, format(event));
            }
        } catch (Throwable ignored) {
            // 日志失败也不能影响 QQ
        }
        return false;
    }

    LsposedBackend.Reporter reporter() {
        return new LsposedBackend.Reporter() {
            @Override
            public void info(String message) {
                if (config.getDebug()) xposed.log(Log.INFO, TAG, message);
            }

            @Override
            public void warn(String message, Throwable cause) {
                xposed.log(Log.WARN, TAG, message, cause);
            }
        };
    }

    /**
     * 单行格式，字段顺序与 docs/TESTING.md / ARCHITECTURE §7 一致：
     * 时间 | 进程 | source | miniAppId | name | scene | entryPath | fakeUrl | via | link | linkType | arkApp | meta | extras
     */
    private String format(MiniAppEvent e) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(clock.get().format(new Date()))
                .append(" | ").append(orDash(e.getProcessName()))
                .append(" | ").append(e.getSource())
                .append(" | ").append(orDash(e.getMiniAppId()))
                .append(" | ").append(orDash(e.getName()))
                .append(" | ").append(e.getScene() == null ? "-" : String.valueOf(e.getScene()))
                .append(" | ").append(orDash(e.getEntryPath()))
                .append(" | ").append(orDash(e.getFakeUrl()))
                .append(" | ").append(orDash(e.getVia()))
                .append(" | ").append(orDash(e.getLink()))
                .append(" | ").append(e.getLinkType() == null ? "-" : String.valueOf(e.getLinkType()))
                .append(" | ").append(orDash(e.getArkApp()))
                .append(" | ").append(orDash(e.getMeta()));
        Map<String, String> extras = e.getRawExtras();
        if (extras != null && !extras.isEmpty()) {
            sb.append(" | ").append(extras);
        }
        return sb.toString();
    }

    private static String orDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }
}
