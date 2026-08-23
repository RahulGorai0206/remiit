package com.rahulgorai.remiit.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * One reminder and the rules that make it fire.
 *
 * The three composite fields ([triggers], [delivery], [constraints]) are stored
 * as JSON columns. A rule is therefore one self-contained document, which is
 * what lets the planned on-device AI produce a complete rule in a single step
 * and lets new trigger kinds ship without a schema migration.
 */
@Entity(tableName = "reminder_rules")
@Serializable
data class ReminderRule(
    @PrimaryKey
    val id: String,

    val title: String,

    /** Optional detail line shown under the title. */
    val body: String = "",

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    val triggers: List<Trigger> = emptyList(),

    val match: MatchMode = MatchMode.ANY,

    val delivery: DeliveryConfig = DeliveryConfig(),

    val constraints: RuleConstraints = RuleConstraints(),

    @ColumnInfo(name = "created_at")
    val createdAtEpochMillis: Long = 0L,

    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long = 0L,
) {
    val timeTriggers: List<Trigger.Time> get() = triggers.filterIsInstance<Trigger.Time>()
    val wifiTriggers: List<Trigger.Wifi> get() = triggers.filterIsInstance<Trigger.Wifi>()
    val locationTriggers: List<Trigger.Location> get() = triggers.filterIsInstance<Trigger.Location>()
    val appLaunchTriggers: List<Trigger.AppLaunch> get() = triggers.filterIsInstance<Trigger.AppLaunch>()

    /**
     * True when this rule needs [com.rahulgorai.remiit.service.RemiitMonitorService]
     * running. Time and location triggers are handled by the OS (AlarmManager,
     * geofences), so a rule using only those costs no persistent notification.
     */
    val needsMonitorService: Boolean
        get() = wifiTriggers.isNotEmpty() || appLaunchTriggers.isNotEmpty()

    /**
     * Soonest instant any time trigger on this rule will fire, or null if it has
     * none or they have all elapsed. Used for the "next fire" line on the rule card.
     */
    fun nextTimeFire(after: Instant, defaultZone: ZoneId): Instant? =
        timeTriggers.mapNotNull { trigger ->
            val zone = trigger.timeZoneId?.let(::runCatchingZone) ?: defaultZone
            trigger.recurrence.nextOccurrenceAfter(after, zone)
        }.minOrNull()
}

private fun runCatchingZone(id: String): ZoneId? =
    try {
        ZoneId.of(id)
    } catch (_: Exception) {
        // A rule can outlive a zone id (tzdb renames, or an AI-authored rule
        // with a bad zone). Falling back to the device zone keeps the rule
        // firing instead of dropping it silently.
        null
    }
