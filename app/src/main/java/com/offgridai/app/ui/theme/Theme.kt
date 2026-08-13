package com.offgridai.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ══════════════════════════════════════════════════════
//  OffGrid AI — Material3 Light Color Scheme
//  Minimalist palette — NO dark theme (client preference)
// ══════════════════════════════════════════════════════

private val OffGridColorScheme = lightColorScheme(
    primary          = OffGridPrimary,
    onPrimary        = OffGridOnPrimary,
    primaryContainer = OffGridProgressBg,
    onPrimaryContainer = OffGridOnBackground,
    secondary        = OffGridText,
    onSecondary      = OffGridOnPrimary,
    secondaryContainer = OffGridSurfaceVar,
    onSecondaryContainer = OffGridOnBackground,
    background       = OffGridBackground,
    onBackground     = OffGridOnBackground,
    surface          = OffGridSurface,
    onSurface        = OffGridOnBackground,
    surfaceVariant   = OffGridSurfaceVar,
    onSurfaceVariant = OffGridText,
    outline          = OffGridSubtle,
    error            = OffGridError,
    onError          = OffGridOnPrimary,
    scrim            = OffGridScrim,
)

@Composable
fun OffGridAITheme(
    content: @Composable () -> Unit
) {
    val colorScheme = OffGridColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar matches background color
            window.statusBarColor = OffGridBackground.toArgb()
            // Navigation bar matches background color
            window.navigationBarColor = OffGridBackground.toArgb()
            // Dark icons on light background
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = OffGridTypography,
        content     = content
    )
}
