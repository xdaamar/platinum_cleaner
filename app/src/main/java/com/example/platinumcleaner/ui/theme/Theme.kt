package com.example.platinumcleaner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PlatinumPrimary,
    onPrimary = PlatinumOnPrimary,
    primaryContainer = PlatinumPrimary,
    onPrimaryContainer = PlatinumOnPrimary,
    surface = PlatinumSurface,
    onSurface = PlatinumOnSurface,
    surfaceVariant = PlatinumSurfaceVariant,
    onSurfaceVariant = PlatinumOnSurfaceVariant,
    background = PlatinumBackground,
    onBackground = PlatinumOnSurface,
    outline = PlatinumOutline,
    outlineVariant = PlatinumOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = PlatinumSurface,
    onPrimary = PlatinumPrimary,
    primaryContainer = PlatinumSurfaceDark,
    onPrimaryContainer = PlatinumOnSurfaceDark,
    surface = PlatinumSurfaceDark,
    onSurface = PlatinumOnSurfaceDark,
    surfaceVariant = PlatinumSurfaceDark,
    onSurfaceVariant = PlatinumOutlineVariant,
    background = PlatinumBackgroundDark,
    onBackground = PlatinumOnSurfaceDark,
    outline = PlatinumOutline,
    outlineVariant = PlatinumOutlineVariant
)

@Composable
fun PlatinumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = android.graphics.Color.TRANSPARENT
                it.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PlatinumTypography,
        content = content
    )
}
