package com.rahulgorai.remiit.delivery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.rahulgorai.remiit.R
import com.rahulgorai.remiit.data.model.DeliveryMode

/**
 * Notification channels, one per delivery mode.
 *
 * Separate channels rather than per-notification importance because importance
 * is a channel-level property on Android 8+ and cannot be raised afterwards.
 * Splitting them also lets the user mute ordinary reminders in system settings
 * while leaving alarms audible, which is the distinction the app is built around.
 */
object NotificationChannels {

    const val REMINDERS = "reminders"
    const val BANNERS = "banners"
    const val ALARMS = "alarms"
    const val MONITOR = "monitor"

    fun channelFor(mode: DeliveryMode): String = when (mode) {
        DeliveryMode.NOTIFICATION -> REMINDERS
        DeliveryMode.FULLSCREEN_BANNER -> BANNERS
        DeliveryMode.ALARM -> ALARMS
    }

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                REMINDERS,
                context.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_reminders_description)
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                BANNERS,
                context.getString(R.string.channel_banners_name),
                // HIGH is the minimum for a heads-up notification, and a
                // full-screen intent is only honoured on a high-importance
                // channel — at DEFAULT the banner silently degrades to a
                // shade entry.
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_banners_description)
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ALARMS,
                context.getString(R.string.channel_alarms_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alarms_description)
                enableVibration(true)
                setBypassDnd(true)
                // USAGE_ALARM ties the channel to the alarm volume stream, so an
                // alarm reminder stays audible with media muted — the whole
                // point of the alarm mode.
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                MONITOR,
                context.getString(R.string.channel_monitor_name),
                // MIN so the required foreground-service disclosure sits quietly
                // at the bottom of the shade instead of being a nuisance.
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.channel_monitor_description)
                setShowBadge(false)
            }
        )
    }
}
