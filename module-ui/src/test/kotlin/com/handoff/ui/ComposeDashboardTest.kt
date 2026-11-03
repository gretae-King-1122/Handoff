package com.handoff.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.handoff.api.health.HealthEvent
import com.handoff.api.health.HealthPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** These pixels come from the Compose tree, not an HTML approximation or a device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-notnight-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeDashboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val offline = DashboardState(snapshot = DashboardSnapshot(checked = true))
    private val noop = object : DashboardActions {}

    private fun show(state: DashboardState = offline, actions: DashboardActions = noop) {
        compose.setContent { HandoffDashboard(state, actions) }
        compose.waitForIdle()
    }
    private fun navigate(page: DashboardPage) {
        compose.onNodeWithTag("nav-${page.name}").performClick()
        compose.onNodeWithTag("page-${page.name}").assertIsDisplayed()
    }
    private fun settingsItem(tag: String) {
        scrollToControl("page-SETTINGS", tag)
    }
    private fun scrollToControl(pageTag: String, tag: String) {
        val list = compose.onNodeWithTag(pageTag)
        list.performScrollToNode(hasTestTag(tag))
        val control = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        val overlap = (control.bottom - navigation.top + 16f).coerceAtLeast(0f)
        if (overlap > 0f) list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, overlap) }
    }
    private fun screenshot(name: String, sheetTag: String? = null) {
        compose.waitForIdle()
        val output = File("build/outputs/compose-preview/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { stream ->
            val activity = compose.onNode(isRoot() and hasAnyDescendant(hasTestTag("navigation")))
            val base = activity.captureToImage().asAndroidBitmap()
            val bitmap = if (sheetTag == null) base else {
                // A root sits above IsDialog, so captureToImage(root) picks the Activity
                // window. Capture the actual dialog and composite the two full-screen
                // windows, including the dialog's own scrim, as Android would display them.
                val overlay = compose.onNode(isDialog() and hasAnyDescendant(hasTestTag(sheetTag)))
                    .captureToImage().asAndroidBitmap()
                check(base.width == overlay.width && base.height == overlay.height)
                assertTrue("Glass sheet must retain a readable surface when source replay is unavailable",
                    android.graphics.Color.alpha(overlay.getPixel(overlay.width / 2, overlay.height - 4)) >= 220)
                base.copy(Bitmap.Config.ARGB_8888, true).also { Canvas(it).drawBitmap(overlay, 0f, 0f, null) }
            }
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    @Test fun fourPagesUseAccessibleSelectedTabsAndRestorePage() {
        val restore = StateRestorationTester(compose)
        restore.setContent { HandoffDashboard(offline, noop) }
        DashboardPage.entries.forEach { page ->
            navigate(page)
            compose.onNodeWithTag("nav-${page.name}").assertIsSelected()
        }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("page-SETTINGS").assertIsDisplayed()
        compose.onNodeWithTag("nav-SETTINGS").assertIsSelected()
    }

    @Test fun switchingStylesChangesNavigationLayoutWithoutLosingTheCurrentPage() {
        val value = mutableStateOf(offline.copy(reduceMotion = true))
        compose.setContent { HandoffDashboard(value.value, noop) }
        navigate(DashboardPage.SETTINGS)
        val floating = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Material bar floats away from the screen edge", floating.left > 0f)
        compose.runOnIdle { value.value = value.value.copy(style = UiStyle.MIUIX) }
        compose.onNodeWithTag("page-SETTINGS").assertIsDisplayed()
        compose.onNodeWithTag("nav-SETTINGS").assertIsSelected()
        val flat = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, flat.left, 1f)
        assertTrue("Miuix uses the full-width navigation layout", flat.width > floating.width)
        compose.onNodeWithTag("reduce-motion").assertIsOn().assertIsEnabled()
        navigate(DashboardPage.HOME)
        scrollToControl("page-HOME", "destination-douyin")
        compose.onNodeWithTag("destination-douyin").assertIsDisplayed().performClick()
        compose.onNodeWithText("前往交接设置").performClick()
        compose.onNodeWithTag("page-SETTINGS").assertIsDisplayed()
        compose.runOnIdle { value.value = value.value.copy(style = UiStyle.MATERIAL) }
        compose.onNodeWithTag("nav-SETTINGS").assertIsSelected()
        compose.onNodeWithTag("reduce-motion").assertIsOn()
    }

    @Test fun liquidNavigationDragSelectsTheDestinationOnRelease() {
        show()
        compose.onNodeWithTag("navigation").performTouchInput {
            swipe(start = androidx.compose.ui.geometry.Offset(width * 0.125f, height / 2f),
                end = androidx.compose.ui.geometry.Offset(width * 0.875f, height / 2f), durationMillis = 350)
        }
        compose.onNodeWithTag("page-SETTINGS").assertIsDisplayed()
        compose.onNodeWithTag("nav-SETTINGS").assertIsSelected()
    }

    @Test fun unconnectedSettingsAreReadOnlyAndPilotsRemainOff() {
        var writes = 0
        show(actions = object : DashboardActions { override fun setSetting(key: String, enabled: Boolean) { writes++ } })
        navigate(DashboardPage.SETTINGS)
        settingsItem("setting-enabled"); compose.onNodeWithTag("setting-enabled").assertIsNotEnabled()
        settingsItem("setting-default_browser"); compose.onNodeWithTag("setting-default_browser").assertIsOn().assertIsNotEnabled()
        listOf("douyin", "kuaishou").forEach { key ->
            settingsItem("setting-$key")
            compose.onNodeWithTag("setting-$key").assertIsOff().assertIsNotEnabled()
        }
        assertEquals(0, writes)
    }

    @Test fun readableSettingsDispatchIndependentKeysAndDisablePendingWrites() {
        val writes = mutableListOf<Pair<String, Boolean>>()
        show(offline.copy(snapshot = offline.snapshot.copy(settingsReadable = true, settings = mapOf("default_browser" to true, "douyin" to true, "kuaishou" to false)), pendingSettings = setOf("enabled")),
            object : DashboardActions { override fun setSetting(key: String, enabled: Boolean) { writes += key to enabled } })
        navigate(DashboardPage.SETTINGS)
        settingsItem("setting-enabled"); compose.onNodeWithTag("setting-enabled").assertIsNotEnabled()
        settingsItem("setting-default_browser"); compose.onNodeWithTag("setting-default_browser").assertIsOn().performClick()
        settingsItem("setting-douyin"); compose.onNodeWithTag("setting-douyin").assertIsOn().performClick()
        settingsItem("setting-kuaishou"); compose.onNodeWithTag("setting-kuaishou").assertIsOff().performClick()
        assertEquals(listOf("default_browser" to false, "douyin" to false, "kuaishou" to true), writes)
    }

    @Test fun appearanceSheetWorksWithoutFrameworkAndKeepsCurrentPage() {
        val value = mutableStateOf(offline)
        val actions = object : DashboardActions { override fun setStyle(style: UiStyle) { value.value = value.value.copy(style = style) } }
        compose.setContent { HandoffDashboard(value.value, actions) }
        navigate(DashboardPage.SETTINGS)
        compose.onNodeWithTag("appearance-style").performClick()
        screenshot("appearance-sheet-before", "appearance-options")
        compose.onNodeWithTag("style-miuix").performClick()
        compose.onNodeWithTag("style-miuix").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已选择"))
        compose.onNodeWithText("选一个喜欢的风格").assertIsDisplayed()
        assertEquals(UiStyle.MIUIX, value.value.style)
        screenshot("appearance-sheet", "appearance-options")
    }

    @Test fun platformSheetShowsUnknownStateAndNavigatesToSettings() {
        show()
        scrollToControl("page-HOME", "destination-douyin")
        compose.onNodeWithTag("destination-douyin").assertIsDisplayed().performClick()
        screenshot("destination-sheet", "destination-sheet")
        compose.onNodeWithText("国内版 App").assertIsDisplayed()
        compose.onNodeWithText("前往交接设置").performClick()
        compose.onNodeWithTag("page-SETTINGS").assertIsDisplayed()
    }

    @Test fun confirmedActivationAndDisabledHandoffStaySeparate() {
        show(offline.copy(snapshot = offline.snapshot.copy(framework = FrameworkSnapshot(true, "LSPosed", "2.2.1", 1, 102, true, true, FrameworkSnapshot.RuntimeState.CURRENT),
            settingsReadable = true, settings = mapOf("enabled" to false))))
        compose.onNodeWithText("已在 QQ 激活").assertIsDisplayed()
        compose.onNodeWithText("链接跳转 · 已关闭").assertIsDisplayed()
    }

    @Test fun startRoundNeedsExplicitConfirmationAndCancelDoesNothing() {
        var starts = 0
        show(actions = object : DashboardActions { override fun startRun() { starts++ } })
        navigate(DashboardPage.REGRESSION)
        compose.onNodeWithTag("start-run").performClick()
        compose.onNodeWithText("取消").performClick(); assertEquals(0, starts)
        compose.onNodeWithTag("start-run").performClick()
        compose.onNodeWithTag("confirm-action").performClick(); assertEquals(1, starts)
    }

    @Test fun logsUsePlainLanguageInBothStylesAndKeepTestConfirmation() {
        val value = mutableStateOf(offline.copy(reduceMotion = true))
        compose.setContent { HandoffDashboard(value.value, noop) }
        UiStyle.entries.forEach { style ->
            compose.runOnIdle { value.value = value.value.copy(style = style) }
            navigate(DashboardPage.REGRESSION)
            compose.onNodeWithTag("nav-REGRESSION").assertTextContains("日志")
            compose.onAllNodesWithText("回归", substring = true).assertCountEquals(0)
            compose.onNodeWithTag("start-run").assertTextContains("新建测试记录").performClick()
            compose.onNodeWithText("新建测试记录？").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
        }
    }

    @Test fun settingsDoNotExposeInternalHookContractInEitherStyle() {
        val value = mutableStateOf(offline.copy(reduceMotion = true))
        compose.setContent { HandoffDashboard(value.value, noop) }
        UiStyle.entries.forEach { style ->
            compose.runOnIdle { value.value = value.value.copy(style = style) }
            navigate(DashboardPage.SETTINGS)
            // Search the entire lazy list, including content initially off screen.
            assertThrows(AssertionError::class.java) {
                compose.onNodeWithTag("page-SETTINGS").performScrollToNode(hasTestTag("expand-contract"))
            }
            settingsItem("expand-adapters")
            compose.onNodeWithTag("expand-adapters").assertExists()
            settingsItem("open-about")
            compose.onNodeWithTag("open-about").assertIsDisplayed()
            compose.onAllNodesWithText("契约", substring = true).assertCountEquals(0)
        }
    }

    @Test fun aboutUsesTheOriginalHandoffLauncherArtwork() {
        val bytes = compose.activity.resources.openRawResource(R.drawable.handoff_brand).use { it.readBytes() }
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        // Exact bytes of the original xxxhdpi launcher icon, not a redesigned mark.
        assertEquals("9f24f758417653c58136968239e2bccc0e9c04761247c43556d2ecf1a29dd133", digest)
        show()
        navigate(DashboardPage.SETTINGS)
        settingsItem("open-about")
        compose.onNodeWithTag("open-about").performClick()
        compose.onNodeWithTag("about-app-icon").assertIsDisplayed()
        compose.onNodeWithContentDescription("Handoff 应用图标").assertIsDisplayed()
    }

    @Test fun positiveRegressionResultRequiresASeparateConfirmation() {
        val snapshot = offline.snapshot.copy(qqCode = 16580)
        val run = RegressionRun(1, snapshot.environment.key(), 16580, 35, "preview", "unknown", 0, emptyMap())
        val results = mutableListOf<Pair<HealthPolicy.Case, String>>()
        show(offline.copy(snapshot = snapshot.copy(runs = listOf(run))), object : DashboardActions { override fun mark(test: HealthPolicy.Case, result: String) { results += test to result } })
        navigate(DashboardPage.REGRESSION)
        compose.onNodeWithTag("page-REGRESSION").performScrollToNode(hasTestTag("pass-WEB"))
        compose.onNodeWithTag("pass-WEB").performClick()
        assertTrue(results.isEmpty())
        compose.onNodeWithTag("confirm-action").performClick()
        assertEquals(listOf(HealthPolicy.Case.WEB to "PASS"), results)
    }

    @Test fun signalFiltersShowOnlyMatchingEvents() {
        val events = listOf(
            HealthPolicy.Evidence(1, 1, 1, HealthEvent(HealthEvent.Kind.ACCEPTED, HealthEvent.Entry.BROWSER, HealthEvent.Reason.NONE), "unknown"),
            HealthPolicy.Evidence(2, 1, 1, HealthEvent(HealthEvent.Kind.INSTALL_FAILED, HealthEvent.Entry.SDK, HealthEvent.Reason.NONE), "unknown"),
        )
        show(offline.copy(snapshot = offline.snapshot.copy(evidence = events)))
        navigate(DashboardPage.DIAGNOSTICS)
        compose.onNodeWithTag("page-DIAGNOSTICS").performScrollToNode(hasTestTag("filter-accepted"))
        compose.onNodeWithTag("filter-accepted").performClick()
        compose.onNodeWithText("外跳请求已接受").assertExists()
        compose.onNodeWithText("目标签名挂载失败").assertDoesNotExist()
        compose.onNodeWithTag("filter-issues").performClick()
        compose.onNodeWithText("目标签名挂载失败").assertExists()
        compose.onNodeWithText("外跳请求已接受").assertDoesNotExist()
    }

    @Test fun refreshPreservesExpandedCardAndScrollPosition() {
        val value = mutableStateOf(offline)
        compose.setContent { HandoffDashboard(value.value, noop) }
        compose.onNodeWithTag("page-HOME").performScrollToNode(hasText("libxposed API"))
        compose.onNodeWithText("libxposed API").assertIsDisplayed()
        compose.runOnIdle { value.value = value.value.copy(snapshot = value.value.snapshot.copy(moduleName = "0.6.0")) }
        compose.onNodeWithText("libxposed API").assertIsDisplayed()
    }

    @Test fun informationCardsStartExpandedAndRememberManualCollapse() {
        val restore = StateRestorationTester(compose)
        restore.setContent { HandoffDashboard(offline, noop) }
        listOf("expand-framework", "expand-device").forEach { tag ->
            scrollToControl("page-HOME", tag)
            compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已展开"))
            compose.onNodeWithTag(tag).performClick()
        }
        navigate(DashboardPage.SETTINGS)
        navigate(DashboardPage.HOME)
        restore.emulateSavedInstanceStateRestore()
        listOf("expand-framework", "expand-device").forEach { tag ->
            scrollToControl("page-HOME", tag)
            compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已收起"))
        }
    }

    @Test fun aboutUsesInstalledVersionAndOpensProjectLinksWithoutLosingSettings() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                HandoffDashboard(offline.copy(snapshot = offline.snapshot.copy(moduleName = "9.8.7", moduleCode = 987)), noop)
            }
        }
        navigate(DashboardPage.SETTINGS)
        settingsItem("open-about")
        compose.onNodeWithTag("open-about").performClick()
        compose.onNodeWithTag("about-sheet").assertIsDisplayed()
        compose.onNodeWithText("v9.8.7 · 987").assertExists()
        listOf("about-project", "about-releases", "about-issues").forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().performClick()
        }
        assertEquals(listOf("https://github.com/gretae-King-1122/Handoff",
            "https://github.com/gretae-King-1122/Handoff/releases",
            "https://github.com/gretae-King-1122/Handoff/issues"), opened)
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog
            dialog.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("about-sheet").assertDoesNotExist()
        compose.onNodeWithTag("nav-SETTINGS").assertIsSelected()
    }

    @Test fun aboutShowsBrowserFailureAndCanScrollToDevelopmentDetails() {
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { throw android.content.ActivityNotFoundException() }
            }) { HandoffDashboard(offline, noop) }
        }
        navigate(DashboardPage.SETTINGS)
        settingsItem("open-about")
        compose.onNodeWithTag("open-about").performClick()
        screenshot("about-sheet", "about-sheet")
        compose.onNodeWithTag("about-project").performScrollTo().performClick()
        compose.onNodeWithTag("about-link-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("开发详情").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Jetpack Compose").assertExists()
    }

    @Test fun reduceMotionCanChangeWithoutRemoteSettings() {
        var reduced = false
        show(actions = object : DashboardActions { override fun setReduceMotion(reduce: Boolean) { reduced = reduce } })
        navigate(DashboardPage.SETTINGS)
        compose.onNodeWithTag("reduce-motion").performClick()
        assertTrue(reduced)
    }

    @Test fun rapidNavigationSettlesOnLastPageWithFullVisibility() {
        show(); compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-DIAGNOSTICS").performClick(); compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("nav-SETTINGS").performClick(); compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("page-SETTINGS").assertIsDisplayed()
        compose.onNodeWithTag("nav-SETTINGS").assertIsSelected()
        compose.onNodeWithTag("page-DIAGNOSTICS").assertDoesNotExist()
        screenshot("motion-settled")
    }
    @Test fun reducedMotionSwitchesDirectlyToTheRestingPage() {
        show(offline.copy(reduceMotion = true)); compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-DIAGNOSTICS").performClick(); compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("page-DIAGNOSTICS").assertIsDisplayed()
        compose.onNodeWithTag("page-HOME").assertDoesNotExist()
    }
    @Test fun disabledSystemAnimationsNeverLeaveThePreviousPageVisible() {
        val animator = android.animation.ValueAnimator::class.java
        val scale = org.robolectric.util.ReflectionHelpers.callStaticMethod<Float>(animator, "getDurationScale")
        fun setScale(value: Float) = org.robolectric.util.ReflectionHelpers.callStaticMethod<Unit>(animator, "setDurationScale",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType!!, value))
        setScale(0f)
        try {
            show(); compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("nav-SETTINGS").performClick(); compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("page-SETTINGS").assertIsDisplayed()
            compose.onNodeWithTag("page-HOME").assertDoesNotExist()
        } finally { setScale(scale) }
    }
    @Test @Config(qualifiers = "w320dp-h640dp-notnight-xhdpi") fun veryLargeFontCanScrollThroughBothStyleOptions() {
        RuntimeEnvironment.setFontScale(2.5f)
        try {
            show(offline.copy(reduceMotion = true)); navigate(DashboardPage.SETTINGS)
            compose.onNodeWithTag("page-SETTINGS").performScrollToNode(hasTestTag("appearance-style"))
            compose.onNodeWithTag("appearance-style").performClick()
            compose.onNodeWithTag("style-miuix").performScrollTo().assertIsDisplayed()
            screenshot("appearance-large-font")
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    @Test fun renderLightPages() { renderPages("material-light") }
    @Test @Config(qualifiers = "w393dp-h851dp-night-xhdpi") fun renderDarkPages() { renderPages("material-dark") }
    @Test fun renderMiuixPages() { renderPages("miuix-light", UiStyle.MIUIX) }
    @Test @Config(qualifiers = "w393dp-h851dp-night-xhdpi") fun renderMiuixDarkPages() { renderPages("miuix-dark", UiStyle.MIUIX) }

    @Test @Config(qualifiers = "w320dp-h640dp-notnight-xhdpi") fun narrowViewportAndLargeFontUseStackedPlatformsAndKeepNavigationVisible() {
        RuntimeEnvironment.setFontScale(1.5f)
        try {
            show(offline.copy(reduceMotion = true))
            DashboardPage.entries.forEach { page -> navigate(page); compose.onNodeWithTag("nav-${page.name}").assertIsDisplayed() }
            screenshot("narrow-large-font")
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }
    @Test @Config(qualifiers = "w851dp-h393dp-land-xhdpi") fun landscapeKeepsNavigationAndScrollingUsable() {
        show(offline.copy(reduceMotion = true)); navigate(DashboardPage.SETTINGS)
        settingsItem("setting-kuaishou"); compose.onNodeWithTag("setting-kuaishou").assertIsDisplayed()
        compose.onNodeWithTag("nav-HOME").assertIsDisplayed(); screenshot("landscape")
    }
    @Test @Config(sdk = [26]) fun minimumAndroidOpensComposeHomeAndSettings() {
        val value = mutableStateOf(offline.copy(reduceMotion = true))
        compose.setContent { HandoffDashboard(value.value, noop) }
        UiStyle.entries.forEach { style ->
            compose.runOnIdle { value.value = value.value.copy(style = style) }
            navigate(DashboardPage.HOME)
            navigate(DashboardPage.SETTINGS)
            compose.onNodeWithTag("appearance-style").assertIsDisplayed()
            compose.onNodeWithTag("nav-SETTINGS").assertIsSelected()
        }
    }

    @Test fun exportPageTransitionFrames() {
        show(); screenshot("motion/home")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-DIAGNOSTICS").performClick()
        repeat(24) { frame -> compose.mainClock.advanceTimeBy(16); screenshot("motion/frame-${frame.toString().padStart(2, '0')}") }
        compose.mainClock.advanceTimeBy(800)
        screenshot("motion/diagnostics")
    }
    private fun renderPages(folder: String, style: UiStyle = UiStyle.MATERIAL) {
        show(offline.copy(style = style))
        DashboardPage.entries.forEach { page -> navigate(page); screenshot("$folder/${page.name.lowercase()}") }
    }
    @Test fun renderTimHomeAndKeepHostSelectionSeparate() {
        val value = mutableStateOf(offline.copy(snapshot = offline.snapshot.copy(
            hostPackage = "com.tencent.tim", qqCode = 4050, qqName = "4.1.0",
            hostVersions = mapOf("com.tencent.mobileqq" to "未安装", "com.tencent.tim" to "4.1.0"))))
        val actions = object : DashboardActions {
            override fun selectHost(packageName: String) {
                value.value = value.value.copy(snapshot = value.value.snapshot.copy(hostPackage = packageName))
            }
        }
        compose.setContent { HandoffDashboard(value.value, actions) }
        compose.onNodeWithTag("host-com.tencent.tim").assertIsSelected()
        compose.onNodeWithText("TIM · 4.1.0").assertIsDisplayed()
        screenshot("tim-home")
        compose.onNodeWithTag("host-com.tencent.mobileqq").performClick().assertIsSelected()
        assertEquals("com.tencent.mobileqq", value.value.snapshot.hostPackage)
        compose.onNodeWithTag("host-com.tencent.tim").performClick().assertIsSelected()
        assertEquals("com.tencent.tim", value.value.snapshot.hostPackage)
    }

}
