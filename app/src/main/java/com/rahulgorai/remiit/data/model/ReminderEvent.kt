package com.rahulgorai.remiit.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What the user did about a reminder once it fired. */
enum class ReminderOutcome {
    /** Fired, awaiting a response. */
    PENDING,
    COMPLETED,
    INCOMPLETE,
    SNOOZED,

    /** Swiped away without answering. */
    DISMISSED,

    /** Hit [DeliveryConfig.autoDismissSeconds] with no response. */
    EXPIRED,
}

/**
 * One firing of a rule, and the response to it.
 *
 * This is both the History screen's data source and the engine's memory: the
 * cooldown and per-day cap in [RuleConstraints] are evaluated by querying fire
 * times out of this table, so it has to be written on every fire — including
 * ones the user never answers.
 */
@Entity(
    tableName = "reminder_events",
    indices = [Index(value = ["rule_id", "fired_at"])],
)
data class ReminderEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "rule_id")
    val ruleId: String,

    /** Denormalised so history still reads correctly after a rule is renamed. */
    @ColumnInfo(name = "rule_title")
    val ruleTitle: String,

    @ColumnInfo(name = "fired_at")
    val firedAtEpochMillis: Long,

    /** Which trigger caused it, as a short human-readable label. */
    @ColumnInfo(name = "trigger_summary")
    val triggerSummary: String,

    val outcome: ReminderOutcome = ReminderOutcome.PENDING,

    @ColumnInfo(name = "responded_at")
    val respondedAtEpochMillis: Long? = null,
)
