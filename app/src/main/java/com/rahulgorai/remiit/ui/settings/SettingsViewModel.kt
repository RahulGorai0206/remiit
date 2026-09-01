package com.rahulgorai.remiit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahulgorai.remiit.data.prefs.SettingsStore
import com.rahulgorai.remiit.data.prefs.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val dynamicColor: Boolean = true,
)

class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        settings.themeMode,
        settings.dynamicColor,
    ) { theme, dynamic -> SettingsState(theme, dynamic) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { settings.setDynamicColor(enabled) }
}
