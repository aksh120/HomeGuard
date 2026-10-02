package org.homeguard.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ActionPrimary,
    onPrimary = TextPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = StatusEmergency,
    onError = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = ActionPrimary,
    onPrimary = TextPrimary,
    background = DarkBackground, // Dark theme is preferred for calm contrast & battery efficiency
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = StatusEmergency,
    onError = TextPrimary
)

@Composable
fun HomeGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // statusBarColor / navigationBarColor are deprecated in API 35. Draw edge to edge
                // instead; every screen already applies statusBarsPadding()/navigationBarsPadding().
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HomeGuardTypography,
        content = content
    )
}
