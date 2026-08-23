package com.rahulgorai.remiit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahulgorai.remiit.data.prefs.AppLaunchDetectorKind
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
    val detector: AppLaunchDetectorKind = AppLaunchDetectorKind.ACCESSIBILITY,
)

class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        settings.themeMode,
        settings.dynamicColor,
        settings.appLaunchDetector,
    ) { theme, dynamic, detector -> SettingsState(theme, dynamic, detector) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { settings.setDynamicColor(enabled) }

    /**
     * Switching detector takes effect without a restart: the coordinator watches
     * this preference and starts or stops the monitor service accordingly.
     */
    fun setDetector(kind: AppLaunchDetectorKind) =
        viewModelScope.launch { settings.setAppLaunchDetector(kind) }
}
