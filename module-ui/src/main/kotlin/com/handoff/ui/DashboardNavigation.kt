package com.handoff.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem

@Composable internal fun DashboardNavigation(selected: DashboardPage, onSelect: (DashboardPage) -> Unit) {
    val insets = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    if (LocalHandoffStyle.current == UiStyle.MATERIAL) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(insets).padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center) {
            if (android.os.Build.VERSION.SDK_INT >= 33) LiquidNavigation(selected, onSelect)
            else SukiMaterialNavigation(selected, onSelect)
        }
    } else {
        Box(Modifier.fillMaxWidth().frostedGlass(0.92f).windowInsetsPadding(insets), contentAlignment = Alignment.Center) {
            MiuixNavigationBar(Modifier.widthIn(max = 720.dp).testTag("navigation"),
                color = Color.Transparent, defaultWindowInsetsPadding = false) {
                DashboardPage.entries.forEach { page ->
                    MiuixNavigationBarItem(selected = page == selected, onClick = { onSelect(page) },
                        icon = if (page == selected) page.selectedIcon else page.icon, label = page.label,
                        modifier = Modifier.testTag("nav-${page.name}"))
                }
            }
        }
    }
}
