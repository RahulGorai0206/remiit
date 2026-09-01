package com.rahulgorai.remiit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulgorai.remiit.data.prefs.SettingsStore
import com.rahulgorai.remiit.data.prefs.ThemeMode
import com.rahulgorai.remiit.ui.RemiitApp
import com.rahulgorai.remiit.ui.theme.RemiitTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settings: SettingsStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transparent on both bars, explicitly.
        //
        // enableEdgeToEdge() looks like it just turns edge-to-edge on, but its
        // default navigation-bar style is a scrim — 50%-opaque #1b1b1b — not
        // transparent. Against a dark app that paints a black band behind the
        // gesture pill, which the theme's transparent navigationBarColor cannot
        // undo because this call is applied afterwards. Passing transparent
        // styles lets the app's own background run under the pill, so the pill
        // floats over the content instead of sitting in a letterbox.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Android 10+ can re-add its own contrast scrim behind the bars. The
        // theme asks for this too; setting it here covers the window after
        // enableEdgeToEdge has reconfigured it.
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false

        setContent {
            // Read as state rather than once at startup so flipping the theme in
            // Settings recomposes immediately instead of on next launch.
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.AUTO)
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(true)

            RemiitTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                RemiitApp()
            }
        }
    }
}
