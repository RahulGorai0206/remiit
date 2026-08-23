package com.rahulgorai.remiit.delivery

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rahulgorai.remiit.data.model.ReminderOutcome
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.trigger.time.TimeTriggerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Handles Complete / Not done / Snooze / Dismiss, from either the notification
 * actions or the full-screen overlay.
 *
 * Routing both through one receiver keeps a single place that writes the
 * outcome, stops the alarm sound and clears the notification — otherwise the
 * overlay and the shade can disagree about whether a reminder was answered.
 */
class ReminderActionReceiver : BroadcastReceiver(), KoinComponent {

    private val repository: RuleRepository by inject()
    private val scheduler: TimeTriggerScheduler by inject()
    private val clock: Clock by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
        val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID).orEmpty()
        val action = intent.action ?: return

        // Silence the alarm and clear the shade before touching the database:
        // the user tapped a button and expects the noise to stop immediately,
        // not after a disk write.
        AlarmSoundPlayer.stop()
        if (eventId > 0) {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(notificationIdFor(eventId))
        }
        // Close the overlay if it is showing.
        context.sendBroadcast(Intent(ACTION_DISMISS_OVERLAY).setPackage(context.packageName))

        val outcome = when (action) {
            ACTION_COMPLETE -> ReminderOutcome.COMPLETED
            ACTION_INCOMPLETE -> ReminderOutcome.INCOMPLETE
            ACTION_SNOOZE -> ReminderOutcome.SNOOZED
            ACTION_DISMISS -> ReminderOutcome.DISMISSED
            else -> return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (eventId > 0) repository.recordOutcome(eventId, outcome)

                if (outcome == ReminderOutcome.SNOOZED && ruleId != null) {
                    val rule = repository.rule(ruleId)
                    if (rule != null) {
                        scheduler.scheduleSnooze(
                            rule = rule,
                            triggerId = triggerId.ifBlank { SNOOZE_TRIGGER_ID },
                            fireAt = clock.instant()
                                .plus(rule.delivery.snoozeMinutes.toLong(), ChronoUnit.MINUTES),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling $action for event $eventId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.rahulgorai.remiit.action.COMPLETE"
        const val ACTION_INCOMPLETE = "com.rahulgorai.remiit.action.INCOMPLETE"
        const val ACTION_SNOOZE = "com.rahulgorai.remiit.action.SNOOZE"
        const val ACTION_DISMISS = "com.rahulgorai.remiit.action.DISMISS"

        /** Local broadcast the overlay listens for so it closes itself. */
        const val ACTION_DISMISS_OVERLAY = "com.rahulgorai.remiit.action.DISMISS_OVERLAY"

        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_TRIGGER_ID = "trigger_id"

        /**
         * A snooze of a non-time trigger (Wi-Fi, geofence) still needs an alarm
         * to bring it back, but has no time trigger to advance afterwards. This
         * sentinel id makes TimeTriggerReceiver skip the re-arm step.
         */
        const val SNOOZE_TRIGGER_ID = "__snooze__"

        /**
         * Notification ids are derived from the event row id so each firing gets
         * its own notification and answering one cannot clear another.
         */
        fun notificationIdFor(eventId: Long): Int = (NOTIFICATION_ID_BASE + eventId).toInt()

        private const val NOTIFICATION_ID_BASE = 20_000L
        private const val TAG = "ReminderActionReceiver"
    }
}
