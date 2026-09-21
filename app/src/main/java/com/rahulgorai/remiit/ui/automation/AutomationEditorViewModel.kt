package com.rahulgorai.remiit.ui.automation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.AutomationActions
import com.rahulgorai.remiit.data.model.AutomationEdge
import com.rahulgorai.remiit.data.model.AutomationTrigger
import com.rahulgorai.remiit.data.model.AutomationTriggerKind
import com.rahulgorai.remiit.data.model.SoundSetting
import com.rahulgorai.remiit.data.model.kind
import com.rahulgorai.remiit.data.prefs.SettingsStore
import com.rahulgorai.remiit.data.repo.AutomationRepository
import com.rahulgorai.remiit.engine.TriggerCoordinator
import com.rahulgorai.remiit.trigger.wifi.WifiNetworks
import com.rahulgorai.remiit.trigger.wifi.WifiScanState
import com.rahulgorai.remiit.trigger.wifi.scan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable form state for one automation.
 *
 * Held apart from [Automation] for the same reason [com.rahulgorai.remiit.ui.builder.RuleDraft]
 * is held apart from a rule: a half-finished edit must never be a row in the
 * database, because a row in the database is something the engine will act on.
 */
data class AutomationDraft(
    val id: String = "",
    val name: String = "",
    val isEnabled: Boolean = true,
    val trigger: AutomationTrigger = AutomationTrigger.Wifi("", AutomationEdge.ENTER),
    val actions: AutomationActions = AutomationActions(),
    val createdAtEpochMillis: Long = 0L,
) {
    /**
     * An automation needs a name, something to change, and a trigger that
     * actually names something. A Wi-Fi trigger with a blank SSID would save
     * happily and then match nothing forever.
     */
    val isValid: Boolean
        get() = name.isNotBlank() && actions.isNotEmpty && triggerIsComplete

    private val triggerIsComplete: Boolean
        get() = when (val t = trigger) {
            is AutomationTrigger.Wifi -> t.ssid.isNotBlank()
            is AutomationTrigger.Bluetooth -> t.address.isNotBlank()
            is AutomationTrigger.Location -> t.latitude != 0.0 || t.longitude != 0.0
        }

    fun toAutomation(): Automation = Automation(
        id = id,
        name = name.trim(),
        isEnabled = isEnabled,
        trigger = trigger,
        actions = actions,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    companion object {
        fun from(automation: Automation) = AutomationDraft(
            id = automation.id,
            name = automation.name,
            isEnabled = automation.isEnabled,
            trigger = automation.trigger,
            actions = automation.actions,
            createdAtEpochMillis = automation.createdAtEpochMillis,
        )
    }
}

class AutomationEditorViewModel(
    private val repository: AutomationRepository,
    private val coordinator: TriggerCoordinator,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _draft = MutableStateFlow(AutomationDraft())
    val draft: StateFlow<AutomationDraft> = _draft.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _wifiScan = MutableStateFlow(WifiScanState())
    val wifiScan: StateFlow<WifiScanState> = _wifiScan.asStateFlow()

    val knownSsids = settings.knownSsids

    private var loadedId: String? = null

    fun load(automationId: String?) {
        // Guard against reloading on recomposition, which would discard edits.
        if (automationId == loadedId) return
        loadedId = automationId

        if (automationId.isNullOrBlank()) {
            _draft.value = AutomationDraft()
            return
        }
        viewModelScope.launch {
            _loading.value = true
            repository.automation(automationId)?.let { _draft.value = AutomationDraft.from(it) }
            _loading.value = false
        }
    }

    fun setName(value: String) = _draft.update { it.copy(name = value) }

    fun setTrigger(trigger: AutomationTrigger) = _draft.update { it.copy(trigger = trigger) }

    /**
     * Switches which kind of thing is being watched.
     *
     * The edge carries across, because "when I *leave*" is the part of the
     * intent the user has already expressed — only the thing being left is
     * changing.
     */
    fun setTriggerKind(kind: AutomationTriggerKind) = _draft.update { draft ->
        if (draft.trigger.kind == kind) return@update draft
        val edge = draft.trigger.edge
        draft.copy(
            trigger = when (kind) {
                AutomationTriggerKind.WIFI -> AutomationTrigger.Wifi("", edge)
                AutomationTriggerKind.BLUETOOTH -> AutomationTrigger.Bluetooth("", "", edge)
                AutomationTriggerKind.LOCATION ->
                    AutomationTrigger.Location(0.0, 0.0, DEFAULT_RADIUS_METERS, "", edge)
            }
        )
    }

    fun setEdge(edge: AutomationEdge) = _draft.update { draft ->
        draft.copy(
            trigger = when (val t = draft.trigger) {
                is AutomationTrigger.Wifi -> t.copy(edge = edge)
                is AutomationTrigger.Bluetooth -> t.copy(edge = edge)
                is AutomationTrigger.Location -> t.copy(edge = edge)
            }
        )
    }

    /** Null clears the sound action, leaving the ringer untouched. */
    fun setSound(setting: SoundSetting?) =
        _draft.update { it.copy(actions = it.actions.copy(sound = setting)) }

    /** Null clears the brightness action. */
    fun setAutoBrightness(enabled: Boolean?) =
        _draft.update { it.copy(actions = it.actions.copy(autoBrightness = enabled)) }

    fun save(onSaved: () -> Unit) {
        val draft = _draft.value
        if (!draft.isValid) return

        viewModelScope.launch {
            val stored = repository.save(draft.toAutomation())
            // Arm it now rather than waiting for the observer, so the
            // automation is live by the time the editor closes.
            coordinator.onAutomationChanged()

            (stored.trigger as? AutomationTrigger.Wifi)?.let { settings.rememberSsid(it.ssid) }

            loadedId = stored.id
            _draft.value = AutomationDraft.from(stored)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val draft = _draft.value
        if (draft.id.isBlank()) {
            onDeleted()
            return
        }
        viewModelScope.launch {
            repository.delete(draft.id)
            coordinator.onAutomationChanged()
            onDeleted()
        }
    }

    /**
     * Fills the network picker, exactly as the rule builder does.
     * See [com.rahulgorai.remiit.trigger.wifi.scan].
     */
    fun scanNearbyWifi(context: Context) {
        if (_wifiScan.value.scanning) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            WifiNetworks.scan(appContext).collect { _wifiScan.value = it }
        }
    }

    private companion object {
        /** Play Services treats anything much tighter as noise; see MIN_RADIUS_METERS. */
        const val DEFAULT_RADIUS_METERS = 150f
    }
}
