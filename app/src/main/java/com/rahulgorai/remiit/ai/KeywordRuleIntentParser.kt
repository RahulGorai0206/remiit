package com.rahulgorai.remiit.ai

import com.rahulgorai.remiit.data.model.DeliveryConfig
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.MINUTES_PER_DAY
import com.rahulgorai.remiit.data.model.Recurrence
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.RuleConstraints
import com.rahulgorai.remiit.data.model.Trigger
import java.util.UUID

/**
 * Placeholder [RuleIntentParser] until an on-device model is wired up.
 *
 * It recognises only the shapes that are cheap to match with regexes — a clock
 * time, "every N minutes/hours", and an alarm/notification hint. Anything else
 * comes back as a failure so the caller falls through to the manual builder.
 * The point is not to be clever; it is to keep [RuleIntentParser]'s call sites
 * exercised so swapping in the real model is a one-file change.
 */
class KeywordRuleIntentParser : RuleIntentParser {

    override val isAvailable: Boolean = false

    override suspend fun parse(utterance: String): Result<ReminderRule> {
        val text = utterance.trim()
        if (text.isEmpty()) return Result.failure(IllegalArgumentException("Empty request"))

        val lower = text.lowercase()
        val recurrence = parseInterval(lower) ?: parseClockTime(lower)
            ?: return Result.failure(
                UnsupportedOperationException("No on-device model loaded; could not read a schedule")
            )

        val mode = when {
            "alarm" in lower || "wake" in lower -> DeliveryMode.ALARM
            "full screen" in lower || "fullscreen" in lower || "banner" in lower ->
                DeliveryMode.FULLSCREEN_BANNER
            else -> DeliveryMode.NOTIFICATION
        }

        return Result.success(
            ReminderRule(
                id = UUID.randomUUID().toString(),
                title = title(text),
                triggers = listOf(
                    Trigger.Time(id = UUID.randomUUID().toString(), recurrence = recurrence)
                ),
                delivery = DeliveryConfig(mode = mode),
                constraints = RuleConstraints(),
            )
        )
    }

    /** "every 2 hours", "every 30 min". */
    private fun parseInterval(lower: String): Recurrence? {
        val match = INTERVAL.find(lower) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        val isHours = match.groupValues[2].startsWith("h")
        val minutes = (if (isHours) amount * 60 else amount).coerceIn(1, MINUTES_PER_DAY)

        // An explicit window if one was given ("between 9 and 6"), else all day.
        val window = WINDOW.find(lower)
        return if (window != null) {
            Recurrence.Interval(
                everyMinutes = minutes,
                startMinuteOfDay = hourToMinuteOfDay(window.groupValues[1].toInt(), morning = true),
                endMinuteOfDay = hourToMinuteOfDay(window.groupValues[2].toInt(), morning = false),
            )
        } else {
            Recurrence.Interval(everyMinutes = minutes)
        }
    }

    /** "at 9", "at 9:30", "at 9pm". */
    private fun parseClockTime(lower: String): Recurrence? {
        val match = CLOCK.find(lower) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]

        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null

        return Recurrence.Daily(minuteOfDay = hour * 60 + minute)
    }

    /** Bare hours in a window are read as working hours: "9 to 6" is 09:00–18:00. */
    private fun hourToMinuteOfDay(hour: Int, morning: Boolean): Int {
        val h = when {
            hour in 1..7 && !morning -> hour + 12
            hour == 12 -> 12
            else -> hour
        }
        return (h.coerceIn(0, 23)) * 60
    }

    /** Strips the scheduling clause so the title reads as the task itself. */
    private fun title(text: String): String {
        val stripped = text
            .replace(Regex("^(remind me to|remind me|remember to)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(INTERVAL, "")
            .replace(WINDOW, "")
            .replace(CLOCK, "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd(',', '.')
        return stripped.ifBlank { text }.replaceFirstChar { it.uppercase() }
    }

    private companion object {
        val INTERVAL = Regex("every\\s+(\\d+)\\s*(hours?|hrs?|h|minutes?|mins?|m)\\b")
        val WINDOW = Regex("(?:between|from)\\s+(\\d{1,2})\\s*(?:am|pm)?\\s*(?:to|-|and|until)\\s*(\\d{1,2})\\s*(?:am|pm)?")
        val CLOCK = Regex("\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
    }
}
