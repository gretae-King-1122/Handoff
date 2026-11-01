package com.handoff.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private const val PROJECT_URL = "https://github.com/gretae-King-1122/Handoff"

@Composable internal fun AboutContent(data: DashboardSnapshot, onOpenUri: (String) -> Unit) {
    var linkFailed by remember { mutableStateOf(false) }
    val openLink: (String) -> Unit = { url ->
        linkFailed = runCatching { onOpenUri(url) }.isFailure
    }
    Column(Modifier.fillMaxWidth().testTag("about-sheet").verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("关于此软件", style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(R.drawable.ic_handoff_mark), null, Modifier.size(56.dp), tint = Color.Unspecified)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Handoff", style = MaterialTheme.typography.headlineSmall)
                Badge(if (data.moduleCode > 0) "v${data.moduleName} · ${data.moduleCode}" else "版本信息暂不可用")
            }
        }
        Text("QQ 负责聊天，链接回到对应 App。", style = MaterialTheme.typography.titleMedium)
        QuietText("一个轻量的 LSPosed 模块，将 QQ 中支持的哔哩哔哩、抖音和快手视频链接交给对应的国内版 App。")
        Panel(padding = 0.dp, spacing = 0.dp) {
            AboutLink("项目主页", "github.com/gretae-King-1122/Handoff", "about-project") { openLink(PROJECT_URL) }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            AboutLink("更新与版本", "查看 GitHub Releases", "about-releases") { openLink("$PROJECT_URL/releases") }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            AboutLink("问题反馈", "提交问题与改进建议", "about-issues") { openLink("$PROJECT_URL/issues") }
        }
        if (linkFailed) Text("无法打开链接，请检查是否有可用的浏览器。", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("about-link-error"))
        Panel {
            SectionTitle("开发详情")
            InfoLine("项目维护", "gretae-King-1122\nHandoff Contributors")
            InfoLine("开发语言", "Kotlin · Java")
            InfoLine("界面技术", "Jetpack Compose")
            InfoLine("设计组件", "Material 3 · Miuix 0.9.4")
            InfoLine("模块接口", "libxposed API 102")
            InfoLine("系统支持", "Android 8.0 及以上")
        }
        Panel {
            SectionTitle("开源与致谢")
            QuietText("液态玻璃底栏源自 SukiSU-Ultra，并保留 AndroidLiquidGlass 与 compose-miuix-ui 的来源声明。")
            QuietText("本开发版组合应用遵循 GPL-3.0；原项目文件保留 Apache-2.0 许可。完整许可文本随安装包提供。")
            TextButton({ openLink("https://github.com/SukiSU-Ultra/SukiSU-Ultra") }, Modifier.testTag("about-sukisu")) {
                Text("查看 SukiSU-Ultra")
            }
        }
    }
}

@Composable private fun AboutLink(title: String, detail: String, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            QuietText(detail)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
