package com.handoff.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.handoff.api.health.HealthEvent
import com.handoff.api.health.HealthPolicy
import com.handoff.api.health.QqAdapters

internal fun LazyListScope.settingsPage(state: DashboardState, actions: DashboardActions, chooseStyle: () -> Unit,
    openAbout: () -> Unit) {
    val data = state.snapshot
    item("heading") { PageHeading(DashboardPage.SETTINGS, "喜欢的风格，刚好的动效。一切由你决定。") }
    item("appearance") { Panel {
        SectionTitle("个性化", "外观独立保存在本机，不影响交接设置")
        PressCard(chooseStyle, Modifier.testTag("appearance-style")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Text("Aa", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
                }
                Column(Modifier.weight(1f)) { Text("界面风格", style = MaterialTheme.typography.titleMedium); QuietText(state.style.label) }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text("减少动态效果", style = MaterialTheme.typography.titleMedium); QuietText("简化页面切换与卡片动画，尊重系统设置") }
            DashboardSwitch(state.reduceMotion, actions::setReduceMotion, enabled = !state.appearanceSaving,
                modifier = Modifier.testTag("reduce-motion").semantics { stateDescription = if (state.reduceMotion) "已减少动画" else "跟随系统动画" })
        }
        QuietText("两种风格均跟随系统浅色／深色。切换即刻生效，无需重启 ${data.hostLabel}。")
    } }
    item("master") { Panel {
        SectionTitle("交接控制", if (data.settingsReadable) "修改后强停重开 ${data.hostLabel}，确保当前进程应用设置" else if (data.framework.connected()) "设置读取失败，当前值未知；开关只读" else "框架未连接，当前值未知；开关只读")
        SettingSwitch("交接总开关", "所有支持入口的总控制", "enabled", state, actions)
    } }
    item("default-browser") { Panel {
        SectionTitle("普通网页", "排除社交平台与已支持的视频链接")
        SettingSwitch("用默认浏览器打开", "使用系统当前选定的浏览器；未设置时保留 ${data.hostLabel} 原打开方式。", "default_browser", state, actions)
    } }
    item("bilibili") { Panel {
        SectionTitle("哔哩哔哩", "已有入口保持原来的默认值")
        HealthEvent.Entry.entries.filter { it in setOf(HealthEvent.Entry.BROWSER, HealthEvent.Entry.SDK, HealthEvent.Entry.SERVICE, HealthEvent.Entry.CONTAINER) }.forEach { entry ->
            SettingSwitch(entry.title, null, entry.key, state, actions)
        }
    } }
    items(HealthEvent.Entry.entries.filter { it == HealthEvent.Entry.DOUYIN || it == HealthEvent.Entry.KUAISHOU }, key = { "pilot-${it.key}" }) { entry ->
        Panel {
            Badge("可选试验", Color(0xFFAC7B39))
            SettingSwitch(entry.title, if (entry == HealthEvent.Entry.DOUYIN) "短链由国内抖音处理，失败不降级到首页" else "作品与短链交给国内快手，解析失败返回 ${data.hostLabel} 浏览器", entry.key, state, actions)
            QuietText(if (entry == HealthEvent.Entry.KUAISHOU) "v.kuaishou.com 短链在后台进行受限 HTTPS 解析，可按返回键取消；请求被接受不代表已打开作品。" else "默认关闭，与快手开关独立；实际是否打开对应作品需要手动确认。")
        }
    }
    item("adapter") { ExpandablePanel("适配管理", QqAdapters.resolve(data.hostPackage, data.qqCode).title(), "expand-adapters") {
        QqAdapters.profiles().filter { it.packageName() == data.hostPackage }.forEach { profile ->
            Text(profile.title(), style = MaterialTheme.typography.titleMedium)
            QuietText(profile.evidence())
            Badge(if (profile.observed()) "有开发记录" else "未完整验证", MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        QuietText("版本命中不代表当前设备可用。实际挂载与交接结果请查看诊断。")
    } }
    item("report") { OutlinedButton(actions::share, Modifier.fillMaxWidth()) { Text("导出当前适配报告") } }
    item("about") { PressCard(openAbout, Modifier.fillMaxWidth().testTag("open-about")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("关于此软件", style = MaterialTheme.typography.titleMedium)
                QuietText("Handoff · 项目主页、开发详情与问题反馈")
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } }
}

@Composable private fun SettingSwitch(title: String, description: String?, key: String, state: DashboardState, actions: DashboardActions) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); description?.let { QuietText(it) } }
        if (key in state.pendingSettings) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        DashboardSwitch(state.snapshot.setting(key), { actions.setSetting(key, it) },
            enabled = state.snapshot.settingsReadable && key !in state.pendingSettings,
            modifier = Modifier.testTag("setting-$key").semantics { stateDescription = if (!state.snapshot.settingsReadable) "当前值未知，只读" else if (state.snapshot.setting(key)) "已开启" else "已关闭" })
    }
}

