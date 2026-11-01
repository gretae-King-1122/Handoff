package com.handoff.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-notnight-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardBackdropTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun capturedPageIncludesSurfaceInLightTheme() = assertCapturedSurface()

    @Test @Config(qualifiers = "w393dp-h851dp-night-xhdpi")
    fun capturedPageIncludesSurfaceInDarkTheme() = assertCapturedSurface()

    private fun assertCapturedSurface() {
        lateinit var liquid: LayerBackdrop
        var surface = 0
        compose.setContent {
            HandoffTheme(UiStyle.MATERIAL, true) {
                val haze = rememberHazeState()
                val backdrop = rememberLayerBackdrop()
                val background = MaterialTheme.colorScheme.background
                SideEffect { liquid = backdrop; surface = background.toArgb() }
                CompositionLocalProvider(LocalBackdrop provides haze) {
                    // Scaffold's own background is outside the recorded page source.
                    Box(Modifier.fillMaxSize().background(background)) {
                        Box(Modifier.fillMaxSize().dashboardBackdropSource(haze, backdrop)) {
                            Text("清除回归矩阵")
                        }
                        Box(Modifier.fillMaxWidth().height(56.dp).frostedGlass())
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        val pixels = runBlocking { liquid.graphicsLayer.toImageBitmap() }.asAndroidBitmap()
        // Sample empty page space, away from text, after both source pipelines have drawn.
        // A transparent input lets the combined indicator show sharp page text underneath
        // its translucent, blurred tab copy.
        listOf(1 to pixels.height / 2, pixels.width / 2 to pixels.height - 2).forEach { (x, y) ->
            assertEquals("The glass input must contain the opaque page surface", surface, pixels.getPixel(x, y))
        }
    }
}
