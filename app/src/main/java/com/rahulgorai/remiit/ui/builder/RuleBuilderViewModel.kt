package com.rahulgorai.remiit.ui.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahulgorai.remiit.data.model.DeliveryConfig
import com.rahulgorai.remiit.data.model.MatchMode
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.RuleConstraints
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.data.prefs.SettingsStore
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.engine.RuleEngine
import com.rahulgorai.remiit.engine.TriggerCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Editable form state for a rule.
 *
 * Held separately from [ReminderRule] so an in-progress edit is never a
 * half-valid rule in the database — nothing is persisted until Save, and
 * [isValid] gates that.
 */
data class RuleDraft(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val isEnabled: Boolean = true,
    val triggers: List<Trigger> = emptyList(),
    val match: MatchMode = MatchMode.ANY,
    val delivery: DeliveryConfig = DeliveryConfig(),
    val constraints: RuleConstraints = RuleConstraints(),
    val createdAtEpochMillis: Long = 0L,
) {
    /** A rule with no title or no trigger can never do anything useful. */
    val isValid: Boolean get() = title.isNotBlank() && triggers.isNotEmpty()

    fun toRule(): ReminderRule = ReminderRule(
        id = id,
        title = title.trim(),
        body = body.trim(),
        isEnabled = isEnabled,
        triggers = triggers,
        match = match,
        delivery = delivery,
        constraints = constraints,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    companion object {
        fun from(rule: ReminderRule) = RuleDraft(
            id = rule.id,
            title = rule.title,
            body = rule.body,
            isEnabled = rule.isEnabled,
            triggers = rule.triggers,
            match = rule.match,
            delivery = rule.delivery,
            constraints = rule.constraints,
            createdAtEpochMillis = rule.createdAtEpochMillis,
        )
    }
}

class RuleBuilderViewModel(
    private val repository: RuleRepository,
    private val coordinator: TriggerCoordinator,
    private val engine: RuleEngine,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _draft = MutableStateFlow(RuleDraft())
    val draft: StateFlow<RuleDraft> = _draft.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loadedId: String? = null

    fun load(ruleId: String?) {
        // Guard against reloading on recomposition, which would discard edits.
        if (ruleId == loadedId) return
        loadedId = ruleId

        if (ruleId.isNullOrBlank()) {
            _draft.value = RuleDraft()
            return
        }
        viewModelScope.launch {
            _loading.value = true
            repository.rule(ruleId)?.let { _draft.value = RuleDraft.from(it) }
            _loading.value = false
        }
    }

    fun setTitle(value: String) = _draft.update { it.copy(title = value) }
    fun setBody(value: String) = _draft.update { it.copy(body = value) }
    fun setMatch(value: MatchMode) = _draft.update { it.copy(match = value) }
    fun setDelivery(value: DeliveryConfig) = _draft.update { it.copy(delivery = value) }
    fun setConstraints(value: RuleConstraints) = _draft.update { it.copy(constraints = value) }

    /** Adds a new trigger, or replaces the existing one with the same id. */
    fun upsertTrigger(trigger: Trigger) = _draft.update { draft ->
        val existing = draft.triggers.indexOfFirst { it.id == trigger.id }
        val triggers = if (existing >= 0) {
            draft.triggers.toMutableList().apply { set(existing, trigger) }
        } else {
            draft.triggers + trigger
        }
        draft.copy(triggers = triggers)
    }

    fun removeTrigger(triggerId: String) = _draft.update { draft ->
        draft.copy(triggers = draft.triggers.filterNot { it.id == triggerId })
    }

    fun save(onSaved: () -> Unit) {
        val draft = _draft.value
        if (!draft.isValid) return

        viewModelScope.launch {
            val stored = repository.save(draft.toRule())
            // Re-register immediately rather than waiting for the rule-table
            // observer, so a saved rule is armed by the time the screen closes.
            coordinator.onRuleSaved(stored)

            // Remember typed SSIDs so the picker can offer them next time; the
            // app never scans for networks.
            stored.wifiTriggers.forEach { settings.rememberSsid(it.ssid) }

            loadedId = stored.id
            _draft.value = RuleDraft.from(stored)
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
            repository.rule(draft.id)?.let { coordinator.onRuleDeleted(it) }
            repository.delete(draft.id)
            onDeleted()
        }
    }

    /**
     * Fires the draft immediately so the user can feel the delivery mode before
     * committing to it. Saved first because delivery needs a persisted rule and
     * a history row to attribute the response to.
     */
    fun preview() {
        val draft = _draft.value
        if (!draft.isValid) return
        viewModelScope.launch {
            val stored = repository.save(draft.toRule())
            loadedId = stored.id
            _draft.value = RuleDraft.from(stored)
            engine.previewNow(stored)
        }
    }

    fun newTriggerId(): String = UUID.randomUUID().toString()
}