@Composable internal fun AppearanceChoices(state: DashboardState, onChoose: (UiStyle) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("appearance-options").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("选一个喜欢的风格", style = MaterialTheme.typography.headlineMedium)
        QuietText("随时切换，跟随系统浅色与深色。交接开关保持原样。")
        UiStyle.entries.forEach { style ->
            val selected = style == state.style
            val accent = Color(if (style == UiStyle.MATERIAL) 0xFF1A73E8 else 0xFF3478F6)
            OutlinedCard(onClick = { if (!state.appearanceSaving) onChoose(style) },
                enabled = !state.appearanceSaving, modifier = Modifier.fillMaxWidth().testTag("style-${style.key}").semantics { stateDescription = if (selected) "已选择" else "未选择" },
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(containerColor = if (selected) accent.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(62.dp).background(accent.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(22.dp).background(accent, RoundedCornerShape(if (style == UiStyle.MATERIAL) 9.dp else 5.dp)))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(2) { Box(Modifier.weight(1f).height(13.dp).background(accent.copy(alpha = 0.28f), RoundedCornerShape(4.dp))) } }
                        Box(Modifier.fillMaxWidth().height(8.dp).background(accent.copy(alpha = 0.18f), CircleShape))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(style.label, style = MaterialTheme.typography.titleMedium)
                        QuietText(if (style == UiStyle.MATERIAL) "Suki 胶囊底栏 · 选中展开文字" else "Miuix 原生组件 · 分组列表 · 平铺底栏")
                    }
                    RadioButton(selected, onClick = null)
                }
            }
        }
        if (state.appearanceSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

internal fun LazyListScope.regressionPage(state: DashboardState, actions: DashboardActions, confirm: (String) -> Unit) {
    val data = state.snapshot
    item("heading") { PageHeading(DashboardPage.REGRESSION, "手动测试并记录交接结果，运行信号请查看诊断页。") }
    item("environment") { Panel {
        Badge("当前测试环境")
        Text("${data.hostLabel} ${data.qqName}", style = MaterialTheme.typography.titleLarge)
        QuietText("Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n${if (data.framework.connected()) data.framework.label() else "框架未知"}")
        DashboardPrimaryButton({ confirm("start") }, Modifier.fillMaxWidth().testTag("start-run"), enabled = !state.busy) { Text("新建测试记录") }
    } }
    val run = data.runs.firstOrNull()
    if (run == null) item("empty") { Panel {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.AutoMirrored.Rounded.List, null, Modifier.padding(18.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text("好的记录，从第一次确认开始", style = MaterialTheme.typography.titleLarge)
        Text("还没有测试轮次。新一轮的 ${HealthPolicy.Case.entries.size} 项测试都会从未验证开始。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        QuietText("每轮绑定当前 ${data.hostLabel}、Android、框架与模块版本；不会预填开发设备的结果。")
    } } else {
        val same = run.environment == data.environment.key()
        item("progress") { Panel {
            val progress by animateFloatAsState(run.completed.toFloat() / HealthPolicy.Case.entries.size, motionSpec(500), label = "regression progress")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator({ progress }, Modifier.size(62.dp), strokeWidth = 5.dp, trackColor = MaterialTheme.colorScheme.primaryContainer)
                    Text("${run.completed}/${HealthPolicy.Case.entries.size}", style = MaterialTheme.typography.titleMedium)
                }
                Column { Text("当前轮次 ${timeLabel(run.started, true)}", style = MaterialTheme.typography.titleMedium); QuietText("通过 ${run.passed} · 未通过 ${run.failed}") }
            }
            if (!same) Text("环境已变化，本轮作为历史保留，请开始新一轮。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } }
        items(HealthPolicy.Case.entries, key = { "case-${it.name}" }) { test ->
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(test.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Badge(resultLabel(run.results[test.name]), if (run.results[test.name] == "FAIL") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Text(test.instruction.replace("QQ", data.hostLabel), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                QuietText(if (test.needsEvidence) "通过需要本轮交接信号，并由你确认实际结果" else "手动确认负向结果，模块不会自动判定")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton({ confirm("pass:${test.name}") }, enabled = same && !state.busy, modifier = Modifier.testTag("pass-${test.name}")) { Text("通过") }
                    TextButton({ actions.mark(test, "FAIL") }, enabled = same && !state.busy) { Text("未通过") }
                    TextButton({ actions.mark(test, "SKIP") }, enabled = same && !state.busy) { Text("跳过") }
                }
            }
        }
        item("open-qq") { DashboardPrimaryButton(actions::openQq, Modifier.fillMaxWidth()) { Text("打开 ${data.hostLabel}，执行测试") } }
    }
    if (data.runs.isNotEmpty()) item("history") { ExpandablePanel("环境历史", "本机 ${data.runs.size} 轮 · 最多保留 20 轮", "expand-history") {
        data.runs.take(20).forEach { old ->
            Text(timeLabel(old.started, true), style = MaterialTheme.typography.titleMedium)
            QuietText("${com.handoff.api.HostApps.label(old.packageName)} ${old.qq} / API ${old.android} / ${old.model}\n${old.framework} / 模块 ${old.module}\n通过 ${old.passed} · 未通过 ${old.failed} · 其余未验证或跳过")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    } }
    item("export") { OutlinedButton(actions::share, Modifier.fillMaxWidth()) { Text("导出测试与诊断记录") } }
    item("clear") { TextButton({ confirm("clear-runs") }, Modifier.fillMaxWidth()) { Text("清除测试记录", color = MaterialTheme.colorScheme.error) } }
}

internal fun resultLabel(result: String?) = when (result) { "PASS" -> "已确认通过"; "FAIL" -> "未通过"; "SKIP" -> "已跳过"; else -> "未验证" }
