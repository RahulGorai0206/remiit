package com.rahulgorai.remiit.ai

import com.rahulgorai.remiit.data.model.ReminderRule

/**
 * Turns a natural-language request ("remind me to drink water every hour at the
 * office") into a [ReminderRule].
 *
 * This interface exists now, ahead of any model, to fix the seam. The whole
 * rule model is already `@Serializable`, so an on-device LLM only ever has to
 * emit the JSON documented in [RULE_JSON_SCHEMA] — meaning the AI work stays
 * inside this package and touches nothing in the engine, triggers or UI.
 *
 * The intended implementation is MediaPipe `tasks-genai` running a small
 * instruction-tuned model with constrained JSON output, mirroring the setup in
 * the expense-tracker project.
 */
interface RuleIntentParser {

    /** True when a real model is loaded and [parse] will do more than guess. */
    val isAvailable: Boolean

    /**
     * Best-effort parse. Returns failure rather than throwing, because the
     * caller's fallback is always the same: open the manual rule builder
     * pre-filled with whatever was understood.
     */
    suspend fun parse(utterance: String): Result<ReminderRule>
}

/**
 * The contract an implementing model must satisfy. Kept next to the interface
 * so the prompt and the Kotlin model cannot drift apart unnoticed.
 *
 * Discriminator values (`time`, `wifi`, `location`, `app_launch`, `once`,
 * `daily`, `weekly`, `monthly`, `interval`) are the `@SerialName`s on
 * [com.rahulgorai.remiit.data.model.Trigger] and
 * [com.rahulgorai.remiit.data.model.Recurrence]. Changing one there is a
 * breaking change here.
 */
const val RULE_JSON_SCHEMA: String = """
{
  "title": "string, short imperative task name",
  "body": "string, optional detail",
  "match": "ANY | ALL",
  "triggers": [
    { "type": "time", "id": "uuid",
      "recurrence": { "type": "daily",    "minuteOfDay": 540 } },
    { "type": "time", "id": "uuid",
      "recurrence": { "type": "weekly",   "daysOfWeek": [1,3,5], "minuteOfDay": 540 } },
    { "type": "time", "id": "uuid",
      "recurrence": { "type": "monthly",  "dayOfMonth": 1, "minuteOfDay": 540 } },
    { "type": "time", "id": "uuid",
      "recurrence": { "type": "interval", "everyMinutes": 60,
                      "startMinuteOfDay": 540, "endMinuteOfDay": 1080 } },
    { "type": "time", "id": "uuid",
      "recurrence": { "type": "once",     "epochMillis": 0 } },
    { "type": "wifi", "id": "uuid", "ssid": "string", "event": "CONNECTED | DISCONNECTED" },
    { "type": "location", "id": "uuid", "latitude": 0.0, "longitude": 0.0,
      "radiusMeters": 150.0, "event": "ENTER | EXIT | DWELL", "label": "string" },
    { "type": "app_launch", "id": "uuid",
      "packages": ["com.example.app"], "excludes": ["com.rahulgorai.remiit"] }
  ],
  "delivery": {
    "mode": "NOTIFICATION | FULLSCREEN_BANNER | ALARM",
    "vibrate": true, "snoozeMinutes": 10, "showCompleteIncomplete": true
  },
  "constraints": {
    "cooldownMinutes": 0, "maxFiresPerDay": 0,
    "quietHours": { "startMinuteOfDay": 1320, "endMinuteOfDay": 420 },
    "activeDays": [1,2,3,4,5], "matchWindowMinutes": 15
  }
}
"""
