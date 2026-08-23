package com.rahulgorai.remiit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.rahulgorai.remiit.data.prefs.ThemeMode

/**
 * The app theme.
 *
 * Uses [MaterialExpressiveTheme] rather than `MaterialTheme`, which does two
 * things: it opts every component into its expressive variant, and it installs
 * the expressive [androidx.compose.material3.MotionScheme] as the default. That
 * second part is why animations across the app are spring-based without each
 * one asking — `MaterialTheme.motionScheme` is read by the components
 * themselves, and by [rememberSpatialSpec] for custom motion.
 *
 * The motion scheme is left at its default deliberately: `MotionScheme.expressive()`
 * is internal to material3, and the expressive theme already selects it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemiitTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        // minSdk is 33, so dynamic colour is available on every device the app
        // supports — no version guard needed, only the user's preference.
        dynamicColor ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> RemiitDarkScheme
        else -> RemiitLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Status/navigation bar icon colour has to follow the theme, not the
            // system setting: a manual light theme on a device in dark mode
            // otherwise gets white icons on a white bar.
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        shapes = RemiitShapes,
        typography = RemiitTypography,
        content = content,
    )
}
