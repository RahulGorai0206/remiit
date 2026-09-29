package com.rahulgorai.remiit.automation

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import com.rahulgorai.remiit.data.model.RingerMode
import com.rahulgorai.remiit.data.model.SoundSetting
import com.rahulgorai.remiit.data.model.VolumeStream
import kotlin.math.roundToInt

/** The outcome of one settings change, in words the user can act on. */
sealed interface ActionResult {
    data object Ok : ActionResult

    /** [reason] is shown on the automation card, so it names the fix. */
    data class Failed(val reason: String) : ActionResult
}

/**
 * Everything an automation is allowed to change about the phone.
 *
 * An interface because the engine's job — decide what to change, record what
 * happened — is pure logic worth testing, and the implementation underneath is
 * three system services that cannot exist in a unit test.
 */
interface DeviceControls {

    /** Do Not Disturb access, granted in Settings and revocable at any time. */
    fun hasDndAccess(): Boolean

    /** "Modify system settings" — what writing the brightness mode needs. */
    fun canWriteSystemSettings(): Boolean

    fun applySound(setting: SoundSetting): ActionResult

    fun applyAutoBrightness(enabled: Boolean): ActionResult

    /** [percent] is 0-100 of the stream's range; see [volumeIndexFor]. */
    fun applyVolume(stream: VolumeStream, percent: Int): ActionResult

    // ---- Reading the current state, for restore-on-exit ------------------
    //
    // Each returns null when the value cannot be read, and a null is recorded
    // as "nothing to restore" for that setting. Guessing instead — assuming the
    // ringer was on, say — would restore a state the phone was never in.

    fun readRinger(): RingerMode?

    fun readDndOn(): Boolean?

    fun readAutoBrightness(): Boolean?

    /** Percent of the stream's range; see [volumePercentFor]. */
    fun readVolume(stream: VolumeStream): Int?
}

/**
 * The inverse of [volumeIndexFor]: the percentage a device index represents.
 *
 * A snapshot is recorded in percent and restored through [volumeIndexFor], so
 * the round trip has to land on the index it started from. It does for any
 * scale of up to a hundred steps — a percent is then no coarser than a step,
 * so rounding to one and back cannot cross a step boundary — which covers
 * every stream on every device Android ships. The test proves it exhaustively.
 */
fun volumePercentFor(index: Int, min: Int, max: Int): Int {
    if (max <= min) return 0
    val clamped = index.coerceIn(min, max)
    return ((clamped - min) * 100f / (max - min)).roundToInt()
}

/**
 * Turns a percentage into the index Android wants.
 *
 * Pure, and separate from the class that calls it, because this is the part
 * worth being sure about: the scales are small (ring often tops out at 7) so
 * rounding decides whether "60%" lands on 4 or 5, and a stream's floor is not
 * always zero — some devices refuse to mute the call stream and report a
 * minimum of 1. Interpolating across [min]..[max] rather than 0..[max] is what
 * keeps 0% meaning "as quiet as this stream goes" rather than throwing.
 */
fun volumeIndexFor(percent: Int, min: Int, max: Int): Int {
    if (max <= min) return min
    val clamped = percent.coerceIn(0, 100)
    return min + ((max - min) * clamped / 100f).roundToInt()
}

/**
 * Whether setting this stream to this level needs Do Not Disturb access.
 *
 * Silencing the ringer is a Do Not Disturb operation however it is reached, so
 * taking the ring, notification or system stream to zero hits the same platform
 * check that [SoundSetting.SILENT] does. Alarms and media are exempt: neither
 * is part of the ringer, which is exactly why they have their own streams —
 * muting your music is not a Do Not Disturb decision.
 */
fun volumeRequiresDndAccess(stream: VolumeStream, percent: Int): Boolean =
    percent == 0 && stream != VolumeStream.ALARM && stream != VolumeStream.MEDIA

/**
 * Whether this setting cannot even be attempted without Do Not Disturb access.
 *
 * Silencing the ringer is a Do Not Disturb operation underneath — since Android
 * 7 the platform refuses `setRingerMode(SILENT)` from an app without policy
 * access, because silent and DND are the same subsystem. Vibrate and ring are
 * not listed here despite being able to fail the same way: they only need the
 * grant when they are *leaving* silent, which depends on the state of the phone
 * at the moment the automation runs rather than on the automation itself.
 * Demanding the grant up front for those would be asking for an intrusive
 * permission to cover a case that may never arise.
 */
fun SoundSetting.requiresDndAccess(): Boolean = when (this) {
    SoundSetting.SILENT, SoundSetting.DND_ON, SoundSetting.DND_OFF -> true
    SoundSetting.VIBRATE, SoundSetting.RING -> false
}

class AndroidDeviceControls(private val context: Context) : DeviceControls {

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val notifications: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    override fun hasDndAccess(): Boolean =
        notifications?.isNotificationPolicyAccessGranted == true

    override fun canWriteSystemSettings(): Boolean = Settings.System.canWrite(context)

    override fun applySound(setting: SoundSetting): ActionResult {
        if (setting.requiresDndAccess() && !hasDndAccess()) {
            return ActionResult.Failed("Do Not Disturb access not granted")
        }

        return when (setting) {
            SoundSetting.DND_ON -> setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                "turn on Do Not Disturb",
            )

            SoundSetting.DND_OFF -> setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_ALL,
                "turn off Do Not Disturb",
            )

