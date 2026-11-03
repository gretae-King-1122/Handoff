package com.handoff.ui

import android.animation.ValueAnimator
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.handoff.api.health.HealthPolicy
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.rememberHazeState
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HandoffDashboard(
    state: DashboardState, actions: DashboardActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = DashboardPage.entries[pageIndex.coerceIn(0, 3)]
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var systemMotion by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    LifecycleResumeEffect(Unit) {
        systemMotion = ValueAnimator.areAnimatorsEnabled()
        onPauseOrDispose { }
    }
    val motion = !state.reduceMotion && systemMotion
    val lists = List(4) { rememberLazyListState() }
    val holder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val backdrop = rememberHazeState()
    val liquidBackdrop = if (android.os.Build.VERSION.SDK_INT >= 33) rememberLayerBackdrop() else null
    BackHandler(page != DashboardPage.HOME && !appearanceOpen && !aboutOpen && destination == null && confirmation == null) { pageIndex = 0 }
    HandoffTheme(state.style, motion) {
      CompositionLocalProvider(LocalBackdrop provides backdrop, LocalLiquidBackdrop provides liquidBackdrop) {
        val dark = isSystemInDarkTheme()
        val activity = LocalActivity.current
        SideEffect { activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        } }
        val navigate: (DashboardPage) -> Unit = { next ->
            if (next == page) scope.launch { if (motion) lists[next.ordinal].animateScrollToItem(0) else lists[next.ordinal].scrollToItem(0) }
            else pageIndex = next.ordinal
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                DashboardNavigation(page, navigate)
            },
        ) { padding ->
            AnimatedContent(page, Modifier.fillMaxSize().dashboardBackdropSource(backdrop, liquidBackdrop), transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(if (motion) 220 else 0)) + slideInHorizontally(tween(if (motion) 280 else 0)) { it / 12 * direction })
                    .togetherWith(fadeOut(tween(if (motion) 110 else 0)) + slideOutHorizontally(tween(if (motion) 220 else 0)) { -it / 16 * direction })
            }, label = "dashboard page") { target ->
                holder.SaveableStateProvider(target.name) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("page-${target.name}"),
                            state = lists[target.ordinal], contentPadding = PaddingValues(
                                start = 20.dp + padding.calculateStartPadding(LocalLayoutDirection.current),
                                end = 20.dp + padding.calculateEndPadding(LocalLayoutDirection.current),
                                top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when (target) {
                                DashboardPage.HOME -> homePage(state, actions, navigate) { destination = it }
                                DashboardPage.DIAGNOSTICS -> diagnosticsPage(state.snapshot, actions, filter, { filter = it }) { confirmation = "clear-health" }
                                DashboardPage.REGRESSION -> regressionPage(state, actions) { confirmation = it }
                                DashboardPage.SETTINGS -> settingsPage(state, actions,
                                    chooseStyle = { appearanceOpen = true }, openAbout = { aboutOpen = true })
                            }
                        }
                    }
                }
            }
        }
        if (appearanceOpen) GlassSheet(onDismiss = { appearanceOpen = false }) {
            AppearanceChoices(state) { actions.setStyle(it) }
        }
        if (aboutOpen) GlassSheet(onDismiss = { aboutOpen = false }) {
            AboutContent(state.snapshot, uriHandler::openUri)
        }
        destination?.let { key ->
            GlassSheet(onDismiss = { destination = null }) {
                val app = destinations.first { it.key == key }
                Column(Modifier.fillMaxWidth().testTag("destination-sheet").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DestinationMark(app, state.snapshot.appIcons[key])
                    Text(app.title, style = MaterialTheme.typography.headlineMedium)
                    Text(app.detail, style = MaterialTheme.typography.bodyLarge)
                    InfoLine("交接入口", state.snapshot.destinationState(key))
                    InfoLine("国内版 App", if (state.snapshot.installed[key] == true) "已安装" else "未发现")
                    QuietText(if (key == "bilibili") "汇总网页、小程序 SDK、服务与容器四类入口。请手动确认是否打开对应作品，并在日志页记录结果。" else "可选试验，默认关闭。开关独立，不猜作品 ID，不降级到首页。")
                    DashboardPrimaryButton({ destination = null; pageIndex = DashboardPage.SETTINGS.ordinal }, Modifier.fillMaxWidth()) { Text("前往交接设置") }
                }
            }
        }
        confirmation?.let { id ->
            val test = id.removePrefix("pass:").takeIf { id.startsWith("pass:") }?.let { HealthPolicy.Case.valueOf(it) }
            val title = when (id) { "start" -> "新建测试记录？"; "clear-health" -> "清除诊断记录？"; "clear-runs" -> "清除全部测试记录？"; else -> "确认「${test?.title}」通过？" }
            val detail = when (id) {
                "start" -> "保留历史记录。新的 ${HealthPolicy.Case.entries.size} 项测试均为未验证，正向通过需要本轮实际交接信号，并由你确认结果。"
                "clear-health" -> "仅清除本机诊断，不清理 QQ / TIM 数据或测试记录。后续信号仍可继续上报。"
                "clear-runs" -> "将清除本机全部测试记录，不影响 QQ / TIM 或模块设置。"
                else -> if (test?.needsEvidence == true) "请确认已打开对应作品，并且没有并行打开聊天应用内容。交接信号不能代替你的页面确认。" else "这是一条手动确认记录，不代表模块自动验证了负向结果。"
            }
            AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(title) }, text = { Text(detail, Modifier.verticalScroll(rememberScrollState())) },
                dismissButton = { TextButton({ confirmation = null }) { Text("取消") } },
                confirmButton = { TextButton({
                    confirmation = null
                    when (id) { "start" -> actions.startRun(); "clear-health" -> actions.clearDiagnostics(); "clear-runs" -> actions.clearRuns(); else -> test?.let { actions.mark(it, "PASS") } }
                }, Modifier.testTag("confirm-action")) { Text("确认") } })
        }
      }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GlassSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null, containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f)) {
        // Inside the sheet's placement transform: capture, drawing and touch coordinates move together.
        Column(Modifier.fillMaxWidth().frostedGlass(0.86f)) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            content()
        }
    }
}
