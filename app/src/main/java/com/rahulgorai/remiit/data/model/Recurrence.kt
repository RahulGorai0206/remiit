package com.rahulgorai.remiit.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The shape of a [Trigger.Time] schedule.
 *
 * Times are stored as a minute-of-day plus a calendar pattern rather than as
 * absolute instants, so a rule keeps meaning "09:00 local" across time zone
 * changes and daylight-saving transitions instead of drifting by an hour.
 */
@Serializable
sealed interface Recurrence {

    /** Fires once at an absolute instant, then never again. */
    @Serializable
    @SerialName("once")
    data class Once(val epochMillis: Long) : Recurrence

    /** Every day at [minuteOfDay]. */
    @Serializable
    @SerialName("daily")
    data class Daily(val minuteOfDay: Int) : Recurrence

    /** On the given ISO days (1 = Monday … 7 = Sunday) at [minuteOfDay]. */
    @Serializable
    @SerialName("weekly")
    data class Weekly(val daysOfWeek: Set<Int>, val minuteOfDay: Int) : Recurrence

    /**
     * On [dayOfMonth] each month at [minuteOfDay]. A day past the end of a
     * short month is clamped to that month's last day, so 31 still fires in
     * February rather than being skipped.
     */
    @Serializable
    @SerialName("monthly")
    data class Monthly(val dayOfMonth: Int, val minuteOfDay: Int) : Recurrence

    /**
     * Every [everyMinutes] within a daily window — the "drink water every hour
     * between 09:00 and 18:00" case.
     *
     * The window must not wrap midnight; [startMinuteOfDay] >= [endMinuteOfDay]
     * is treated as the whole day.
     */
    @Serializable
    @SerialName("interval")
    data class Interval(
        val everyMinutes: Int,
        val startMinuteOfDay: Int = 0,
        val endMinuteOfDay: Int = MINUTES_PER_DAY - 1,
    ) : Recurrence
}

const val MINUTES_PER_DAY: Int = 24 * 60

/**
 * The first instant strictly after [after] at which this recurrence fires, or
 * null if it never will again (a [Recurrence.Once] already in the past, or a
 * [Recurrence.Weekly] with no days selected).
 *
 * Strictly-after matters: schedulers call this with "now" right after a fire,
 * and an inclusive comparison would re-arm the alarm for the instant that just
 * elapsed and spin.
 */
fun Recurrence.nextOccurrenceAfter(after: Instant, zone: ZoneId): Instant? = when (this) {
    is Recurrence.Once ->
        Instant.ofEpochMilli(epochMillis).takeIf { it.isAfter(after) }

    is Recurrence.Daily ->
        firstMatching(after, zone, daysToScan = 2) { _ -> minuteOfDay }

    is Recurrence.Weekly -> {
        val days = daysOfWeek.filter { it in 1..7 }.toSet()
        if (days.isEmpty()) {
            null
        } else {
            // Scan 8 days rather than 7: today may already be past the time, so
            // the same weekday one week out has to stay reachable.
            firstMatching(after, zone, daysToScan = 8) { date ->
                minuteOfDay.takeIf { date.dayOfWeek.value in days }
            }
        }
    }

    is Recurrence.Monthly -> {
        // Two months is sufficient because clamping guarantees every month has
        // a candidate day; only "this month's is already past" needs a rollover.
        var candidate: Instant? = null
        var monthStart = after.atZone(zone).toLocalDate().withDayOfMonth(1)
        repeat(2) {
            if (candidate == null) {
                val clampedDay = dayOfMonth.coerceIn(1, monthStart.lengthOfMonth())
                val at = zonedAt(monthStart.withDayOfMonth(clampedDay), minuteOfDay, zone)
                if (at.toInstant().isAfter(after)) candidate = at.toInstant()
                monthStart = monthStart.plusMonths(1)
            }
        }
        candidate
    }

    is Recurrence.Interval -> {
        val step = everyMinutes.coerceAtLeast(1)
        val wholeDay = startMinuteOfDay >= endMinuteOfDay
        val windowStart = if (wholeDay) 0 else startMinuteOfDay
        val windowEnd = if (wholeDay) MINUTES_PER_DAY - 1 else endMinuteOfDay

        var candidate: Instant? = null
        var date = after.atZone(zone).toLocalDate()
        repeat(2) {
            if (candidate == null) {
                var minute = windowStart
                while (minute <= windowEnd && candidate == null) {
                    val at = zonedAt(date, minute, zone).toInstant()
                    if (at.isAfter(after)) candidate = at
                    minute += step
                }
                date = date.plusDays(1)
            }
        }
        candidate
    }
}

/**
 * Walks forward [daysToScan] calendar days from [after], returning the first
 * instant produced by [minuteOfDayFor] that lies strictly after it.
 */
private inline fun firstMatching(
    after: Instant,
    zone: ZoneId,
    daysToScan: Int,
    minuteOfDayFor: (LocalDate) -> Int?,
): Instant? {
    var date = after.atZone(zone).toLocalDate()
    repeat(daysToScan) {
        val minute = minuteOfDayFor(date)
        if (minute != null) {
            val at = zonedAt(date, minute, zone).toInstant()
            if (at.isAfter(after)) return at
        }
        date = date.plusDays(1)
    }
    return null
}

/**
 * Resolves a local date plus minute-of-day to a zoned instant.
 *
 * [ZonedDateTime.of] is used deliberately rather than adding minutes to the
 * start of day: it resolves a local time that does not exist (the spring-forward
 * gap) by shifting forward past the gap, and picks the earlier offset for a
 * time that happens twice in autumn. Adding elapsed minutes instead would move
 * a 09:00 reminder to 10:00 on transition days.
 */
internal fun zonedAt(date: LocalDate, minuteOfDay: Int, zone: ZoneId): ZonedDateTime {
    val clamped = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    return ZonedDateTime.of(date, LocalTime.of(clamped / 60, clamped % 60), zone)
}
