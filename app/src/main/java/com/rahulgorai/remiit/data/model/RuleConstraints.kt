package com.rahulgorai.remiit.data.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * Limits applied after a rule's triggers match but before it fires.
 *
 * These live on the rule rather than inside each trigger so a constraint like
 * "at most once an hour, never at night" is written once and covers every
 * trigger on the rule. Without that, "remind me on any app launch" is unusable:
 * it would fire dozens of times an hour, including at 3am.
 */
@Serializable
data class RuleConstraints(
    /** Minimum gap between two fires of this rule. 0 disables. */
    val cooldownMinutes: Int = 0,

    /** Cap on fires per calendar day in the device's zone. 0 means unlimited. */
    val maxFiresPerDay: Int = 0,

    /** Window during which the rule stays silent. */
    val quietHours: QuietHours? = null,

    /** ISO days (1 = Monday … 7 = Sunday) the rule is active on. Empty = all. */
    val activeDays: Set<Int> = emptySet(),

    val validFromEpochMillis: Long? = null,
    val validUntilEpochMillis: Long? = null,

    /**
     * For [MatchMode.ALL], how long a satisfied trigger stays "recently
     * satisfied" while waiting for its siblings. Without a window, connecting
     * to office Wi-Fi on Monday would still count toward an ALL match on Friday.
     */
    val matchWindowMinutes: Int = 15,
) {
    /**
     * Whether [at] falls inside the rule's allowed days and validity range and
     * outside quiet hours. Does not consider cooldown or the daily cap — those
     * need the fire history and are evaluated by the engine.
     */
    fun allows(at: Instant, zone: ZoneId): Boolean {
        val millis = at.toEpochMilli()
        validFromEpochMillis?.let { if (millis < it) return false }
        validUntilEpochMillis?.let { if (millis > it) return false }

        val local = at.atZone(zone)
        if (activeDays.isNotEmpty() && local.dayOfWeek.value !in activeDays) return false

        val minuteOfDay = local.hour * 60 + local.minute
        if (quietHours?.contains(minuteOfDay) == true) return false

        return true
    }
}

/**
 * A daily silent window, as minutes from local midnight.
 *
 * The interesting case is the common one: 22:00–07:00 wraps past midnight, so
 * [start] is greater than [end] and containment is the union of two ranges
 * rather than a single interval.
 */
@Serializable
data class QuietHours(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
) {
    fun contains(minuteOfDay: Int): Boolean =
        if (startMinuteOfDay <= endMinuteOfDay) {
            minuteOfDay >= startMinuteOfDay && minuteOfDay < endMinuteOfDay
        } else {
            minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
        }
}
