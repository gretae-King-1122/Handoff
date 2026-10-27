package com.handoff.api

/** 一条命中记录。字段顺序与日志行一致，便于人工核对与 `grep`。 */
data class LogEntry(
    val timestamp: Long,
    val processName: String,
    val source: Source,
    val miniAppId: String?,
    val name: String?,
    val scene: Int?,
    val entryPath: String?,
    val ruleId: String?,
    val vars: Map<String, String>,
    val targetType: String?,
    val result: String,
)
