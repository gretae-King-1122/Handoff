package com.handoff.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The launcher and LSPosed settings entry both open the same Compose dashboard. */
class DashboardActivity : ComponentActivity() {
    private val snackbar = androidx.compose.material3.SnackbarHostState()
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiStyleStore(this).read().theme)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DashboardViewModel(AndroidDashboardBackend(applicationContext)) as T
        })[DashboardViewModel::class.java]
        setContent {
            val state = model.state.collectAsStateWithLifecycle()
            HandoffDashboard(state.value, model, snackbar)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) { model.refresh(); delay(4_000) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.events.collect { event -> when (event) {
                    is DashboardEvent.Share -> runCatching {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Handoff 脱敏兼容性报告")
                            .putExtra(Intent.EXTRA_TEXT, event.text), "导出前请核对环境信息"))
                    }.onFailure { toast("暂时无法分享报告") }
                    DashboardEvent.OpenQq -> runCatching {
                        val intent = packageManager.getLaunchIntentForPackage("com.tencent.mobileqq")
                        if (intent == null) toast("无法启动 QQ") else startActivity(intent)
                    }.onFailure { toast("QQ 启动失败，请手动打开") }
                    is DashboardEvent.Message -> launch { snackbar.showSnackbar(event.text) }
                } }
            }
        }
    }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
}
