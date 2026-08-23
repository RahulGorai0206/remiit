package com.rahulgorai.remiit.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun clockLabel(minuteOfDay: Int): String {
    val m = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    return "%02d:%02d".format(m / 60, m % 60)
}

/**
 * Compact, non-localised description of a trigger.
 *
 * Used for [ReminderEvent.triggerSummary] — the history log wants a stable
 * string it can store, not a string that changes meaning when the device
 * locale changes. Screens that need localised text format from the trigger
 * fields directly instead of calling this.
 */
fun Trigger.shortSummary(): String = when (this) {
    is Trigger.Time -> recurrence.shortSummary()
    is Trigger.Wifi -> when (event) {
        WifiEvent.CONNECTED -> "Connected to $ssid"
        WifiEvent.DISCONNECTED -> "Left $ssid"
    }

    is Trigger.Location -> {
        val place = label.ifBlank { "%.4f, %.4f".format(latitude, longitude) }
        when (event) {
            LocationEvent.ENTER -> "Arrived at $place"
            LocationEvent.EXIT -> "Left $place"
            LocationEvent.DWELL -> "Stayed ${dwellMinutes}m at $place"
        }
    }

    is Trigger.AppLaunch -> when {
        packages.isEmpty() -> "Any app opened"
        packages.size == 1 -> "${packages.first()} opened"
        else -> "${packages.size} apps opened"
    }
}

fun Recurrence.shortSummary(): String = when (this) {
    is Recurrence.Once ->
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    is Recurrence.Daily -> "Daily at ${clockLabel(minuteOfDay)}"

    is Recurrence.Weekly -> {
        val days = daysOfWeek.filter { it in 1..7 }.sorted()
        when {
            days.isEmpty() -> "Weekly (no days set)"
            days.size == 7 -> "Daily at ${clockLabel(minuteOfDay)}"
            else -> days.joinToString(", ") { DAY_NAMES[it - 1] } + " at ${clockLabel(minuteOfDay)}"
        }
    }

    is Recurrence.Monthly -> "Day $dayOfMonth at ${clockLabel(minuteOfDay)}"

    is Recurrence.Interval -> {
        val every = when {
            everyMinutes % 60 == 0 -> "${everyMinutes / 60}h"
            else -> "${everyMinutes}m"
        }
        if (startMinuteOfDay >= endMinuteOfDay) {
            "Every $every"
        } else {
            "Every $every, ${clockLabel(startMinuteOfDay)}–${clockLabel(endMinuteOfDay)}"
        }
    }
}
