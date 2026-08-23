package com.rahulgorai.remiit.delivery

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.rahulgorai.remiit.data.model.DeliveryConfig

/**
 * Plays the looping alarm tone and vibration for [DeliveryMode.ALARM].
 *
 * A process-wide singleton on purpose. The sound is started by the overlay
 * activity but has to be stoppable from a notification action, a different
 * activity instance, or the auto-dismiss timeout — so there must be exactly one
 * player, and no way to end up with two alarms sounding at once.
 */
object AlarmSoundPlayer {

    private var player: MediaPlayer? = null
    private var vibratorManager: VibratorManager? = null
    private val lock = Any()

    val isPlaying: Boolean get() = synchronized(lock) { player != null }

    fun start(context: Context, config: DeliveryConfig) {
        synchronized(lock) {
            stopLocked()

            if (config.vibrate) startVibration(context, config)

            val uri = config.soundUri?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return

            try {
                player = MediaPlayer().apply {
                    // USAGE_ALARM routes to the alarm stream, so the reminder is
                    // audible even with media and ringtone volume at zero.
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    isLooping = true
                    if (config.escalateVolume) setVolume(INITIAL_VOLUME, INITIAL_VOLUME)
                    prepare()
                    start()
                }
                if (config.escalateVolume) escalate(context)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start alarm sound", e)
                stopLocked()
            }
        }
    }

    fun stop() = synchronized(lock) { stopLocked() }

    private fun stopLocked() {
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
        player = null
        runCatching { vibratorManager?.cancel() }
        vibratorManager = null
    }

    /**
     * Ramps volume up over a few seconds rather than opening at full blast.
     * Driven off MediaPlayer's own timeline via a short polling loop on a
     * daemon thread, so it stops as soon as the player is released.
     */
    private fun escalate(context: Context) {
        val target = player ?: return
        Thread {
            var step = 0
            while (step < ESCALATE_STEPS) {
                Thread.sleep(ESCALATE_STEP_MILLIS)
                synchronized(lock) {
                    val current = player
                    if (current == null || current !== target) return@Thread
                    step++
                    val volume = INITIAL_VOLUME +
                        (1f - INITIAL_VOLUME) * (step.toFloat() / ESCALATE_STEPS)
                    runCatching { current.setVolume(volume, volume) }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun startVibration(context: Context, config: DeliveryConfig) {
        val manager = context.getSystemService(VibratorManager::class.java) ?: return
        val pattern = config.vibrationPattern
            .takeIf { it.size >= 2 }
            ?.toLongArray()
            ?: DeliveryConfig.DEFAULT_VIBRATION_PATTERN.toLongArray()

        runCatching {
            manager.vibrate(
                CombinedVibration.createParallel(
                    // repeat = 0 loops the pattern from the start; the vibration
                    // has to persist as long as the sound does.
                    VibrationEffect.createWaveform(pattern, 0)
                ),
                // USAGE_ALARM so the vibration survives Do Not Disturb, matching
                // the audio stream the tone plays on.
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
            vibratorManager = manager
        }.onFailure { Log.w(TAG, "Could not start vibration", it) }
    }

    /** One-shot buzz used by the rule builder's Preview button. */
    fun previewVibration(context: Context, config: DeliveryConfig) {
        val manager = context.getSystemService(VibratorManager::class.java) ?: return
        val pattern = config.vibrationPattern.takeIf { it.size >= 2 }?.toLongArray()
            ?: DeliveryConfig.DEFAULT_VIBRATION_PATTERN.toLongArray()
        runCatching {
            manager.vibrate(
                CombinedVibration.createParallel(VibrationEffect.createWaveform(pattern, -1))
            )
        }
    }

    /** True when the alarm stream is muted, so the UI can warn the user. */
    fun isAlarmStreamSilent(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }

    private const val TAG = "AlarmSoundPlayer"
    private const val INITIAL_VOLUME = 0.2f
    private const val ESCALATE_STEPS = 8
    private const val ESCALATE_STEP_MILLIS = 700L
}