            SoundSetting.SILENT -> setRingerMode(AudioManager.RINGER_MODE_SILENT, "silence")
            SoundSetting.VIBRATE -> setRingerMode(AudioManager.RINGER_MODE_VIBRATE, "vibrate")
            SoundSetting.RING -> setRingerMode(AudioManager.RINGER_MODE_NORMAL, "ring")
        }
    }

    /**
     * Priority-only rather than total silence for "DND on".
     *
     * [NotificationManager.INTERRUPTION_FILTER_NONE] blocks alarms too, which
     * would let an automation quietly disable the alarm clock — including
     * Remiit's own. Priority is also what the system's own Do Not Disturb tile
     * does, so the automation leaves the phone in a state the user recognises.
     */
    private fun setInterruptionFilter(filter: Int, what: String): ActionResult {
        val manager = notifications ?: return ActionResult.Failed("No notification service")
        return runCatching {
            manager.setInterruptionFilter(filter)
            ActionResult.Ok
        }.getOrElse {
            Log.e(TAG, "Could not $what", it)
            ActionResult.Failed("Android refused to $what")
        }
    }

    private fun setRingerMode(mode: Int, what: String): ActionResult {
        val manager = audio ?: return ActionResult.Failed("No audio service")
        return runCatching {
            manager.ringerMode = mode
            ActionResult.Ok
        }.getOrElse {
            // The realistic failure: moving out of silent while Do Not Disturb
            // is on, which needs policy access the user has not granted. It
            // cannot be predicted when the automation is written, only when it
            // runs, so this is where it gets explained.
            Log.e(TAG, "Could not set ringer to $what", it)
            if (!hasDndAccess()) {
                ActionResult.Failed("Could not $what — needs Do Not Disturb access")
            } else {
                ActionResult.Failed("Android refused to $what")
            }
        }
    }

    override fun applyAutoBrightness(enabled: Boolean): ActionResult {
        if (!canWriteSystemSettings()) {
            return ActionResult.Failed("Modify system settings not granted")
        }
        val target = if (enabled) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }
        return runCatching {
            val written = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                target,
            )
            // putInt reports failure by returning false rather than throwing,
            // which is the case on devices whose OEM brightness implementation
            // does not use this setting at all.
            if (written) ActionResult.Ok
            else ActionResult.Failed("This device did not accept the brightness change")
        }.getOrElse {
            Log.e(TAG, "Could not set adaptive brightness", it)
            ActionResult.Failed("Android refused the brightness change")
        }
    }

    override fun applyVolume(stream: VolumeStream, percent: Int): ActionResult {
        val manager = audio ?: return ActionResult.Failed("No audio service")
        if (volumeRequiresDndAccess(stream, percent) && !hasDndAccess()) {
            return ActionResult.Failed(
                "Muting ${stream.name.lowercase()} needs Do Not Disturb access"
            )
        }

        val androidStream = stream.androidStream
        return runCatching {
            val index = volumeIndexFor(
                percent = percent,
                min = manager.getStreamMinVolume(androidStream),
                max = manager.getStreamMaxVolume(androidStream),
            )
            // No flags: an automation changing the volume should not throw the
            // system volume panel over whatever the user is looking at.
            manager.setStreamVolume(androidStream, index, 0)
            ActionResult.Ok
        }.getOrElse {
            // The realistic failure is the same one the ringer has: the change
            // would cross the Do Not Disturb boundary — raising a muted stream
            // while DND is on — which cannot be predicted when the automation
            // is written, only when it runs.
            Log.e(TAG, "Could not set ${stream.name} volume", it)
            if (!hasDndAccess()) {
                ActionResult.Failed(
                    "Could not set ${stream.name.lowercase()} volume — needs " +
                        "Do Not Disturb access"
                )
            } else {
                ActionResult.Failed("Android refused the ${stream.name.lowercase()} volume change")
            }
        }
    }

    override fun readRinger(): RingerMode? = when (audio?.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
        AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
        AudioManager.RINGER_MODE_NORMAL -> RingerMode.RING
        else -> null
    }

    /**
     * Anything other than "all" counts as on — priority, alarms-only and total
     * silence are all Do Not Disturb to the user. Unknown means the service
     * could not say, which is recorded as nothing to restore.
     */
    override fun readDndOn(): Boolean? = when (notifications?.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> false
        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        NotificationManager.INTERRUPTION_FILTER_ALARMS,
        NotificationManager.INTERRUPTION_FILTER_NONE -> true
        // Named rather than left to an else, so a filter added in a future
        // Android is recorded as "unknown" instead of silently counted as on.
        NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> null
        else -> null
    }

    /** Reading needs no permission; only writing needs "Modify system settings". */
    override fun readAutoBrightness(): Boolean? = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrNull()

    override fun readVolume(stream: VolumeStream): Int? {
        val manager = audio ?: return null
        val androidStream = stream.androidStream
        return runCatching {
            volumePercentFor(
                index = manager.getStreamVolume(androidStream),
                min = manager.getStreamMinVolume(androidStream),
                max = manager.getStreamMaxVolume(androidStream),
            )
        }.getOrNull()
    }

    private val VolumeStream.androidStream: Int
        get() = when (this) {
            VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
            VolumeStream.RING -> AudioManager.STREAM_RING
            VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
            VolumeStream.ALARM -> AudioManager.STREAM_ALARM
            VolumeStream.SYSTEM -> AudioManager.STREAM_SYSTEM
        }

    private companion object {
        const val TAG = "DeviceControls"
    }
}
