package com.handoff.ui

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handoff.api.health.HealthEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun LazyListScope.homePage(state: DashboardState, actions: DashboardActions, navigate: (DashboardPage) -> Unit, destination: (String) -> Unit) {
    val data = state.snapshot
    item("heading") { PageHeading(DashboardPage.HOME, "视频交给对应 App，普通网页用默认浏览器打开。") }
    item("host") { Panel {
        SectionTitle("聊天应用", "切换后分别检查版本、激活与诊断记录；交接开关共用")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(com.handoff.api.HostApps.QQ, com.handoff.api.HostApps.TIM).forEach { host ->
                FilterChip(data.hostPackage == host, { actions.selectHost(host) },
                    label = { Text("${com.handoff.api.HostApps.label(host)} · ${data.hostVersions[host] ?: "未安装"}") },
                    enabled = !state.busy, modifier = Modifier.testTag("host-$host"))
            }
        }
    } }
    item("status") { ActivationHero(data) { navigate(DashboardPage.DIAGNOSTICS) } }
    item("stats") {
      if (LocalHandoffStyle.current == UiStyle.MIUIX) {
        Panel(padding = 18.dp) {
            InfoLine("入口挂载记录", data.mounted?.toString() ?: "—")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            InfoLine("本机测试记录", data.runs.size.toString())
        }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(data.mounted?.toString() ?: "—", "入口挂载记录", "共 ${HealthEvent.Entry.entries.size - 1} 类入口", Modifier.weight(1f))
            Metric(data.runs.size.toString(), "本机测试记录", "实际结果，由你确认", Modifier.weight(1f))
        }
      }
    }
    item("destinations") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("链接目的地", "点按查看状态与交接设置")
            DestinationCards(data, destination)
        }
    }
    if (data.checked && !data.activation.active()) item("setup") {
        ExpandablePanel("需要一点准备", "查看启用与检查步骤", "expand-setup") {
            listOf("01" to "在兼容的 LSPosed 框架中启用 Handoff", "02" to "为模块勾选 ${data.hostLabel} 作用域", "03" to "强停并重开 ${data.hostLabel}，再返回查看状态").forEach { (number, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(number, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge); Text(text, style = MaterialTheme.typography.bodyMedium) }
            }
            if (data.framework.connected() && data.framework.scopeKnown() && !data.framework.scoped()) {
                FilledTonalButton(actions::requestScope, Modifier.fillMaxWidth()) { Text("申请 ${data.hostLabel} 作用域") }
            }
        }
    }
    item("framework") {
        ExpandablePanel("LSPosed / 框架信息", if (data.framework.connected()) data.framework.label() else "尚未连接", "expand-framework", initiallyExpanded = true) {
            InfoLine("连接状态", if (!data.checked) "检查中" else if (data.framework.connected()) "已连接" else "未连接")
            InfoLine("框架名称", data.framework.name())
            InfoLine("框架版本", data.framework.version())
            InfoLine("框架版本号", if (data.framework.connected()) data.framework.versionCode().toString() else "未知")
            InfoLine("libxposed API", if (data.framework.connected()) data.framework.api().toString() else "未知")
            InfoLine("${data.hostLabel} 作用域", data.framework.scopeLabel(data.hostPackage))
        }
    }
    item("device") {
        ExpandablePanel("版本与设备", "所有信息仅保存在本机", "expand-device", initiallyExpanded = true) {
            InfoLine("Handoff", "v${data.moduleName} / ${data.moduleCode}")
            InfoLine(data.hostLabel, "${data.qqName} / ${data.qqCode}")
            InfoLine("Android", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            InfoLine("设备型号", Build.MODEL)
            listOf("bilibili" to "哔哩哔哩", "douyin" to "抖音", "kuaishou" to "快手").forEach { (key, title) ->
                InfoLine(title, if (data.installed[key] == true) "已安装国内版" else "未发现国内版")
            }
        }
    }
    item("notice") { QuietText("激活只表示当前模块已在 ${data.hostLabel} 主进程加载；请手动确认是否打开对应作品，可在日志页记录结果、在诊断页查看运行信号。") }
}

