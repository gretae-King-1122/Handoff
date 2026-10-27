package com.handoff.api

/**
 * 命中日志。
 *
 * 注意（API 102 事实）：目标进程内 `openRemoteFile()` 只读，所以 M1/M2 的探针日志走
 * `XposedInterface.log()`，不走本接口；本接口留给 M3 的「广播回传 → App 落盘」方案。
 */
interface LogRepository {
    fun append(entry: LogEntry)

    fun tail(limit: Int): List<LogEntry>

    fun clear()
}
