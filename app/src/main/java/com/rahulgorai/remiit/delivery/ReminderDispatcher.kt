package com.rahulgorai.remiit.delivery

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rahulgorai.remiit.R
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.ui.reminder.ReminderOverlayActivity
import com.rahulgorai.remiit.util.PENDING_INTENT_FLAGS
import com.rahulgorai.remiit.util.Permissions
import com.rahulgorai.remiit.util.requestCodeFor

/**
 * Turns a fired rule into whatever the user asked for: a notification, a
 * full-screen banner, or an alarm.
 *
 * All three modes post a notification. The difference is the channel's
 * importance and whether a full-screen intent is attached — that is the only
 * supported way to put a UI on screen from the background on modern Android,
 * and it degrades to a heads-up notification if the grant is missing rather
 * than failing outright.
 */
class ReminderDispatcher(
    private val context: Context,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun deliver(rule: ReminderRule, eventId: Long, triggerSummary: String) {
        NotificationChannels.ensureCreated(context)

        val config = rule.delivery
        val notificationId = ReminderActionReceiver.notificationIdFor(eventId)

        val builder = NotificationCompat.Builder(context, NotificationChannels.channelFor(config.mode))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(rule.title)
            .setContentText(rule.body.ifBlank { triggerSummary })
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(rule.body.ifBlank { triggerSummary })
                    .setSummaryText(triggerSummary)
            )
            .setAutoCancel(true)
            .setOngoing(config.ongoing)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(overlayPendingIntent(rule, eventId, triggerSummary))

        if (config.mode != DeliveryMode.NOTIFICATION) {
            builder
                .setPriority(NotificationCompat.PRIORITY_MAX)
                // CATEGORY_ALARM is what earns heads-up presentation and DND
                // bypass on an alarm channel.
                .setCategory(
                    if (config.mode == DeliveryMode.ALARM) NotificationCompat.CATEGORY_ALARM
                    else NotificationCompat.CATEGORY_REMINDER
                )
                .setFullScreenIntent(overlayPendingIntent(rule, eventId, triggerSummary), true)
        }

        if (config.showCompleteIncomplete) {
            builder.addAction(
                0,
                context.getString(R.string.action_complete),
                actionIntent(ReminderActionReceiver.ACTION_COMPLETE, rule, eventId),
            )
            builder.addAction(
                0,
                context.getString(R.string.action_incomplete),
                actionIntent(ReminderActionReceiver.ACTION_INCOMPLETE, rule, eventId),
            )
        }
        if (config.snoozeMinutes > 0) {
            builder.addAction(
                0,
                context.getString(R.string.action_snooze),
                actionIntent(ReminderActionReceiver.ACTION_SNOOZE, rule, eventId),
            )
        }
        // Swiping away is a real answer, and DISMISSED is worth distinguishing
        // from "never responded" in the history.
        builder.setDeleteIntent(
            actionIntent(ReminderActionReceiver.ACTION_DISMISS, rule, eventId)
        )

        try {
            notificationManager?.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "POST_NOTIFICATIONS not granted; reminder for ${rule.id} dropped", e)
            return
        }

        // A full-screen intent on a screen that is already on and unlocked is
        // shown as a heads-up notification only — the OS will not launch the
        // activity. Starting it directly covers that case, which is the common
        // one when the user is actively using the phone.
        if (config.usesFullScreen && Permissions.canUseFullScreenIntent(context)) {
            startOverlayDirectly(rule, eventId, triggerSummary)
        }
    }

    private fun startOverlayDirectly(rule: ReminderRule, eventId: Long, triggerSummary: String) {
        runCatching {
            context.startActivity(
                overlayIntent(rule, eventId, triggerSummary)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            // Background activity starts are restricted; the full-screen intent
            // attached above is the fallback and needs no action here.
            Log.d(TAG, "Direct overlay start refused, relying on full-screen intent")
        }
    }

    private fun overlayIntent(rule: ReminderRule, eventId: Long, triggerSummary: String): Intent =
        Intent(context, ReminderOverlayActivity::class.java).apply {
            putExtra(ReminderOverlayActivity.EXTRA_RULE_ID, rule.id)
            putExtra(ReminderOverlayActivity.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderOverlayActivity.EXTRA_TRIGGER_SUMMARY, triggerSummary)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }

    private fun overlayPendingIntent(
        rule: ReminderRule,
        eventId: Long,
        triggerSummary: String,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCodeFor("overlay", rule.id, eventId.toString()),
        overlayIntent(rule, eventId, triggerSummary),
        PENDING_INTENT_FLAGS,
    )

    private fun actionIntent(action: String, rule: ReminderRule, eventId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCodeFor(action, rule.id, eventId.toString()),
            Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                putExtra(ReminderActionReceiver.EXTRA_EVENT_ID, eventId)
                putExtra(ReminderActionReceiver.EXTRA_RULE_ID, rule.id)
            },
            PENDING_INTENT_FLAGS,
        )

    private companion object {
        const val TAG = "ReminderDispatcher"
    }
}
