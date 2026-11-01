package com.handoff.ui

import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import com.handoff.api.health.HealthEvent
import com.handoff.api.health.HealthPolicy
import org.json.JSONArray
import org.json.JSONObject

internal data class RegressionRun(
    val started: Long, val environment: String, val qq: Long, val android: Int,
    val model: String, val framework: String, val module: Long, val results: Map<String, String>,
) {
    val passed get() = results.values.count { it == "PASS" }
    val failed get() = results.values.count { it == "FAIL" }
    val completed get() = results.count { it.value in setOf("PASS", "FAIL", "SKIP") }
}

internal data class DashboardSnapshot(
    val checked: Boolean = false,
    val framework: FrameworkSnapshot = FrameworkSnapshot.unavailable(),
    val moduleCode: Long = 0, val moduleName: String = "—",
    val qqCode: Long = 0, val qqName: String = "未安装",
    val installed: Map<String, Boolean> = emptyMap(),
    val appIcons: Map<String, ImageBitmap> = emptyMap(),
    val settingsReadable: Boolean = false, val settings: Map<String, Boolean> = emptyMap(),
    val healthJson: String = "{}", val runsJson: String = "[]",
    val evidence: List<HealthPolicy.Evidence> = emptyList(), val runs: List<RegressionRun> = emptyList(),
) {
    val activation: ActivationStatus get() = ActivationStatus.resolve(checked, framework)
    val environment: HealthPolicy.Environment get() = HealthPolicy.Environment(
        moduleCode, qqCode, Build.VERSION.SDK_INT, Build.MODEL, HealthPolicy.safe(framework.label()),
    )
    val current: Boolean get() {
        val health = JSONObject(healthJson)
        return framework.connected() && moduleCode > 0 && qqCode > 0 &&
            health.optLong("module") == moduleCode && health.optLong("qq") == qqCode &&
            health.optString("framework") == HealthPolicy.safe(framework.label())
    }
    val mounted: Int? get() = if (!current) null else evidence
        .filter { it.event().kind() == HealthEvent.Kind.INSTALLED && it.event().entry() != HealthEvent.Entry.SYSTEM }
        .map { it.event().entry() }.distinct().size
    val latestOutcome: HealthPolicy.Evidence? get() = if (!current) null else evidence.lastOrNull {
        it.event().kind() in setOf(HealthEvent.Kind.ACCEPTED, HealthEvent.Kind.LAUNCH_FAILED, HealthEvent.Kind.PASSED, HealthEvent.Kind.ENTERED)
    }
    fun setting(key: String): Boolean = settings[key] ?: HealthEvent.Entry.entries
        .find { it.key == key }?.defaultEnabled() ?: true
    fun destinationState(key: String): String {
        if (!settingsReadable) return if (framework.connected()) "状态未知" else "待连接"
        val enabled = if (key == "bilibili") listOf("browser", "sdk", "service", "container").any(::setting) else setting(key)
        return when {
            !setting("enabled") || !enabled -> "未开启"
            installed[key] != true -> "未安装"
            else -> "已开启"
        }
    }
    fun report(): String = JSONObject().put("schema", 1).put("moduleVersion", moduleName)
        .put("environment", environment.key()).put("scope", framework.scopeLabel())
        .put("events", JSONObject(healthJson).optJSONArray("events") ?: JSONArray())
        .put("runs", JSONArray(runsJson)).put("adapter", com.handoff.api.health.QqAdapters.resolve(qqCode).id())
        .put("notice", "交接记录不等于视频加载成功；负向结果为手动确认；不包含聊天、链接、视频ID或设备序列号。")
        .toString(2)
}

internal data class DashboardState(
    val snapshot: DashboardSnapshot = DashboardSnapshot(),
    val style: UiStyle = UiStyle.MATERIAL, val reduceMotion: Boolean = false,
    val refreshing: Boolean = false, val busy: Boolean = false,
    val appearanceSaving: Boolean = false, val pendingSettings: Set<String> = emptySet(),
)

internal interface DashboardActions {
    fun refresh() {}
    fun setStyle(style: UiStyle) {}
    fun setReduceMotion(reduce: Boolean) {}
    fun setSetting(key: String, enabled: Boolean) {}
    fun requestScope() {}
    fun startRun() {}
    fun mark(test: HealthPolicy.Case, result: String) {}
    fun clearDiagnostics() {}
    fun clearRuns() {}
    fun share() {}
    fun openQq() {}
}
