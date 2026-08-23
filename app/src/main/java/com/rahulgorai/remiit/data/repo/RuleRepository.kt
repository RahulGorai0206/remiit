package com.rahulgorai.remiit.data.repo

import com.rahulgorai.remiit.data.db.ReminderEventDao
import com.rahulgorai.remiit.data.db.RuleDao
import com.rahulgorai.remiit.data.model.ReminderEvent
import com.rahulgorai.remiit.data.model.ReminderOutcome
import com.rahulgorai.remiit.data.model.ReminderRule
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID

/**
 * Single entry point to the rule store.
 *
 * [clock] is injected rather than calling [System.currentTimeMillis] so the
 * engine's cooldown and per-day-cap logic can be driven deterministically in
 * tests.
 */
class RuleRepository(
    private val ruleDao: RuleDao,
    private val eventDao: ReminderEventDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeRules(): Flow<List<ReminderRule>> = ruleDao.observeAll()

    fun observeEnabledRules(): Flow<List<ReminderRule>> = ruleDao.observeEnabled()

    fun observeRule(id: String): Flow<ReminderRule?> = ruleDao.observeById(id)

    suspend fun enabledRules(): List<ReminderRule> = ruleDao.getEnabled()

    suspend fun rule(id: String): ReminderRule? = ruleDao.getById(id)

    /** Inserts or updates, filling in id and timestamps. Returns the stored rule. */
    suspend fun save(rule: ReminderRule): ReminderRule {
        val now = clock.millis()
        val stored = rule.copy(
            id = rule.id.ifBlank { UUID.randomUUID().toString() },
            createdAtEpochMillis = if (rule.createdAtEpochMillis == 0L) now else rule.createdAtEpochMillis,
            updatedAtEpochMillis = now,
        )
        ruleDao.upsert(stored)
        return stored
    }

    suspend fun setEnabled(id: String, enabled: Boolean) =
        ruleDao.setEnabled(id, enabled, clock.millis())

    suspend fun delete(id: String) = ruleDao.deleteById(id)

    // ---- History -----------------------------------------------------------

    fun observeRecentEvents(limit: Int = 200): Flow<List<ReminderEvent>> =
        eventDao.observeRecent(limit)

    fun observeEventsForRule(ruleId: String): Flow<List<ReminderEvent>> =
        eventDao.observeForRule(ruleId)

    /** Records a fire and returns the row id, which the notification carries so
     *  a later Complete/Incomplete tap can be attributed to this exact firing. */
    suspend fun recordFire(rule: ReminderRule, triggerSummary: String): Long =
        eventDao.insert(
            ReminderEvent(
                ruleId = rule.id,
                ruleTitle = rule.title,
                firedAtEpochMillis = clock.millis(),
                triggerSummary = triggerSummary,
                outcome = ReminderOutcome.PENDING,
            )
        )

    suspend fun recordOutcome(eventId: Long, outcome: ReminderOutcome) =
        eventDao.recordOutcome(eventId, outcome, clock.millis())

    suspend fun lastFiredAt(ruleId: String): Long? = eventDao.lastFiredAt(ruleId)

    suspend fun countFiredSince(ruleId: String, sinceEpochMillis: Long): Int =
        eventDao.countFiredSince(ruleId, sinceEpochMillis)

    /**
     * Housekeeping for cold start: resolve reminders the process died holding,
     * and drop history past the retention window.
     */
    suspend fun pruneAndExpire(retentionDays: Long = 90) {
        val now = clock.millis()
        eventDao.expireStale(olderThanEpochMillis = now - STALE_PENDING_MILLIS)
        eventDao.deleteOlderThan(now - retentionDays * 24 * 60 * 60 * 1000L)
    }

    private companion object {
        /** A reminder still PENDING after this long is never getting answered. */
        const val STALE_PENDING_MILLIS = 24 * 60 * 60 * 1000L
    }
}
