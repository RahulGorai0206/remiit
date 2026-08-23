package com.rahulgorai.remiit.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Follow the system setting, or override it. */
enum class ThemeMode { AUTO, LIGHT, DARK }

/**
 * Which mechanism watches for other apps being opened.
 *
 * Android gives no ordinary API for this, and the two available routes trade
 * off against each other rather than one being strictly better, so the choice
 * is the user's:
 *
 * - [ACCESSIBILITY] is instant and costs almost no battery, but needs an
 *   accessibility grant and is against Google Play policy for this use.
 * - [USAGE_STATS] is policy-safe and needs only the usage-access grant, but
 *   polls — so it lags by around a second and keeps a foreground service alive.
 */
enum class AppLaunchDetectorKind { ACCESSIBILITY, USAGE_STATS }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "remiit_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val APP_LAUNCH_DETECTOR = stringPreferencesKey("app_launch_detector")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KNOWN_SSIDS = stringSetPreferencesKey("known_ssids")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.AUTO
    }

    /** Material You. On by default — following the device accent is the point. */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }

    val appLaunchDetector: Flow<AppLaunchDetectorKind> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_LAUNCH_DETECTOR]
            ?.let { runCatching { AppLaunchDetectorKind.valueOf(it) }.getOrNull() }
            ?: AppLaunchDetectorKind.ACCESSIBILITY
    }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /**
     * SSIDs the user has referenced before. The app never scans for networks, so
     * this plus the currently-connected SSID is the whole picker.
     */
    val knownSsids: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.KNOWN_SSIDS] ?: emptySet() }

    suspend fun setThemeMode(mode: ThemeMode) =
        edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) =
        edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setAppLaunchDetector(kind: AppLaunchDetectorKind) =
        edit { it[Keys.APP_LAUNCH_DETECTOR] = kind.name }

    suspend fun setOnboardingComplete(done: Boolean) =
        edit { it[Keys.ONBOARDING_DONE] = done }

    suspend fun rememberSsid(ssid: String) = edit { prefs ->
        if (ssid.isNotBlank()) {
            prefs[Keys.KNOWN_SSIDS] = (prefs[Keys.KNOWN_SSIDS] ?: emptySet()) + ssid
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
