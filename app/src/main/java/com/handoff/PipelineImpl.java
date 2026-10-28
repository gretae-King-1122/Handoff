package com.handoff;

import android.util.Log;

import com.handoff.api.MiniAppEvent;
import com.handoff.hook.LsposedBackend;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

/**
 * 小程序诊断事件的日志接收端，永不阻断。
 *
 * <p>仅显式启用诊断时格式化字段快照；正常交接只输出短安装/命中摘要。
 * 规则匹配与执行在 M2 接入 `:module-rules` / `:module-executor` 后填进来。
 */
final class PipelineImpl {

    private static final String TAG = "Handoff";

    private final XposedInterface xposed;
    private final boolean diagnostics;
    // 启动回调和页面通知可能来自不同线程，SimpleDateFormat 本身不线程安全。
    private final ThreadLocal<SimpleDateFormat> clock = ThreadLocal.withInitial(
            () -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));

    PipelineImpl(XposedInterface xposed, boolean diagnostics) {
        this.xposed = xposed;
        this.diagnostics = diagnostics;
    }

    /** @return M1 恒为 false —— 永不阻断，绝不改变 QQ 行为。 */
    boolean handle(MiniAppEvent event) {
        try {
            if (diagnostics) {
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
                // Normal install / redirect summaries stay available for device verification.
                xposed.log(Log.INFO, TAG, message);
            }

            @Override
            public void warn(String message, Throwable cause) {
                xposed.log(Log.WARN, TAG, message, cause);
            }
        };
    }

    /**
     * 诊断事件的单行格式：
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
