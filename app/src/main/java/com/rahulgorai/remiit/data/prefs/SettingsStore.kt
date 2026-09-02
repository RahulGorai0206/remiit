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
import kotlinx.coroutines.flow.onEach

/** Follow the system setting, or override it. */
enum class ThemeMode { AUTO, LIGHT, DARK }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "remiit_settings")

class SettingsStore(private val context: Context) {

    /**
     * Last values seen by a collector, readable synchronously.
     *
     * The reminder overlay is built from a plain window rather than an Activity,
     * so there is no lifecycle scope to await a DataStore read in and no way to
     * suspend on the delivery path. These are updated as the flows below emit,
     * which covers every case where the app has been opened this process. A
     * process started cold by an alarm falls back to the defaults, which is the
     * right failure: a reminder shown in the system theme beats one delayed to
     * look up a colour.
     */
    @Volatile
    var themeModeSnapshot: ThemeMode = ThemeMode.AUTO
        private set

    @Volatile
    var dynamicColorSnapshot: Boolean = true
        private set

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KNOWN_SSIDS = stringSetPreferencesKey("known_ssids")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.AUTO
    }.onEach { themeModeSnapshot = it }

    /** Material You. On by default — following the device accent is the point. */
    val dynamicColor: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
            .onEach { dynamicColorSnapshot = it }

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
