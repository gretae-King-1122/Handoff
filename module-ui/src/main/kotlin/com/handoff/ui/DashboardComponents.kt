package com.handoff.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor
import androidx.compose.material.icons.rounded.Check

internal enum class DashboardPage(val label: String, val title: String, val eyebrow: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("首页", "概览", "设备与服务", Icons.Outlined.Home, Icons.Rounded.Home),
    DIAGNOSTICS("诊断", "诊断", "运行记录", Icons.Outlined.Info, Icons.Rounded.Info),
    REGRESSION("回归", "回归", "验证与记录", Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Rounded.List),
    SETTINGS("设置", "设置", "个性化与偏好", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable internal fun PageHeading(page: DashboardPage, description: String) {
    val miuix = LocalHandoffStyle.current == UiStyle.MIUIX
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (!miuix) Text(page.eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp))
        Text(page.title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun Panel(modifier: Modifier = Modifier, padding: Dp = 20.dp, spacing: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    if (LocalHandoffStyle.current == UiStyle.MIUIX) {
        MiuixCard(modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(padding)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing), content = content)
        }
        return
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@Composable internal fun SectionTitle(title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable internal fun QuietText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable internal fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = color.copy(alpha = 0.1f), shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable internal fun PressCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (LocalHandoffStyle.current == UiStyle.MIUIX) {
        MiuixCard(modifier, cornerRadius = 16.dp, insideMargin = PaddingValues(16.dp), onClick = onClick) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
        return
    }
    OutlinedCard(onClick, modifier, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable internal fun DashboardSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalHandoffStyle.current == UiStyle.MIUIX) {
        MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    } else {
        Switch(checked, onCheckedChange, modifier, enabled = enabled,
            thumbContent = if (checked) ({ Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }) else null)
    }
}

@Composable internal fun DashboardPrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    if (LocalHandoffStyle.current == UiStyle.MIUIX) {
        MiuixButton(onClick, modifier, enabled = enabled, colors = MiuixButtonDefaults.buttonColorsPrimary()) {
            CompositionLocalProvider(LocalContentColor provides MiuixContentColor.current) { content() }
        }
    } else Button(onClick, modifier, enabled = enabled, content = content)
}

@Composable internal fun ExpandablePanel(title: String, subtitle: String, tag: String,
    initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val motion = LocalHandoffMotion.current
    Panel {
        Row(Modifier.fillMaxWidth().testTag(tag).semantics { stateDescription = if (expanded) "已展开" else "已收起" }.clip(MaterialTheme.shapes.small).clickable(role = Role.Button) { expanded = !expanded }
            .padding(vertical = 6.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                QuietText(subtitle)
            }
            Crossfade(expanded, animationSpec = motionSpec(160), label = "disclosure icon") {
                Icon(if (it) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null,
                    Modifier.padding(start = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(expanded,
            enter = fadeIn(tween(if (motion) 180 else 0)) + expandVertically(tween(if (motion) 240 else 0)),
            exit = fadeOut(tween(if (motion) 120 else 0)) + shrinkVertically(tween(if (motion) 200 else 0))) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}
