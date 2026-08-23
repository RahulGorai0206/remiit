package com.rahulgorai.remiit.engine

import com.rahulgorai.remiit.data.db.ReminderEventDao
import com.rahulgorai.remiit.data.db.RuleDao
import com.rahulgorai.remiit.data.model.ReminderEvent
import com.rahulgorai.remiit.data.model.ReminderOutcome
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.delivery.ReminderDelivery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory DAOs so the engine is exercised through the real
 * [com.rahulgorai.remiit.data.repo.RuleRepository] rather than a mock of it —
 * the cooldown and daily-cap logic is expressed as DAO queries, so stubbing the
 * repository would test nothing.
 */
class FakeRuleDao : RuleDao {
    val rules = MutableStateFlow<List<ReminderRule>>(emptyList())

    override fun observeAll(): Flow<List<ReminderRule>> = rules
    override fun observeEnabled(): Flow<List<ReminderRule>> =
        rules.map { list -> list.filter { it.isEnabled } }

    override suspend fun getEnabled(): List<ReminderRule> = rules.value.filter { it.isEnabled }
    override suspend fun getById(id: String): ReminderRule? = rules.value.find { it.id == id }
    override fun observeById(id: String): Flow<ReminderRule?> =
        rules.map { list -> list.find { it.id == id } }

    override suspend fun upsert(rule: ReminderRule) {
        rules.value = rules.value.filterNot { it.id == rule.id } + rule
    }

    override suspend fun delete(rule: ReminderRule) = deleteById(rule.id)

    override suspend fun deleteById(id: String) {
        rules.value = rules.value.filterNot { it.id == id }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long) {
        rules.value = rules.value.map {
            if (it.id == id) it.copy(isEnabled = enabled, updatedAtEpochMillis = updatedAt) else it
        }
    }
}

class FakeEventDao : ReminderEventDao {
    val events = MutableStateFlow<List<ReminderEvent>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(event: ReminderEvent): Long {
        val id = nextId++
        events.value = events.value + event.copy(id = id)
        return id
    }

    override fun observeRecent(limit: Int): Flow<List<ReminderEvent>> =
        events.map { it.sortedByDescending(ReminderEvent::firedAtEpochMillis).take(limit) }

    override fun observeForRule(ruleId: String): Flow<List<ReminderEvent>> =
        events.map { list -> list.filter { it.ruleId == ruleId } }

    override suspend fun getById(id: Long): ReminderEvent? = events.value.find { it.id == id }

    override suspend fun lastFiredAt(ruleId: String): Long? =
        events.value.filter { it.ruleId == ruleId }.maxOfOrNull { it.firedAtEpochMillis }

    override suspend fun countFiredSince(ruleId: String, sinceEpochMillis: Long): Int =
        events.value.count { it.ruleId == ruleId && it.firedAtEpochMillis >= sinceEpochMillis }

    override suspend fun recordOutcome(id: Long, outcome: ReminderOutcome, respondedAt: Long) {
        events.value = events.value.map {
            if (it.id == id) it.copy(outcome = outcome, respondedAtEpochMillis = respondedAt) else it
        }
    }

    override suspend fun expireStale(
        olderThanEpochMillis: Long,
        pending: ReminderOutcome,
        expired: ReminderOutcome,
    ) {
        events.value = events.value.map {
            if (it.outcome == pending && it.firedAtEpochMillis < olderThanEpochMillis) {
                it.copy(outcome = expired)
            } else it
        }
    }

    override suspend fun deleteOlderThan(olderThanEpochMillis: Long) {
        events.value = events.value.filter { it.firedAtEpochMillis >= olderThanEpochMillis }
    }
}

/** Records what the engine decided to show. */
class RecordingDelivery : ReminderDelivery {
    data class Delivered(val ruleId: String, val eventId: Long, val summary: String)

    val delivered = mutableListOf<Delivered>()

    override fun deliver(
        rule: ReminderRule,
        eventId: Long,
        triggerSummary: String,
    ) {
        delivered += Delivered(rule.id, eventId, triggerSummary)
    }
}
