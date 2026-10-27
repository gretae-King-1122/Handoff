package com.handoff.api

/** 执行层的一档动作，对应规则 JSON 里 `targets[]` 的 `type`。 */
sealed interface Target {
    /** `ACTION_VIEW` + https，**不指定 package**，交给系统 App Links。 */
    data class Http(val tpl: String) : Target

    /** `ACTION_VIEW` + 自定义 scheme + `setPackage(pkg)`。 */
    data class Deeplink(val pkg: String, val tpl: String) : Target

    /** 显式 `ACTION_MAIN` + `CATEGORY_LAUNCHER` + `setPackage(pkg)`，直接 startActivity。 */
    data class Package(val pkg: String) : Target

    /** 不动作、恒失败，用于触发放行。 */
    data object Passthrough : Target
}

/**
 * 降级链的一项。
 *
 * `whenVar` 对应 JSON 的 `when`（Kotlin 里 `when` 是关键字）：非 null 时，该变量不存在就跳过本项。
 */
data class TargetSpec(
    val whenVar: String?,
    val action: Target,
)

/** 执行结果。 */
data class ExecResult(
    val success: Boolean,
    val type: String? = null,
    val detail: String? = null,
)
