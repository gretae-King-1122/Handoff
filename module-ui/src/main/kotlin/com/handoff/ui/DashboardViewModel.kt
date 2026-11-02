package com.handoff.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handoff.api.health.HealthPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface DashboardEvent {
    data class Message(val text: String) : DashboardEvent
    data class Share(val text: String) : DashboardEvent
    data class OpenHost(val packageName: String) : DashboardEvent
}

internal class DashboardViewModel(private val backend: DashboardBackend, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel(), DashboardActions {
    private val mutable = MutableStateFlow(DashboardState())
    val state = mutable.asStateFlow()
    private val messages = Channel<DashboardEvent>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private val io = Mutex()
    private val changed = Runnable { refresh() }

    init {
        backend.observe(changed)
        viewModelScope.launch {
            val appearance = withContext(dispatcher) { backend.appearance() }
            mutable.update { it.copy(style = appearance.first, reduceMotion = appearance.second) }
            for (request in refreshes) {
                mutable.update { it.copy(refreshing = true) }
                try {
                    val snapshot = io.withLock { withContext(dispatcher) { backend.read() } }
                    mutable.update { it.copy(snapshot = snapshot) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message("读取失败，保留上次状态；请刷新重试") }
                finally { mutable.update { it.copy(refreshing = false) } }
            }
        }
        refresh()
    }
    override fun refresh() { refreshes.trySend(Unit) }
    override fun setStyle(style: UiStyle) {
        if (mutable.value.appearanceSaving || mutable.value.style == style) return
        appearanceChange({ backend.saveStyle(style) }) { it.copy(style = style) }
    }
    override fun setReduceMotion(reduce: Boolean) {
        if (mutable.value.appearanceSaving || mutable.value.reduceMotion == reduce) return
        appearanceChange({ backend.saveReduceMotion(reduce) }) { it.copy(reduceMotion = reduce) }
    }
    private fun appearanceChange(save: () -> Boolean, update: (DashboardState) -> DashboardState) {
        mutable.update { it.copy(appearanceSaving = true) }
        viewModelScope.launch {
            try {
                if (io.withLock { withContext(dispatcher) { save() } }) mutable.update(update)
                else message("外观保存失败，未应用更改")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message("外观保存失败，未应用更改") }
            finally { mutable.update { it.copy(appearanceSaving = false) } }
        }
    }
    override fun setSetting(key: String, enabled: Boolean) {
        if (!mutable.value.snapshot.settingsReadable || key in mutable.value.pendingSettings) return
        mutable.update { it.copy(pendingSettings = it.pendingSettings + key) }
        viewModelScope.launch {
            try {
                io.withLock { withContext(dispatcher) {
                    backend.saveSetting(key, enabled)
                    val snapshot = backend.read()
                    mutable.update { it.copy(snapshot = snapshot) }
                } }
                message("已保存，请强停重开 QQ / TIM")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message("保存失败，未确认生效"); refresh() }
            finally { mutable.update { it.copy(pendingSettings = it.pendingSettings - key) } }
        }
    }
    override fun selectHost(packageName: String) = mutate { backend.selectHost(packageName) }
    override fun requestScope() {
        viewModelScope.launch {
            withContext(dispatcher) {
                backend.requestScope { approved ->
                    message(if (approved) "作用域已更新，请重开 ${mutable.value.snapshot.hostLabel}" else "请到 LSPosed 手动检查 ${mutable.value.snapshot.hostLabel} 作用域")
                    refresh()
                }
            }
        }
    }
    override fun startRun() {
        val env = mutable.value.snapshot.environment
        if (env.qq() <= 0) return message("请先安装 ${mutable.value.snapshot.hostLabel}")
        mutate { backend.startRun(env) }
    }
    override fun mark(test: HealthPolicy.Case, result: String) {
        val env = mutable.value.snapshot.environment
        mutate { backend.mark(env, test, result) }
    }
    override fun clearDiagnostics() = mutate { backend.clearDiagnostics() }
    override fun clearRuns() = mutate { backend.clearRuns() }
    private fun mutate(action: () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { io.withLock { withContext(dispatcher) { action() } }; refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message("未保存：存储不可用、环境已变化，或缺少本轮匹配交接证据") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    override fun share() { messages.trySend(DashboardEvent.Share(mutable.value.snapshot.report())) }
    override fun openQq() { messages.trySend(DashboardEvent.OpenHost(mutable.value.snapshot.hostPackage)) }
    private fun message(text: String) { messages.trySend(DashboardEvent.Message(text)) }
    override fun onCleared() { backend.unobserve(changed); refreshes.close(); messages.close() }
}
