package com.rahulgorai.remiit.trigger.time

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.data.model.nextOccurrenceAfter
import com.rahulgorai.remiit.util.PENDING_INTENT_FLAGS
import com.rahulgorai.remiit.util.requestCodeFor
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Arms and cancels the OS alarms behind [Trigger.Time].
 *
 * Recurrences are re-armed one fire at a time rather than using
 * [AlarmManager.setRepeating]: repeating alarms are inexact on modern Android,
 * and an interval like "every hour between 09:00 and 18:00" cannot be expressed
 * as a fixed period anyway.
 */
class TimeTriggerScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Whether the OS will honour exact timing. When false, alarms are still set
     * but the system may delay them to batch wakeups — so the UI needs to say
     * so rather than let the user think a 09:00 reminder is guaranteed.
     */
    val canScheduleExact: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /** Arms the next fire for every time trigger on [rule]. */
    fun schedule(rule: ReminderRule) {
        if (!rule.isEnabled) {
            cancel(rule)
            return
        }
        rule.timeTriggers.forEach { trigger -> scheduleTrigger(rule, trigger) }
    }

    /**
     * Arms the single next occurrence of [trigger]. Called both when a rule is
     * saved and from [TimeTriggerReceiver] after a fire, which is what advances
     * a recurrence.
     */
    fun scheduleTrigger(rule: ReminderRule, trigger: Trigger.Time, after: Instant = clock.instant()) {
        val zone = resolveZone(trigger.timeZoneId)
        val next = trigger.recurrence.nextOccurrenceAfter(after, zone)
        if (next == null) {
            // A one-shot that has already fired. Cancelling keeps a stale
            // PendingIntent from being reused if the rule is edited later.
            cancelTrigger(rule.id, trigger.id)
            return
        }
        setAlarm(rule, trigger, next)
    }

    fun cancel(rule: ReminderRule) {
        rule.timeTriggers.forEach { cancelTrigger(rule.id, it.id) }
    }

    fun cancelTrigger(ruleId: String, triggerId: String) {
        alarmManager?.cancel(pendingIntent(ruleId, triggerId))
    }

    /**
     * Re-arms everything from scratch. Used after boot, after an app update,
     * and from the periodic reconcile worker — alarms do not survive any of
     * those, and a dropped alarm is a reminder that silently never arrives.
     */
    fun rescheduleAll(rules: List<ReminderRule>) {
        rules.filter { it.isEnabled }.forEach { schedule(it) }
    }

    /** Arms a one-off alarm for a snooze. Reuses the rule's own delivery mode. */
    fun scheduleSnooze(rule: ReminderRule, triggerId: String, fireAt: Instant) {
        val trigger = rule.timeTriggers.firstOrNull { it.id == triggerId }
        setAlarmAt(
            ruleId = rule.id,
            triggerId = triggerId,
            fireAt = fireAt,
            asAlarmClock = rule.delivery.mode == DeliveryMode.ALARM,
            recurrenceLabel = trigger?.let { "Snoozed" } ?: "Snoozed",
        )
    }

    private fun setAlarm(rule: ReminderRule, trigger: Trigger.Time, fireAt: Instant) {
        setAlarmAt(
            ruleId = rule.id,
            triggerId = trigger.id,
            fireAt = fireAt,
            // setAlarmClock survives Doze and app standby, which a reminder the
            // user treats as an alarm has to. It also surfaces the system alarm
            // icon, so the guarantee is visible.
            asAlarmClock = rule.delivery.mode == DeliveryMode.ALARM,
            recurrenceLabel = null,
        )
    }

    @SuppressLint("MissingPermission")
    private fun setAlarmAt(
        ruleId: String,
        triggerId: String,
        fireAt: Instant,
        asAlarmClock: Boolean,
        recurrenceLabel: String?,
    ) {
        val manager = alarmManager ?: return
        val intent = pendingIntent(ruleId, triggerId, recurrenceLabel)
        val atMillis = fireAt.toEpochMilli()

        try {
            when {
                asAlarmClock && canScheduleExact ->
                    manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, intent), intent)

                canScheduleExact ->
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)

                // Exact alarms not granted. Still set something: a reminder a
                // few minutes late beats no reminder, and the permission screen
                // surfaces the downgrade.
                else ->
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            }
        } catch (e: SecurityException) {
            // canScheduleExactAlarms can go stale between the check and the call
            // if the user revokes it in Settings mid-flight.
            Log.w(TAG, "Exact alarm refused for $ruleId/$triggerId, falling back to inexact", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    private fun pendingIntent(
        ruleId: String,
        triggerId: String,
        recurrenceLabel: String? = null,
    ): PendingIntent {
        val intent = Intent(context, TimeTriggerReceiver::class.java).apply {
            action = TimeTriggerReceiver.ACTION_FIRE
            putExtra(TimeTriggerReceiver.EXTRA_RULE_ID, ruleId)
            putExtra(TimeTriggerReceiver.EXTRA_TRIGGER_ID, triggerId)
            recurrenceLabel?.let { putExtra(TimeTriggerReceiver.EXTRA_LABEL, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(ruleId, triggerId),
            intent,
            PENDING_INTENT_FLAGS,
        )
    }

    private fun resolveZone(id: String?): ZoneId =
        id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: clock.zone

    private companion object {
        const val TAG = "TimeTriggerScheduler"
    }
}
