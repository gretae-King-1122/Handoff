package com.handoff.api

/**
 * 事件接收端：hook 后端（Java）把 [MiniAppEvent] 交进来。
 *
 * @return `true` = 已接管，hook 应阻断原方法；`false` = 放行。
 */
fun interface EventSink {
    fun onEvent(event: MiniAppEvent): Boolean
}

/**
 * Hook 后端：把「一次小程序启动」变成 [MiniAppEvent]，交给 [sink]。
 *
 * 换后端（Zygisk / 内核）只需重新实现本接口，`Pipeline` 及以下不接触 QQ 内部类型。
 */
fun interface HookBackend {
    /**
     * @param classLoader 目标包（QQ）的 ClassLoader，来自 `PackageReadyParam#getClassLoader()`
     * @param processName 当前进程名，如 `com.tencent.mobileqq` / `com.tencent.mobileqq:mini`
     * @param sink        事件接收端
     */
    fun install(
        classLoader: ClassLoader,
        processName: String,
        sink: EventSink,
    )
}
