package com.handoff.api

/** 规则引擎输出的决策。 */
sealed interface Decision {
    /** 放行原小程序。 */
    data object Passthrough : Decision

    /** 交给执行层按 [targets] 顺序尝试。 */
    data class Redirect(
        val ruleId: String,
        val vars: Map<String, String>,
        val targets: List<TargetSpec>,
    ) : Decision
}
