package org.homeguard.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HomeGuardColorScheme = darkColorScheme(
    primary = ActionPrimary,
    onPrimary = TextPrimary,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    error = StatusEmergency,
    onError = TextPrimary
)

/**
 * HomeGuard renders a single dark scheme.
 *
 * The previous implementation offered a light scheme that was identical to the dark one, which
 * meant the `darkTheme` flag did nothing. A calm, consistent dark surface suits night use and
 * avoids a bright panel waking someone at 3am.
 */
@Composable
fun HomeGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // statusBarColor / navigationBarColor are deprecated in API 35. Draw edge to edge
                // instead; screens apply their own status bar padding.
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = HomeGuardColorScheme,
        typography = HomeGuardTypography,
        content = content
    )
}