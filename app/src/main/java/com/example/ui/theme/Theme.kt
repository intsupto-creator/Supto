package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MayraDarkColorScheme = darkColorScheme(
    primary = NeonPink,
    onPrimary = TextPrimary,
    primaryContainer = HotMagenta,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricCyan,
    onSecondary = Obsidian,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = ElectricCyan,
    tertiary = BrightGold,
    onTertiary = Obsidian,
    background = Obsidian,
    onBackground = TextPrimary,
    surface = DeepViolet,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,
    outline = TextTertiary,
    error = MutedRed,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Keep Mayra's custom neon aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = MayraDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Obsidian.toArgb()
            window.navigationBarColor = Obsidian.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
