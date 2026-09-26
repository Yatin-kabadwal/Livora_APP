package com.livora.corbett.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.view.View
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Forest950,
    primaryContainer = Forest700,
    onPrimaryContainer = GoldLight,
    secondary = Sage,
    onSecondary = Forest950,
    secondaryContainer = Forest800,
    onSecondaryContainer = Cream,
    tertiary = Ember,
    onTertiary = Forest950,
    background = Forest950,
    onBackground = Cream,
    surface = Forest900,
    onSurface = Cream,
    surfaceVariant = Forest800,
    onSurfaceVariant = Color(0xFFB9CBBD),
    surfaceTint = Gold,
    outline = Color(0xFF3F5F4E),
    outlineVariant = Color(0xFF24402F),
    error = Color(0xFFFF8A7A),
    onError = Forest950,
    errorContainer = Color(0xFF4A1E18),
    onErrorContainer = Color(0xFFFFDAD4),
    surfaceContainerLowest = Forest950,
    surfaceContainerLow = Color(0xFF0A1710),
    surfaceContainer = Forest900,
    surfaceContainerHigh = Forest800,
    surfaceContainerHighest = Forest700,
    inverseSurface = Cream,
    inverseOnSurface = Forest950,
    inversePrimary = Brand.goldDeep,
)

private val LightScheme = lightColorScheme(
    primary = Forest700,
    onPrimary = Cream,
    primaryContainer = Color(0xFFD5E6DA),
    onPrimaryContainer = Forest900,
    secondary = Brand.goldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E7C6),
    onSecondaryContainer = Color(0xFF3A2C08),
    tertiary = Color(0xFFB4501B),
    onTertiary = Color.White,
    background = Cream,
    onBackground = Forest900,
    surface = Color(0xFFFBF8EF),
    onSurface = Forest900,
    surfaceVariant = Color(0xFFE7E1CF),
    onSurfaceVariant = Color(0xFF4A5C50),
    surfaceTint = Forest700,
    outline = Color(0xFF7C8B80),
    outlineVariant = Color(0xFFCFC9B6),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F3E6),
    surfaceContainer = Color(0xFFF1ECDC),
    surfaceContainerHigh = Color(0xFFEAE4D2),
    surfaceContainerHighest = Color(0xFFE3DDCA),
    inverseSurface = Forest900,
    inverseOnSurface = Cream,
    inversePrimary = Gold,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** True when the system "remove animations" (animator duration scale 0) setting is on. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Base URL used to resolve site-relative image paths (from /settings/public). */
val LocalAssetBase = staticCompositionLocalOf<String?> { null }

val LocalDarkTheme = staticCompositionLocalOf { true }

private fun applyBarIcons(view: View, darkTheme: Boolean) {
    val window = (view.context as? Activity)?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)
    controller.isAppearanceLightStatusBars = !darkTheme
    controller.isAppearanceLightNavigationBars = !darkTheme
}

@Composable
fun CorbettTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect { applyBarIcons(view, darkTheme) }
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Forces the dark palette for a subtree (screens drawn over the forest backdrop). */
@Composable
fun ForceDark(content: @Composable () -> Unit) {
    val outer = LocalDarkTheme.current
    val view = LocalView.current
    DisposableEffect(outer) {
        if (!view.isInEditMode) applyBarIcons(view, true)
        onDispose { if (!view.isInEditMode) applyBarIcons(view, outer) }
    }
    CompositionLocalProvider(LocalDarkTheme provides true) {
        MaterialTheme(colorScheme = DarkScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

@Composable
fun resolveDark(mode: String): Boolean = when (mode) {
    "light" -> false
    "system" -> isSystemInDarkTheme()
    else -> true
}
