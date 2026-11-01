package com.handoff.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

internal val LocalBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** Both glass pipelines capture the same page, including its surface. */
@Composable internal fun Modifier.dashboardBackdropSource(haze: HazeState, liquid: LayerBackdrop?): Modifier =
    hazeSource(haze).then(liquid?.let { Modifier.layerBackdrop(it) } ?: Modifier)
        // A transparent page lets the combined liquid indicator expose a second,
        // sharp copy of the content underneath its translucent, blurred tab copy.
        .background(MaterialTheme.colorScheme.background)

/** Source-backed blur: only background pixels blur; labels and controls stay sharp. */
@Composable internal fun Modifier.frostedGlass(opacity: Float = 0.72f): Modifier {
    val backdrop = LocalBackdrop.current ?: return background(MaterialTheme.colorScheme.surface)
    val surface = MaterialTheme.colorScheme.surface
    val background = MaterialTheme.colorScheme.background
    val style = remember(surface, background, opacity) {
        HazeBlurStyle {
            blurEnabled(Build.VERSION.SDK_INT >= 31)
            blurRadius(24.dp)
            noiseFactor(0.008f)
            backgroundColor(background)
            colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = opacity))))
            // Android 8–11 and software canvases retain contrast without an unsupported blur pass.
            fallbackColorEffect(HazeColorEffect.tint(surface.copy(alpha = 0.96f)))
        }
    }
    // A dialog may draw before its cross-window source is ready. Keep text readable
    // during that frame, and on software renderers that cannot replay the source layer.
    return background(surface.copy(alpha = opacity)).hazeBlur(HazeInput.Sources(backdrop), style)
}
