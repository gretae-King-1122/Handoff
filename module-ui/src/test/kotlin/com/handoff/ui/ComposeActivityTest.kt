package com.handoff.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The manifest entry, real ViewModel, local storage and Compose tree work together offline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-notnight-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeActivityTest {
    @get:Rule val compose = createAndroidComposeRule<DashboardActivity>()
    @Test fun launcherOpensComposeAndSettingsRemainReadOnlyWithoutFramework() {
        compose.onNodeWithTag("page-HOME").assertIsDisplayed()
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithTag("page-SETTINGS").assertIsDisplayed()
        compose.onNodeWithTag("page-SETTINGS").performScrollToNode(hasTestTag("setting-enabled"))
        compose.onNodeWithTag("setting-enabled").assertIsNotEnabled()
    }
    @Test fun manifestActivityCanBeRecreatedWithItsSelectedPage() {
        compose.onNodeWithTag("nav-DIAGNOSTICS").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("page-DIAGNOSTICS").assertIsDisplayed()
        compose.onNodeWithTag("nav-DIAGNOSTICS").assertIsSelected()
    }
    @Test @Config(sdk = [26]) fun androidEightOpensTheActualManifestActivity() {
        compose.onNodeWithTag("page-HOME").assertIsDisplayed()
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithTag("page-SETTINGS").assertIsDisplayed()
    }
}
