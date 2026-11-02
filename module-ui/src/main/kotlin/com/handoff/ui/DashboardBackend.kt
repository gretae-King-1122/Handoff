package com.handoff.ui

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.handoff.api.HostApps
import com.handoff.api.health.HealthEvent
import com.handoff.api.health.HealthPolicy
import com.handoff.storage.HealthStore
import com.handoff.storage.RegressionStore
import io.github.libxposed.service.XposedService
import org.json.JSONObject
import kotlin.math.roundToInt

/** All disk, package-manager and binder operations are called on the IO dispatcher. */
internal interface DashboardBackend {
    suspend fun read(): DashboardSnapshot
    fun appearance(): Pair<UiStyle, Boolean>
    fun saveStyle(style: UiStyle): Boolean
    fun saveReduceMotion(reduce: Boolean): Boolean
    fun saveSetting(key: String, enabled: Boolean)
    fun startRun(environment: HealthPolicy.Environment)
    fun mark(environment: HealthPolicy.Environment, test: HealthPolicy.Case, result: String)
    fun clearDiagnostics()
    fun clearRuns()
    fun selectHost(packageName: String) {}
    fun requestScope(callback: (Boolean) -> Unit)
    fun observe(callback: Runnable) {}
    fun unobserve(callback: Runnable) {}
}

internal class AndroidDashboardBackend(private val context: Context) : DashboardBackend {
    private data class CachedIcon(val updated: Long, val version: Long, val size: Int, val bitmap: ImageBitmap)
    private val icons = mutableMapOf<String, CachedIcon>()
    private val styles = UiStyleStore(context)
    private val appearance = context.getSharedPreferences("handoff_appearance", Context.MODE_PRIVATE)
    override fun appearance() = styles.read() to runCatching { appearance.getBoolean("reduce_motion", false) }.getOrDefault(false)
    override fun saveStyle(style: UiStyle) = styles.write(style)
    override fun saveReduceMotion(reduce: Boolean): Boolean {
        val previous = appearance().second
        if (runCatching { appearance.edit().putBoolean("reduce_motion", reduce).commit() }.getOrDefault(false)) return true
        runCatching { appearance.edit().putBoolean("reduce_motion", previous).commit() }
        return false
    }
    override suspend fun read(): DashboardSnapshot {
        val own = pkg(context.packageName)
        val hosts = listOf(HostApps.QQ, HostApps.TIM).associateWith(::pkg)
        val savedHost = appearance.getString("host_package", null)
        val host = savedHost?.takeIf(HostApps::isSupported) ?: if (hosts[HostApps.QQ] == null && hosts[HostApps.TIM] != null) HostApps.TIM else HostApps.QQ
        val qq = hosts[host]
        val apps = destinations.associate { it.key to pkg(it.packageName) }
        val service = FrameworkConnection.service
        val framework = FrameworkSnapshot.read(service, code(own), host)
        val remote = if (framework.connected()) runCatching {
            val prefs = service!!.getRemotePreferences("handoff_settings")
            buildMap {
                put("enabled", prefs.getBoolean("enabled", true))
                HealthEvent.Entry.entries.filter { it != HealthEvent.Entry.SYSTEM }.forEach {
                    put(it.key, prefs.getBoolean(it.key, it.defaultEnabled()))
                }
            }
        }.getOrNull() else null
        val health = HealthStore.read(context, host)
        val storedRuns = RegressionStore.read(context)
        val runs = org.json.JSONArray().also { selected ->
            for (index in 0 until storedRuns.length()) storedRuns.optJSONObject(index)?.let { run ->
                if (run.optString("packageName", HostApps.QQ) == host) selected.put(run)
            }
        }
        return DashboardSnapshot(
            checked = true, hostPackage = host, hostVersions = hosts.mapValues { it.value?.versionName ?: "未安装" }, framework = framework, moduleCode = code(own), moduleName = own?.versionName ?: "—",
            qqCode = code(qq), qqName = qq?.versionName ?: "未安装",
            installed = apps.mapValues { it.value != null },
            appIcons = destinations.mapNotNull { app -> appIcon(app.packageName, apps[app.key])?.let { app.key to it } }.toMap(),
            settingsReadable = remote != null, settings = remote.orEmpty(), healthJson = health.toString(),
            runsJson = runs.toString(), evidence = HealthStore.evidence(health),
            runs = (0 until runs.length()).mapNotNull { index -> runs.optJSONObject(index)?.let { run ->
                val results = run.optJSONObject("results") ?: JSONObject()
                RegressionRun(run.optLong("started"), run.optString("environment"), run.optLong("qq"), run.optInt("android"),
                    run.optString("model"), run.optString("framework"), run.optLong("module"),
                    HealthPolicy.Case.entries.mapNotNull { test -> results.optString(test.name).takeIf { it.isNotBlank() }?.let { test.name to it } }.toMap(), run.optString("packageName", HostApps.QQ))
            } }.filter { it.packageName == host },
        )
    }
    override fun saveSetting(key: String, enabled: Boolean) {
        require(key == "enabled" || HealthEvent.Entry.entries.any { it != HealthEvent.Entry.SYSTEM && it.key == key })
        val service = FrameworkConnection.service ?: error("框架未连接")
        check(service.getRemotePreferences("handoff_settings").edit().putBoolean(key, enabled).commit())
    }
    override fun startRun(environment: HealthPolicy.Environment) { RegressionStore.start(context, environment) }
    override fun mark(environment: HealthPolicy.Environment, test: HealthPolicy.Case, result: String) {
        RegressionStore.result(context, environment, test, result)
    }
    override fun clearDiagnostics() { HealthStore.clear(context) }
    override fun clearRuns() { RegressionStore.clear(context) }
    override fun requestScope(callback: (Boolean) -> Unit) {
        val service = FrameworkConnection.service ?: return callback(false)
        val host = appearance.getString("host_package", null)?.takeIf(HostApps::isSupported)
            ?: if (pkg(HostApps.QQ) == null && pkg(HostApps.TIM) != null) HostApps.TIM else HostApps.QQ
        runCatching { service.requestScope(listOf(host), object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) = callback(approved.contains(host))
            override fun onScopeRequestFailed(reason: String) = callback(false)
        }) }.onFailure { callback(false) }
    }
    override fun selectHost(packageName: String) {
        require(HostApps.isSupported(packageName))
        check(appearance.edit().putString("host_package", packageName).commit())
    }
    override fun observe(callback: Runnable) = FrameworkConnection.observe(callback)
    override fun unobserve(callback: Runnable) = FrameworkConnection.unobserve(callback)
    private fun appIcon(packageName: String, info: PackageInfo?): ImageBitmap? {
        if (info == null) {
            icons.remove(packageName)
            return null
        }
        val size = (48 * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        icons[packageName]?.takeIf { it.updated == info.lastUpdateTime && it.version == code(info) && it.size == size }
            ?.let { return it.bitmap }
        // Runs on IO with the rest of the package reads. Rasterize both ordinary and
        // adaptive Drawables without tinting, then reuse pixels until the app changes.
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(size, size).asImageBitmap()
        }.getOrNull()
        if (bitmap != null) icons[packageName] = CachedIcon(info.lastUpdateTime, code(info), size, bitmap)
        else icons.remove(packageName)
        return bitmap
    }
    @Suppress("DEPRECATION") private fun pkg(name: String): PackageInfo? = runCatching { context.packageManager.getPackageInfo(name, 0) }.getOrNull()
    @Suppress("DEPRECATION") private fun code(info: PackageInfo?): Long = info?.let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
    } ?: 0L
}
