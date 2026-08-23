package com.rahulgorai.remiit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        enableEdgeToEdge()

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
