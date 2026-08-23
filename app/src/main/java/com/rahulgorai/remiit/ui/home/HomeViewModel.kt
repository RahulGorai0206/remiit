package com.rahulgorai.remiit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.engine.RuleEngine
import com.rahulgorai.remiit.engine.TriggerCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

data class RuleCardState(
    val rule: ReminderRule,
    /** Next scheduled fire, when the rule has a time trigger. */
    val nextFire: Instant?,
)

class HomeViewModel(
    private val repository: RuleRepository,
    private val coordinator: TriggerCoordinator,
    private val engine: RuleEngine,
    private val clock: Clock,
) : ViewModel() {

    val rules: StateFlow<List<RuleCardState>> = repository.observeRules()
        .map { rules ->
            val now = clock.instant()
            rules.map { RuleCardState(it, it.nextTimeFire(now, clock.zone)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(rule: ReminderRule, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(rule.id, enabled)
            // Registrations follow the database, so the coordinator has to be
            // told to re-apply — otherwise a disabled rule keeps its alarms.
            coordinator.onRuleSaved(rule.copy(isEnabled = enabled))
        }
    }

    fun delete(rule: ReminderRule) {
        viewModelScope.launch {
            repository.delete(rule.id)
            coordinator.onRuleDeleted(rule)
        }
    }

    /** Fires the rule right now, bypassing constraints, so the user can feel it. */
    fun preview(rule: ReminderRule) {
        viewModelScope.launch { engine.previewNow(rule) }
    }
}
