package com.handoff.ui

import androidx.lifecycle.ViewModelStore
import com.handoff.api.health.HealthPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun model(backend: FakeBackend) = DashboardViewModel(backend, dispatcher).also { store.put("dashboard", it) }

    @Test fun refreshDuringAnInflightReadIsCoalescedAndNeverLost() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val backend = FakeBackend().apply { beforeRead = { if (reads == 1) gate.await() } }
        val model = model(backend)
        runCurrent(); assertTrue(model.state.value.refreshing); assertEquals(1, backend.reads)
        repeat(4) { model.refresh() }
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(2, backend.reads); assertFalse(model.state.value.refreshing)
    }
    @Test fun unreadableSettingsCannotInvokeABackendWrite() = runTest(dispatcher) {
        val backend = FakeBackend(); val model = model(backend); advanceUntilIdle()
        model.setSetting("douyin", true); advanceUntilIdle()
        assertTrue(backend.writes.isEmpty()); assertFalse(model.state.value.snapshot.settingsReadable)
    }
    @Test fun independentPilotChangesAreReadBackWithoutOptimisticState() = runTest(dispatcher) {
        val backend = FakeBackend().apply { snapshot = snapshot.copy(settingsReadable = true, settings = mapOf("douyin" to false, "kuaishou" to false)) }
        val model = model(backend); advanceUntilIdle()
        model.setSetting("douyin", true)
        assertFalse(model.state.value.snapshot.setting("douyin")); assertTrue("douyin" in model.state.value.pendingSettings)
        advanceUntilIdle()
        assertTrue(model.state.value.snapshot.setting("douyin")); assertFalse(model.state.value.snapshot.setting("kuaishou"))
        assertTrue(model.state.value.pendingSettings.isEmpty()); assertEquals(listOf("douyin" to true), backend.writes)
    }
    @Test fun writeFailureClearsPendingAndPreservesVerifiedSetting() = runTest(dispatcher) {
        val backend = FakeBackend().apply { snapshot = snapshot.copy(settingsReadable = true); failWrite = true }
        val model = model(backend); advanceUntilIdle()
        model.setSetting("douyin", true); advanceUntilIdle()
        assertFalse(model.state.value.snapshot.setting("douyin")); assertTrue(model.state.value.pendingSettings.isEmpty())
        assertTrue((model.events.first() as DashboardEvent.Message).text.contains("未确认生效"))
    }
    @Test fun failedAppearanceSaveNeverClaimsTheNewStyleWasApplied() = runTest(dispatcher) {
        val backend = FakeBackend().apply { appearanceSaved = false }; val model = model(backend); advanceUntilIdle()
        model.setStyle(UiStyle.MIUIX); advanceUntilIdle()
        assertEquals(UiStyle.MATERIAL, model.state.value.style); assertFalse(model.state.value.appearanceSaving)
        assertTrue((model.events.first() as DashboardEvent.Message).text.contains("未应用"))
    }
    @Test fun appearanceAndMotionAreSeparateFromRemoteSettings() = runTest(dispatcher) {
        val backend = FakeBackend(); val model = model(backend); advanceUntilIdle()
        model.setStyle(UiStyle.MIUIX); advanceUntilIdle(); model.setReduceMotion(true); advanceUntilIdle()
        assertEquals(UiStyle.MIUIX, model.state.value.style); assertTrue(model.state.value.reduceMotion)
        assertTrue(backend.writes.isEmpty()); assertEquals(UiStyle.MIUIX to true, backend.appearance())
    }
    @Test fun readFailureEndsLoadingAndAllowsLaterRefresh() = runTest(dispatcher) {
        val backend = FakeBackend(); val model = model(backend); advanceUntilIdle()
        backend.beforeRead = { error("unavailable") }; model.refresh(); advanceUntilIdle()
        assertFalse(model.state.value.refreshing); assertTrue(model.state.value.snapshot.checked)
        backend.beforeRead = {}; backend.snapshot = backend.snapshot.copy(qqName = "9.3.75"); model.refresh(); advanceUntilIdle()
        assertEquals("9.3.75", model.state.value.snapshot.qqName)
    }
    @Test fun missingQqPreventsStartingARegressionRound() = runTest(dispatcher) {
        val backend = FakeBackend(); val model = model(backend); advanceUntilIdle()
        model.startRun(); advanceUntilIdle(); assertEquals(0, backend.starts)
    }
    @Test fun mutationsRefreshAndBackendRejectionsNeverBecomeResults() = runTest(dispatcher) {
        val backend = FakeBackend().apply { snapshot = snapshot.copy(qqCode = 16580); rejectMark = true }
        val model = model(backend); advanceUntilIdle(); model.startRun(); advanceUntilIdle()
        assertEquals(1, backend.starts); val count = backend.reads
        model.mark(HealthPolicy.Case.WEB, "PASS"); advanceUntilIdle()
        assertFalse(model.state.value.busy); assertEquals(count, backend.reads)
        assertTrue((model.events.first() as DashboardEvent.Message).text.contains("缺少本轮匹配交接证据"))
    }
    @Test fun clearingViewModelUnregistersItsFrameworkObserver() = runTest(dispatcher) {
        val backend = FakeBackend(); model(backend); advanceUntilIdle()
        assertNotNull(backend.observer); store.clear(); assertNull(backend.observer)
    }
    private class FakeBackend : DashboardBackend {
        var snapshot = DashboardSnapshot(checked = true)
        var reads = 0
        var beforeRead: suspend () -> Unit = {}
        var currentStyle = UiStyle.MATERIAL
        var reduceMotion = false
        var appearanceSaved = true
        var failWrite = false
        var rejectMark = false
        var starts = 0
        var observer: Runnable? = null
        val writes = mutableListOf<Pair<String, Boolean>>()
        override suspend fun read(): DashboardSnapshot { reads++; beforeRead(); return snapshot }
        override fun appearance() = currentStyle to reduceMotion
        override fun saveStyle(style: UiStyle): Boolean { if (appearanceSaved) currentStyle = style; return appearanceSaved }
        override fun saveReduceMotion(reduce: Boolean): Boolean { if (appearanceSaved) reduceMotion = reduce; return appearanceSaved }
        override fun saveSetting(key: String, enabled: Boolean) { if (failWrite) error("unavailable"); writes += key to enabled; snapshot = snapshot.copy(settings = snapshot.settings + (key to enabled)) }
        override fun startRun(environment: HealthPolicy.Environment) { starts++ }
        override fun mark(environment: HealthPolicy.Environment, test: HealthPolicy.Case, result: String) { if (rejectMark) error("No evidence") }
        override fun clearDiagnostics() {}
        override fun clearRuns() {}
        override fun requestScope(callback: (Boolean) -> Unit) { callback(false) }
        override fun observe(callback: Runnable) { observer = callback }
        override fun unobserve(callback: Runnable) { if (observer === callback) observer = null }
    }
}
