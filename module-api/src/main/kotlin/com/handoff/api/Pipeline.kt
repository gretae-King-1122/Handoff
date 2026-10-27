package com.handoff.api

/**
 * 从 hook 层到规则 / 执行层的唯一出口。
 *
 * @return `true` = 已接管，hook 应阻断原方法；`false` = 放行（`chain.proceed()`）。
 */
fun interface Pipeline {
    fun handle(event: MiniAppEvent): Boolean
}