@Composable private fun ActivationHero(data: DashboardSnapshot, onInspect: () -> Unit) {
    if (LocalHandoffStyle.current == UiStyle.MIUIX) {
        Panel(Modifier.testTag("activation-card"), spacing = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.Info, null, Modifier.padding(13.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    QuietText("激活状态")
                    Text(data.activation.title(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("activation-title"))
                }
            }
            Text(data.activation.detail(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val status = if (!data.settingsReadable) "设置状态未知" else if (data.setting("enabled")) "已开启" else "已关闭"
                QuietText("链接跳转 · $status", Modifier.weight(1f))
                TextButton(onInspect, modifier = Modifier.testTag("hero-inspect")) { Text("查看诊断") }
            }
        }
        return
    }
    val fill by animateColorAsState(MaterialTheme.colorScheme.primaryContainer, motionSpec(250), label = "status surface")
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = MaterialTheme.shapes.large, color = Color.Transparent, modifier = Modifier.testTag("activation-card")) {
        Box(Modifier.fillMaxWidth().background(fill)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Surface(color = ink.copy(alpha = 0.08f), shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (data.activation.active()) "●" else "○", color = ink, style = MaterialTheme.typography.labelSmall)
                        Text("激活状态", color = ink, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Crossfade(data.activation.title(), animationSpec = motionSpec(), label = "activation title") { title ->
                    Text(title, color = ink, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("activation-title"))
                }
                Text(data.activation.detail(), color = ink.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    val status = if (!data.settingsReadable) "设置状态未知" else if (data.setting("enabled")) "已开启" else "已关闭"
                    Text("链接跳转 · $status", color = ink, style = MaterialTheme.typography.labelSmall)
                    TextButton(onInspect, colors = ButtonDefaults.textButtonColors(contentColor = ink), modifier = Modifier.testTag("hero-inspect")) { Text("查看诊断") }
                }
            }
        }
    }
}

@Composable private fun Metric(value: String, title: String, detail: String, modifier: Modifier = Modifier) {
    Panel(modifier, padding = 18.dp, spacing = 6.dp) {
        Crossfade(value, animationSpec = motionSpec(), label = "metric") { Text(it, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, style = MaterialTheme.typography.labelLarge); QuietText(detail) }
    }
}

internal data class Destination(val key: String, val packageName: String, val title: String, val mark: String, val color: Color, val detail: String)
internal val destinations = listOf(
    Destination("bilibili", "tv.danmaku.bili", "哔哩哔哩", "B", Color(0xFFDB7899), "视频链接与小程序"),
    Destination("douyin", "com.ss.android.ugc.aweme", "抖音", "♪", Color(0xFF7989D6), "视频链接 · 可选试验"),
    Destination("kuaishou", "com.smile.gifmaker", "快手", "K", Color(0xFFD19B4E), "作品与短链 · 可选试验"),
)

