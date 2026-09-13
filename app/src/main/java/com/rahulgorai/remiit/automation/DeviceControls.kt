package com.rahulgorai.remiit.automation

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import com.rahulgorai.remiit.data.model.SoundSetting

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
}

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

    private companion object {
        const val TAG = "DeviceControls"
    }
}
