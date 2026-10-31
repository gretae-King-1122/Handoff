package com.handoff.ui;

/** Never infer activation from a version profile, a historical event, or just a service connection. */
record ActivationStatus(String title, String detail, boolean active) {
    static ActivationStatus resolve(boolean checked, FrameworkSnapshot framework) {
        if (!checked) return new ActivationStatus("正在检查激活状态", "正在读取框架连接、QQ 作用域和运行状态。", false);
        if (!framework.connected()) return new ActivationStatus("未确认激活", "未连接兼容的 LSPosed 框架。请在框架管理器启用 Handoff；未连接不等于没有 root。", false);
        if (!framework.scopeKnown()) return new ActivationStatus("激活状态未知", "框架已连接，但无法读取 QQ 作用域。请在框架管理器检查。", false);
        if (!framework.scoped()) return new ActivationStatus("QQ 作用域未授权", "框架已连接，请为 Handoff 勾选 QQ，并强停重开 QQ。", false);
        return switch (framework.runtime()) {
            case CURRENT -> new ActivationStatus("已在 QQ 激活", "框架确认 QQ 主进程已加载当前模块版本。实际入口命中和视频交接请查看诊断。", true);
            case NOT_RUNNING -> new ActivationStatus("等待 QQ 启动", "QQ 作用域已授权，框架暂未报告运行中的 QQ 主进程。打开 QQ 后返回刷新。", false);
            case OUTDATED -> new ActivationStatus("需要重启 QQ", "QQ 主进程的模块版本或加载状态不是最新。请强停重开 QQ 后刷新。", false);
            case RELOADING -> new ActivationStatus("模块正在重新加载", "框架报告 QQ 主进程正在加载模块，请稍后刷新。", false);
            case UNKNOWN -> new ActivationStatus("激活状态未知", "框架已连接、QQ 作用域已授权，但运行状态不可读；不会据此判为已激活。", false);
        };
    }
}