@Composable private fun DestinationCards(data: DashboardSnapshot, onDestination: (String) -> Unit) {
    if (LocalHandoffStyle.current == UiStyle.MIUIX) {
        Panel(padding = 0.dp, spacing = 0.dp) {
            destinations.forEachIndexed { index, dest ->
                Row(Modifier.fillMaxWidth().testTag("destination-${dest.key}")
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onDestination(dest.key) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DestinationMark(dest, data.appIcons[dest.key])
                    Column(Modifier.weight(1f)) {
                        Text(dest.title, style = MaterialTheme.typography.titleMedium)
                        QuietText(dest.detail)
                    }
                    QuietText(data.destinationState(dest.key))
                }
                if (index < destinations.lastIndex) HorizontalDivider(Modifier.padding(start = 74.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
        return
    }
    BoxWithConstraints {
        val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            destinations.forEach { dest -> DestinationCard(dest, data.destinationState(dest.key), data.appIcons[dest.key], true, Modifier.fillMaxWidth()) { onDestination(dest.key) } }
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            destinations.forEach { dest -> DestinationCard(dest, data.destinationState(dest.key), data.appIcons[dest.key], false, Modifier.weight(1f)) { onDestination(dest.key) } }
        }
    }
}

@Composable private fun DestinationCard(destination: Destination, state: String, icon: ImageBitmap?, stacked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    PressCard(onClick, modifier.testTag("destination-${destination.key}")) {
        if (stacked) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DestinationMark(destination, icon)
            Column(Modifier.weight(1f)) { Text(destination.title, style = MaterialTheme.typography.titleMedium); QuietText(destination.detail) }
            Badge(state, MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            DestinationMark(destination, icon)
            Text(destination.title, style = MaterialTheme.typography.labelLarge)
            Text(state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun DestinationMark(destination: Destination, icon: ImageBitmap? = null) {
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(42.dp))
        return
    }
    Surface(color = destination.color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Text(destination.mark, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = destination.color) }
    }
}

internal fun DashboardSnapshot.diagnosis(): Pair<String, String> = when {
    qqCode == 0L -> "尚未发现 ${hostLabel}" to "安装国内版 ${hostLabel} 后，再为 Handoff 配置作用域。"
    !framework.connected() -> "框架未连接" to "请检查兼容的 LSPosed 框架；未连接不等于设备没有 root。"
    !framework.scopeKnown() -> "${hostLabel} 作用域未知" to "框架已连接，但作用域不可读，请到框架管理器检查。"
    !framework.scoped() -> "需要 ${hostLabel} 作用域" to "请授权模块在 ${hostLabel} 中运行，再强停重开 ${hostLabel}。"
    !current -> "等待本版本上报" to "强停重开 ${hostLabel}，再点击支持的卡片。旧环境记录不用于当前判断。"
    latestOutcome?.event()?.kind() == HealthEvent.Kind.ACCEPTED -> "已请求打开链接" to "请求已被接受；请确认目标页面是否正确打开。"
    latestOutcome?.event()?.kind() == HealthEvent.Kind.LAUNCH_FAILED -> "打开请求未成功" to "已保留 ${hostLabel} 原打开方式；请检查目标应用是否可用。"
    latestOutcome?.event()?.kind() == HealthEvent.Kind.PASSED -> "本次保留 ${hostLabel} 原流程" to latestOutcome!!.event().reason().title
    latestOutcome?.event()?.kind() == HealthEvent.Kind.ENTERED -> "入口已进入，待确认结果" to "打开请求本身不代表目标页面已经显示。"
    else -> "等待真实点击" to "打开 ${hostLabel} 点击支持的视频卡片，然后返回这里刷新。"
}

internal fun LazyListScope.diagnosticsPage(data: DashboardSnapshot, actions: DashboardActions, filter: String, onFilter: (String) -> Unit, confirmClear: () -> Unit) {
    item("heading") { PageHeading(DashboardPage.DIAGNOSTICS, "看清入口、信号与交接，让每一步都有依据。") }
    item("summary") { Panel {
        Badge(if (data.current) "本机诊断记录" else "待验证")
        Text(data.diagnosis().first, style = MaterialTheme.typography.headlineMedium)
        Text(data.diagnosis().second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        QuietText("最近上报 ${timeLabel(org.json.JSONObject(data.healthJson).optLong("at"))} · v${data.moduleName}")
        if (data.framework.connected() && data.framework.scopeKnown() && !data.framework.scoped()) FilledTonalButton(actions::requestScope) { Text("申请 ${data.hostLabel} 作用域") }
    } }
    item("entries") { ExpandablePanel("入口状态", "${HealthEvent.Entry.entries.size - 1} 类入口 · 挂载与交接分开展示", "expand-entries") {
        com.handoff.api.health.HealthEvent.Entry.entries.filter { it != HealthEvent.Entry.SYSTEM }.forEach { entry ->
            val records = if (data.current) data.evidence.filter { it.event().entry() == entry } else emptyList()
            val accepted = records.count { it.event().kind() == HealthEvent.Kind.ACCEPTED }
            val label = when { accepted > 0 -> "有交接记录 · $accepted"; records.any { it.event().kind() == HealthEvent.Kind.INSTALLED } -> "已挂载 · 未验证"; records.any { it.event().kind() == HealthEvent.Kind.INSTALL_FAILED } -> "挂载失败"; else -> "尚无记录" }
            InfoLine(entry.title, label)
        }
    } }
    item("signals-heading") { SectionTitle("最近信号", "按实际记录筛选；历史环境信号不会计入当前状态") }
    item("filters") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "全部", "accepted" to "交接", "issues" to "需关注").forEach { (key, title) ->
                FilterChip(filter == key, { onFilter(key) }, label = { Text(title) }, modifier = Modifier.testTag("filter-$key"))
            }
        }
    }
    val events = data.evidence.asReversed().filter { when (filter) {
        "accepted" -> it.event().kind() == HealthEvent.Kind.ACCEPTED
        "issues" -> it.event().kind() in setOf(HealthEvent.Kind.LAUNCH_FAILED, HealthEvent.Kind.INSTALL_FAILED, HealthEvent.Kind.PASSED)
        else -> true
    } }.take(20)
    if (events.isEmpty()) item("empty-signals") { Panel {
        Text(if (data.evidence.isEmpty()) "还没有信号，等一次真实点击" else "这个分类还没有记录", style = MaterialTheme.typography.titleMedium)
        QuietText("${data.hostLabel} 尚未重启、作用域未启用或报告通道不可用时，可能暂时没有上报。")
    } }
    items(events.withIndex().toList(), key = { "event-${it.index}-${it.value.at()}" }) { (_, evidence) ->
        Panel(if (LocalHandoffMotion.current) Modifier.animateItem() else Modifier) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val accent = when (evidence.event().kind()) { HealthEvent.Kind.ACCEPTED -> MaterialTheme.colorScheme.primary; HealthEvent.Kind.LAUNCH_FAILED, HealthEvent.Kind.INSTALL_FAILED -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                Box(Modifier.size(9.dp).background(accent, CircleShape))
                Column(Modifier.weight(1f)) { Text(evidence.event().entry().title, style = MaterialTheme.typography.titleMedium); QuietText(if (evidence.event().reason() == HealthEvent.Reason.NONE) kindLabel(evidence.event().kind()).replace("QQ", data.hostLabel) else evidence.event().reason().title) }
                QuietText(timeLabel(evidence.at()))
            }
        }
    }
    item("environment") { ExpandablePanel("环境快照", "当前设备与作用域", "expand-environment") {
        InfoLine(data.hostLabel, "${data.qqName} / ${data.qqCode}"); InfoLine("Android", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        InfoLine("框架", if (data.framework.connected()) data.framework.label() else "未连接 · 状态未知"); InfoLine("作用域", data.framework.scopeLabel(data.hostPackage))
        destinations.forEach { InfoLine(it.title, if (data.installed[it.key] == true) "已安装国内版" else "未发现国内版") }
    } }
    item("export") { OutlinedButton(actions::share, Modifier.fillMaxWidth().testTag("export-diagnostics")) { Text("导出脱敏诊断") } }
    item("privacy") { QuietText("离线保存 · 不读取聊天、链接或视频 ID · 不自动上传。导出前请核对设备型号与软件版本。") }
    item("clear") { TextButton(confirmClear, Modifier.fillMaxWidth()) { Text("清除本机诊断记录", color = MaterialTheme.colorScheme.error) } }
}

internal fun timeLabel(at: Long, date: Boolean = false): String = if (at <= 0) "暂无" else SimpleDateFormat(if (date) "MM-dd HH:mm" else "HH:mm:ss", Locale.CHINA).format(Date(at))
internal fun kindLabel(kind: HealthEvent.Kind): String = when (kind) {
    HealthEvent.Kind.SESSION -> "QQ 主进程开始上报"; HealthEvent.Kind.LOADER_BOUND -> "已按实际加载器补挂"
    HealthEvent.Kind.INSTALLED -> "已挂载，尚未验证交接"; HealthEvent.Kind.INSTALL_FAILED -> "目标签名挂载失败"
    HealthEvent.Kind.ENTERED -> "入口已进入"; HealthEvent.Kind.PASSED -> "保留 QQ 原流程"
    HealthEvent.Kind.ACCEPTED -> "外跳请求已接受"; HealthEvent.Kind.LAUNCH_FAILED -> "外跳失败，保留原流程"
}
