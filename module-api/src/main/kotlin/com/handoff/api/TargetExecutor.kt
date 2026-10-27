package com.handoff.api

/** 执行层：按 [Decision.Redirect.targets] 顺序尝试，首个成功即停。实现位于 `:module-executor`。 */
interface TargetExecutor {
    fun run(decision: Decision.Redirect): ExecResult
}
