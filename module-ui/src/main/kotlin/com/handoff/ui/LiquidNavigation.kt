package com.handoff.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handoff.ui.suki.component.FloatingBottomBar
import com.handoff.ui.suki.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor

internal val LocalLiquidBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable internal fun LiquidNavigation(selected: DashboardPage, onSelect: (DashboardPage) -> Unit) {
    val backdrop = LocalLiquidBackdrop.current
    Box(Modifier.widthIn(max = 480.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Respect reduced motion and retain readable labels at accessibility font sizes.
        if (backdrop == null || !LocalHandoffMotion.current || LocalDensity.current.fontScale > 1.3f) {
            SukiMaterialNavigation(selected, onSelect)
        } else {
            val hardwareOptics = Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated
            FloatingBottomBar(modifier = Modifier.fillMaxWidth().testTag("navigation"),
                selectedIndex = selected.ordinal, onSelected = { onSelect(DashboardPage.entries[it]) },
                backdrop = backdrop, tabsCount = DashboardPage.entries.size, isBlurEnabled = hardwareOptics,
                accentColor = MaterialTheme.colorScheme.primary,
                tabContentColor = MaterialTheme.colorScheme.onSurface,
                surfaceContainer = MaterialTheme.colorScheme.surface) { activateTab ->
                DashboardPage.entries.forEach { page ->
                    FloatingBottomBarItem(selected = selected == page, onClick = { activateTab(page.ordinal) },
                        modifier = Modifier.testTag("nav-${page.name}")) {
                        CompositionLocalProvider(LocalContentColor provides MiuixContentColor.current) {
                            Icon(if (selected == page) page.selectedIcon else page.icon, null, Modifier.size(24.dp))
                            Text(page.label, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
                                softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
