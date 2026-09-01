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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "remiit_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KNOWN_SSIDS = stringSetPreferencesKey("known_ssids")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.AUTO
    }

    /** Material You. On by default — following the device accent is the point. */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /**
     * SSIDs the user has referenced before. Joined with the networks in range
     * and the one currently connected to build the rule builder's picker.
     */
    val knownSsids: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.KNOWN_SSIDS] ?: emptySet() }

    suspend fun setThemeMode(mode: ThemeMode) =
        edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) =
        edit { it[Keys.DYNAMIC_COLOR] = enabled }

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
