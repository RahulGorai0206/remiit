package com.rahulgorai.remiit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rahulgorai.remiit.data.model.ReminderEvent
import com.rahulgorai.remiit.data.model.ReminderOutcome
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderEventDao {

    @Insert
    suspend fun insert(event: ReminderEvent): Long

    @Query("SELECT * FROM reminder_events ORDER BY fired_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ReminderEvent>>

    @Query("SELECT * FROM reminder_events WHERE rule_id = :ruleId ORDER BY fired_at DESC")
    fun observeForRule(ruleId: String): Flow<List<ReminderEvent>>

    @Query("SELECT * FROM reminder_events WHERE id = :id")
    suspend fun getById(id: Long): ReminderEvent?

    /** Backs [com.rahulgorai.remiit.data.model.RuleConstraints.cooldownMinutes]. */
    @Query("SELECT MAX(fired_at) FROM reminder_events WHERE rule_id = :ruleId")
    suspend fun lastFiredAt(ruleId: String): Long?

    /** Backs [com.rahulgorai.remiit.data.model.RuleConstraints.maxFiresPerDay]. */
    @Query("SELECT COUNT(*) FROM reminder_events WHERE rule_id = :ruleId AND fired_at >= :sinceEpochMillis")
    suspend fun countFiredSince(ruleId: String, sinceEpochMillis: Long): Int

    @Query("UPDATE reminder_events SET outcome = :outcome, responded_at = :respondedAt WHERE id = :id")
    suspend fun recordOutcome(id: Long, outcome: ReminderOutcome, respondedAt: Long)

    /**
     * Closes out reminders the process never got to resolve — killed mid-alarm,
     * or dismissed by a reboot. Called on cold start so History does not
     * accumulate rows stuck at PENDING forever.
     */
    @Query(
        "UPDATE reminder_events SET outcome = :expired " +
            "WHERE outcome = :pending AND fired_at < :olderThanEpochMillis"
    )
    suspend fun expireStale(
        olderThanEpochMillis: Long,
        pending: ReminderOutcome = ReminderOutcome.PENDING,
        expired: ReminderOutcome = ReminderOutcome.EXPIRED,
    )

    @Query("DELETE FROM reminder_events WHERE fired_at < :olderThanEpochMillis")
    suspend fun deleteOlderThan(olderThanEpochMillis: Long)
}
