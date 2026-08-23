package com.rahulgorai.remiit.data.model

import kotlinx.serialization.Serializable

/**
 * How loudly a reminder announces itself. Deliberately independent of the
 * trigger: any trigger can drive any mode, so "office Wi-Fi connects" can be a
 * quiet notification while "09:00" can be a screen-waking alarm.
 */
@Serializable
enum class DeliveryMode {
    /** Ordinary notification in the shade. */
    NOTIFICATION,

    /** Heads-up notification with a full-screen intent that opens the overlay. */
    FULLSCREEN_BANNER,

    /** Full-screen overlay plus a looping alarm-stream sound until answered. */
    ALARM,
}

@Serializable
data class DeliveryConfig(
    val mode: DeliveryMode = DeliveryMode.NOTIFICATION,

    /**
     * Content URI of the sound to play, or null for the mode's default (the
     * channel default for notifications, the system alarm sound for alarms).
     */
    val soundUri: String? = null,

    val vibrate: Boolean = true,

    /** Off/on millisecond pairs, passed straight to the vibrator. */
    val vibrationPattern: List<Long> = DEFAULT_VIBRATION_PATTERN,

    /**
     * Ramp the alarm from quiet to full volume over a few seconds instead of
     * starting at full blast. Only applies to [DeliveryMode.ALARM].
     */
    val escalateVolume: Boolean = true,

    /** Minutes to defer by when the user snoozes. */
    val snoozeMinutes: Int = 10,

    /**
     * Give up after this many seconds with no response and log the reminder as
     * expired. 0 means wait indefinitely.
     */
    val autoDismissSeconds: Int = 0,

    /** Show the Complete / Incomplete pair. Off leaves only a dismiss action. */
    val showCompleteIncomplete: Boolean = true,

    /** Make the notification non-dismissable until answered. */
    val ongoing: Boolean = false,
) {
    /**
     * True when this configuration needs the full-screen overlay activity.
     * Both modes share one activity; only sound behaviour differs.
     */
    val usesFullScreen: Boolean
        get() = mode == DeliveryMode.FULLSCREEN_BANNER || mode == DeliveryMode.ALARM

    companion object {
        val DEFAULT_VIBRATION_PATTERN: List<Long> = listOf(0L, 400L, 200L, 400L)
    }
}
