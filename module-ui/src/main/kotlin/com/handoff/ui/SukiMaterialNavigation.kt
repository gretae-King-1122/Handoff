// SPDX-License-Identifier: GPL-3.0-only
// Adapted from SukiSU-Ultra's BottomBarMaterial.kt, commit 8e89c3797b91b957b15d1760fc57591adfecc563.
// Copyright SukiSU-Ultra contributors. Changes: Handoff destinations/callbacks, Haze glass,
// reduced-motion handling, accessibility tags and large-font fallback. See LICENSING.md.
package com.handoff.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Upstream's 64dp floating pill, with the selected item's label expanding inline. */
@Composable internal fun SukiMaterialNavigation(selected: DashboardPage, onSelect: (DashboardPage) -> Unit) {
    BoxWithConstraints(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        // Preserve every destination at accessibility font sizes, where the inline pill won't fit.
        val largeFont = LocalDensity.current.fontScale > 1.8f ||
            (maxWidth < 300.dp && LocalDensity.current.fontScale > 1.3f)
        Surface(Modifier.fillMaxWidth().testTag("navigation").clip(CircleShape).frostedGlass(),
            shape = CircleShape, color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
            if (largeFont) {
                NavigationBar(containerColor = Color.Transparent, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    DashboardPage.entries.forEach { page ->
                        NavigationBarItem(selected = page == selected, onClick = { onSelect(page) },
                            modifier = Modifier.testTag("nav-${page.name}"),
                            icon = { Icon(if (page == selected) page.selectedIcon else page.icon, null) },
                            label = { Text(page.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    DashboardPage.entries.forEach { page ->
                        SukiFloatingNavItem(page, selected == page) { onSelect(page) }
                    }
                }
            }
        }
    }
}

@Composable private fun SukiFloatingNavItem(page: DashboardPage, selected: Boolean, onClick: () -> Unit) {
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = motionSpec(220), label = "nav_item_container")
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionSpec(220), label = "nav_item_content")
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(Modifier.height(48.dp).clip(CircleShape).background(containerColor)
            .testTag("nav-${page.name}").selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .animateContentSize(animationSpec = motionSpec(240))
            .padding(horizontal = if (selected) 16.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (selected) page.selectedIcon else page.icon,
                contentDescription = if (selected) null else page.label, modifier = Modifier.size(24.dp))
            AnimatedVisibility(selected,
                enter = fadeIn(motionSpec(180)) + expandHorizontally(motionSpec(240)),
                exit = fadeOut(motionSpec(120)) + shrinkHorizontally(motionSpec(200))) {
                Text(page.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
