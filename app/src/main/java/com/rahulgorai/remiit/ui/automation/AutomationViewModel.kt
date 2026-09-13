package com.rahulgorai.remiit.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.repo.AutomationRepository
import com.rahulgorai.remiit.engine.TriggerCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the automation list. */
class AutomationViewModel(
    private val repository: AutomationRepository,
    private val coordinator: TriggerCoordinator,
) : ViewModel() {

    val automations: StateFlow<List<Automation>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(automation: Automation, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(automation.id, enabled)
            // Registrations follow the enabled flag, and the combined flow in
            // the coordinator would pick this up on its own — but only after
            // the database round-trip. Nudging it here means a geofence is
            // armed by the time the switch finishes animating.
            coordinator.onAutomationChanged()
        }
    }
}
