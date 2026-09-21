package com.rahulgorai.remiit.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahulgorai.remiit.data.backup.BackupFiles
import com.rahulgorai.remiit.data.backup.BackupManager
import com.rahulgorai.remiit.data.backup.ImportResult
import com.rahulgorai.remiit.data.prefs.SettingsStore
import com.rahulgorai.remiit.data.prefs.ThemeMode
import com.rahulgorai.remiit.engine.TriggerCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val dynamicColor: Boolean = true,
)

/** The outcome of the last export or import, shown under the buttons. */
data class BackupStatus(val message: String, val isError: Boolean)

class SettingsViewModel(
    private val settings: SettingsStore,
    private val backup: BackupManager,
    private val coordinator: TriggerCoordinator,
) : ViewModel() {

    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()


    val state: StateFlow<SettingsState> = combine(
        settings.themeMode,
        settings.dynamicColor,
    ) { theme, dynamic -> SettingsState(theme, dynamic) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { settings.setDynamicColor(enabled) }

    /** The filename offered to the system save dialog. */
    fun suggestedFileName(): String = BackupFiles.suggestedName()

    fun exportTo(context: Context, uri: Uri) {
        val app = context.applicationContext
        viewModelScope.launch {
            _busy.value = true
            val json = backup.export()
            // Off the main thread: the destination may be a cloud provider, in
            // which case this write is a network call wearing a file's clothes.
            val written = withContext(Dispatchers.IO) { BackupFiles.write(app, uri, json) }
            _backupStatus.value = if (written) {
                BackupStatus("Backup saved.", isError = false)
            } else {
                BackupStatus("Could not write to that location. Try somewhere else.", isError = true)
            }
            _busy.value = false
        }
    }

    fun importFrom(context: Context, uri: Uri) {
        val app = context.applicationContext
        viewModelScope.launch {
            _busy.value = true
            val json = withContext(Dispatchers.IO) { BackupFiles.read(app, uri) }
            _backupStatus.value = if (json == null) {
                BackupStatus("Could not read that file.", isError = true)
            } else {
                when (val result = backup.import(json)) {
                    is ImportResult.Failure -> BackupStatus(result.reason, isError = true)
                    is ImportResult.Success -> {
                        // Arm everything that just arrived. Without this an
                        // imported rule sits in the database looking enabled
                        // with no alarm, geofence or monitor behind it until
                        // something else happens to trigger a reconcile.
                        coordinator.reconcileAll()
                        BackupStatus(result.describe(), isError = false)
                    }
                }
            }
            _busy.value = false
        }
    }

    fun dismissBackupStatus() {
        _backupStatus.value = null
    }
}

/** "4 rules and 2 automations restored." — counts, so a silent no-op is visible. */
private fun ImportResult.Success.describe(): String {
    if (total == 0) return "Nothing to restore."
    val parts = buildList {
        val ruleCount = rulesAdded + rulesUpdated
        val automationCount = automationsAdded + automationsUpdated
        if (ruleCount > 0) add("$ruleCount ${if (ruleCount == 1) "rule" else "rules"}")
        if (automationCount > 0) {
            add("$automationCount ${if (automationCount == 1) "automation" else "automations"}")
        }
    }
    val updated = rulesUpdated + automationsUpdated
    val tail = if (updated > 0) " ($updated already existed and were updated)" else ""
    return parts.joinToString(" and ") + " restored." + tail
}
