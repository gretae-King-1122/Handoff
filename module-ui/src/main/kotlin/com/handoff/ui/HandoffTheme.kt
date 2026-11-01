package com.handoff.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColors
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColors

internal val LocalHandoffMotion = staticCompositionLocalOf { true }
internal val LocalHandoffStyle = staticCompositionLocalOf { UiStyle.MATERIAL }

@Composable internal fun <T> motionSpec(duration: Int = 280): FiniteAnimationSpec<T> =
    if (LocalHandoffMotion.current) tween(duration) else snap()

@Composable internal fun HandoffTheme(
    style: UiStyle, motion: Boolean = true, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit,
) {
    val miuix = style == UiStyle.MIUIX
    val colors = if (dark) darkColorScheme(
        primary = Color(if (miuix) 0xFF9CBFFF else 0xFFA8C7FA),
        onPrimary = Color(0xFF062E6F), primaryContainer = Color(if (miuix) 0xFF243D5F else 0xFF233D63),
        onPrimaryContainer = Color(0xFFD3E3FD), background = Color(if (miuix) 0xFF000000 else 0xFF111318),
        secondary = Color(0xFFA8C7FA), onSecondary = Color(0xFF062E6F),
        secondaryContainer = Color(0xFF233D63), onSecondaryContainer = Color(0xFFD3E3FD),
        surface = Color(if (miuix) 0xFF1C1C1E else 0xFF1B1C20), surfaceContainer = Color(0xFF25272D),
        surfaceContainerLow = Color(0xFF191B20),
        surfaceContainerLowest = Color(0xFF0C0E12), surfaceContainerHigh = Color(0xFF2B2D33),
        surfaceContainerHighest = Color(0xFF34363D), surfaceVariant = Color(0xFF44474E),
        outline = Color(0xFF8E918A),
        onSurface = Color(0xFFE3E3E8), onBackground = Color(0xFFE3E3E8), onSurfaceVariant = Color(0xFFC3C6CF),
        outlineVariant = Color(0xFF41454E), error = Color(0xFFF2B8B5),
    ) else lightColorScheme(
        primary = Color(if (miuix) 0xFF3478F6 else 0xFF1A73E8),
        onPrimary = Color.White, primaryContainer = Color(if (miuix) 0xFFE7EEFF else 0xFFD3E3FD),
        onPrimaryContainer = Color(0xFF174275),
        secondary = Color(0xFF1A73E8), onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3E3FD), onSecondaryContainer = Color(0xFF174275),
        background = Color(if (miuix) 0xFFF2F2F2 else 0xFFF7F9FC), surface = Color.White,
        surfaceContainer = Color(0xFFEDF2FA), surfaceContainerLow = Color(0xFFF1F4F9),
        surfaceContainerLowest = Color.White, surfaceContainerHigh = Color(0xFFE9EEF6),
        surfaceContainerHighest = Color(0xFFE8EAED), surfaceVariant = Color(0xFFE8EAED),
        outline = Color(0xFF747775),
        onSurface = Color(0xFF202124), onBackground = Color(0xFF202124), onSurfaceVariant = Color(0xFF5F6368),
        outlineVariant = Color(0xFFE1E5EC), error = Color(0xFFB3261E),
    )
    val type = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = if (miuix) FontWeight.SemiBold else FontWeight.Bold, fontSize = if (miuix) 32.sp else 34.sp, lineHeight = 42.sp, letterSpacing = (-1.2).sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp),
    )
    CompositionLocalProvider(LocalHandoffStyle provides style, LocalHandoffMotion provides motion) {
        MaterialTheme(colorScheme = colors, typography = type, shapes = Shapes(
            small = RoundedCornerShape(if (miuix) 12.dp else 16.dp),
            medium = RoundedCornerShape(if (miuix) 16.dp else 24.dp),
            large = RoundedCornerShape(if (miuix) 20.dp else 30.dp),
        )) {
            // Keep Miuix's indication/overscroll inside Material's host theme, so
            // Material does not replace the component library's native feedback.
            if (miuix) {
                val miuixColors = if (dark) miuixDarkColors(background = colors.background,
                    surface = colors.surface, surfaceContainer = colors.surface)
                else miuixLightColors(background = colors.background, surface = colors.surface,
                    surfaceContainer = colors.surface)
                MiuixTheme(colors = miuixColors, content = content)
            } else content()
        }
    }
}
