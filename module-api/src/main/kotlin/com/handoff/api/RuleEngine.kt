package com.handoff.api

/** 规则引擎：纯计算，无 IO、无 Android。实现位于 `:module-rules`。 */
interface RuleEngine {
    /** 载入规则 JSON 原文；解析失败时抛 [IllegalArgumentException]（调用方负责放行）。 */
    fun load(rulesJson: String)

    fun decide(event: MiniAppEvent, config: Config): Decision
}
