package com.handoff.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Offline, explicitly unconnected previews; no activation or successful handoff is simulated. */
@Preview(name = "Material light", widthDp = 393, heightDp = 851, showBackground = true)
@Preview(name = "Material dark", widthDp = 393, heightDp = 851, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable internal fun DashboardPreview() {
    HandoffDashboard(DashboardState(snapshot = DashboardSnapshot(checked = true), reduceMotion = true), object : DashboardActions {})
}

@Preview(name = "Miuix", widthDp = 393, heightDp = 851)
@Composable internal fun MiuixDashboardPreview() {
    HandoffDashboard(DashboardState(snapshot = DashboardSnapshot(checked = true), style = UiStyle.MIUIX, reduceMotion = true), object : DashboardActions {})
}
