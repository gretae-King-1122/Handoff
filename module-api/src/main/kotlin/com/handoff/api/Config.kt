package com.handoff.api

/**
 * 配置。
 *
 * 规则以 JSON 原文存放、由 `:module-rules` 解析 —— 这样 `:module-api` 保持零依赖，
 * 也便于设置页整体导入导出。
 */
data class Config(
    val enabled: Boolean = true,
    val debug: Boolean = false,
    val rulesJson: String = "[]",
    val version: Int = 1,
) {
    companion object {
        /** M1 探针版：开关打开、调试日志打开、无规则。 */
        @JvmField
        val M1_PROBE = Config(enabled = true, debug = true, rulesJson = "[]", version = 1)
    }
}
