package com.handoff.ui

import android.content.Context
import com.handoff.api.health.HealthEvent
import com.handoff.api.health.HealthPolicy
import com.handoff.storage.RegressionStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardBackendTest {
    private lateinit var context: Context
    private lateinit var backend: AndroidDashboardBackend
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        FrameworkConnection.service = null
        context.getSharedPreferences("handoff_appearance", 0).edit().clear().commit()
        context.getSharedPreferences("handoff_matrix", 0).edit().clear().commit()
        context.getSharedPreferences("handoff_settings", 0).edit().clear().commit()
        backend = AndroidDashboardBackend(context)
    }
    @Test fun offlineReadNeverInventsActivationSettingsOrRuns() = runBlocking {
        val state = backend.read()
        assertTrue(state.checked); assertFalse(state.activation.active()); assertFalse(state.settingsReadable)
        assertNull(state.mounted); assertTrue(state.runs.isEmpty()); assertTrue(state.settings.isEmpty())
        assertFalse(state.setting("douyin")); assertFalse(state.setting("kuaishou"))
    }
    @Test fun styleAndMotionPersistWithoutTouchingRemotePreferences() {
        assertTrue(backend.saveStyle(UiStyle.MIUIX)); assertTrue(backend.saveReduceMotion(true))
        assertEquals(UiStyle.MIUIX to true, AndroidDashboardBackend(context).appearance())
        assertTrue(context.getSharedPreferences("handoff_settings", 0).all.isEmpty())
    }
    @Test fun positiveResultStillRequiresEvidenceFromStorage() {
        val env = HealthPolicy.Environment(10, 16580, 35, "test", "LSPosed 2.2.1")
        backend.startRun(env)
        assertThrows(IllegalStateException::class.java) { backend.mark(env, HealthPolicy.Case.WEB, "PASS") }
        assertFalse(RegressionStore.read(context).getJSONObject(0).getJSONObject("results").has("WEB"))
        backend.mark(env, HealthPolicy.Case.OTHER, "PASS")
        assertEquals("PASS", RegressionStore.read(context).getJSONObject(0).getJSONObject("results").getString("OTHER"))
    }
    @Test fun destinationsRespectGlobalToggleAndAllBilibiliEntries() {
        val base = DashboardSnapshot(settingsReadable = true, installed = mapOf("bilibili" to true, "douyin" to true),
            settings = mapOf("browser" to false, "sdk" to true, "service" to false, "container" to false))
        assertEquals("已开启", base.destinationState("bilibili")); assertEquals("未开启", base.destinationState("douyin"))
        assertEquals("未开启", base.copy(settings = base.settings + ("enabled" to false)).destinationState("bilibili"))
        assertEquals("待连接", base.copy(settingsReadable = false).destinationState("bilibili"))
    }
    @Test fun historicalFrameworkRecordsCannotCountAsCurrentMounts() {
        val framework = FrameworkSnapshot(true, "LSPosed", "2.2.1", 1, 102, true, true, FrameworkSnapshot.RuntimeState.CURRENT)
        val snapshot = DashboardSnapshot(framework = framework, moduleCode = 10, qqCode = 16580,
            healthJson = JSONObject().put("module", 10).put("qq", 16580).put("framework", "OldFramework 1").toString(),
            evidence = listOf(HealthPolicy.Evidence(1, 10, 16580, HealthEvent(HealthEvent.Kind.INSTALLED, HealthEvent.Entry.BROWSER, HealthEvent.Reason.NONE), "OldFramework 1")))
        assertFalse(snapshot.current); assertNull(snapshot.mounted)
    }
    @Test fun remoteWritesRequireALiveFramework() {
        assertThrows(IllegalStateException::class.java) { backend.saveSetting("douyin", true) }
        assertThrows(IllegalArgumentException::class.java) { backend.saveSetting("arbitrary", true) }
    }
    @Test fun selectedTimHostIsPersistedAndReportedWithoutPretendingToBeQq() = runBlocking {
        backend.selectHost("com.tencent.tim")
        val snapshot = AndroidDashboardBackend(context).read()
        assertEquals("com.tencent.tim", snapshot.hostPackage)
        assertEquals("TIM", snapshot.hostLabel)
        assertEquals("com.tencent.tim", snapshot.environment.packageName())
        assertEquals("com.tencent.tim", JSONObject(snapshot.report()).getString("packageName"))
        assertThrows(IllegalArgumentException::class.java) { backend.selectHost("com.tencent.tim.fake") }
        Unit
    }
    @Test fun sameVersionQqReportCannotBeCurrentForTim() {
        val framework = FrameworkSnapshot(true, "LSPosed", "2.2.1", 1, 102, true, true, FrameworkSnapshot.RuntimeState.CURRENT)
        val snapshot = DashboardSnapshot(hostPackage = "com.tencent.tim", framework = framework, moduleCode = 10, qqCode = 16580,
            healthJson = JSONObject().put("module", 10).put("qq", 16580).put("framework", "LSPosed 2.2.1").toString())
        assertFalse(snapshot.current)
        assertNull(snapshot.mounted)
        assertTrue(snapshot.copy(healthJson = JSONObject(snapshot.healthJson).put("packageName", "com.tencent.tim").toString()).current)
    }

}
