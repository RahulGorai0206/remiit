package com.rahulgorai.remiit.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahulgorai.remiit.data.model.ReminderEvent
import com.rahulgorai.remiit.data.model.ReminderOutcome
import com.rahulgorai.remiit.data.repo.RuleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryState(
    val events: List<ReminderEvent> = emptyList(),
    val completedCount: Int = 0,
    /** Reminders the user actually responded to, ignored ones excluded. */
    val answeredCount: Int = 0,
)

class HistoryViewModel(repository: RuleRepository) : ViewModel() {

    val state: StateFlow<HistoryState> = repository.observeRecentEvents()
        .map { events ->
            val answered = events.filter {
                it.outcome == ReminderOutcome.COMPLETED || it.outcome == ReminderOutcome.INCOMPLETE
            }
            HistoryState(
                events = events,
                completedCount = answered.count { it.outcome == ReminderOutcome.COMPLETED },
                answeredCount = answered.size,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}
