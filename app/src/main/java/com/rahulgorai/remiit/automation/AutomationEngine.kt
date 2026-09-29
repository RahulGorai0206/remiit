package com.rahulgorai.remiit.automation

import android.util.Log
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.AutomationActions
import com.rahulgorai.remiit.data.model.AutomationTrigger
import com.rahulgorai.remiit.data.model.DeviceSnapshot
import com.rahulgorai.remiit.data.model.SoundSetting
import com.rahulgorai.remiit.data.model.VolumeStream
import com.rahulgorai.remiit.data.model.asSoundSetting
import com.rahulgorai.remiit.data.model.changesDnd
import com.rahulgorai.remiit.data.model.changesRinger
import com.rahulgorai.remiit.data.model.summary
import com.rahulgorai.remiit.data.repo.AutomationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/**
 * Turns a signal into settings changes.
 *
 * The automation counterpart of [com.rahulgorai.remiit.engine.RuleEngine], and
 * a much smaller thing: there is no match mode to satisfy, no cooldown, and no
 * delivery to choose. An edge happened, and every enabled automation watching
 * for that edge runs. That simplicity is the reason automations are a separate
 * system rather than a delivery mode on a rule.
 *
 * What it does own is the record of what happened. Automations run unattended,
 * so an attempt that failed on a revoked permission has to leave a trace — see
 * [Automation.lastResult].
 */
class AutomationEngine(
    private val repository: AutomationRepository,
    private val controls: DeviceControls,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AutomationSink {

    /**
     * Serialises runs.
     *
     * Two automations reacting to the same moment — leaving the house drops
     * Wi-Fi *and* crosses a geofence — would otherwise race to set the ringer,
     * and the phone would end up in whichever state won. Serialised, the
     * outcome is at least deterministic and the record says what ran last.
     */
    private val mutex = Mutex()

    override suspend fun onSignal(signal: AutomationSignal) {
        mutex.withLock {
            try {
                val automations = repository.enabled()
                val toRestore = automations.filter { it.restoreOnExit && it matchesOpposite signal }
                val toApply = automations.filter { it matches signal }
                if (toApply.isEmpty() && toRestore.isEmpty()) {
                    Log.d(TAG, "$signal matched no automation")
                    return
                }
                // Restores first, then applies. When one automation is putting
                // things back and another is setting them outright on the same
                // edge — "silent at the office, restore on leaving" alongside
                // "ring when I leave the office" — the explicit instruction is
                // the one the user wrote down, so it runs last and wins.
                toRestore.forEach { restore(it) }
                toApply.forEach { apply(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling $signal", e)
            }
        }
    }

    private suspend fun apply(automation: Automation) {
        // Snapshot before touching anything, and only if one is not already
        // held. Wi-Fi drops and reconnects all the time — a lift, a dead spot,
        // a roam between access points — and each reconnect is another
        // "arrived". Taking a fresh snapshot on the second one would record the
        // phone as silent, because the first one silenced it, and leaving
        // would then faithfully restore silence.
        //
        // Written before the changes rather than after, so a process killed
        // half-way through applying still leaves something to restore from.
        if (automation.restoreOnExit && automation.savedState == null) {
            val snapshot = capture(automation.actions)
            if (!snapshot.isEmpty) repository.setSavedState(automation.id, snapshot)
        }
        runActions(automation)
    }

    /** Reads exactly the settings [actions] is about to change, and nothing else. */
    private fun capture(actions: AutomationActions) = DeviceSnapshot(
        ringer = if (actions.sound?.changesRinger == true) controls.readRinger() else null,
        dndOn = if (actions.sound?.changesDnd == true) controls.readDndOn() else null,
        autoBrightness = if (actions.autoBrightness != null) controls.readAutoBrightness() else null,
        volumes = actions.volumes.keys
            .mapNotNull { stream -> controls.readVolume(stream)?.let { stream to it } }
            .toMap(),
        takenAtEpochMillis = clock.millis(),
    )

    private suspend fun restore(automation: Automation) {
        // Nothing held means either the apply edge never happened — the phone
        // booted already at the office, say — or this edge has already been
        // handled. Both mean there is nothing of ours to undo, and touching the
        // settings anyway would override whatever the user has set since.
        val snapshot = automation.savedState ?: return

        // Same order as applying: ringer and Do Not Disturb, then levels, then
        // brightness. Restoring a ringer mode can itself move the ring volume,
        // so the recorded level has to be written after it, not before.
        val failures = buildList {
            snapshot.ringer?.let { mode ->
                (controls.applySound(mode.asSoundSetting) as? ActionResult.Failed)
                    ?.let { add(it.reason) }
            }
            snapshot.dndOn?.let { on ->
                val setting = if (on) SoundSetting.DND_ON else SoundSetting.DND_OFF
                (controls.applySound(setting) as? ActionResult.Failed)?.let { add(it.reason) }
            }
            VolumeStream.entries.forEach { stream ->
                snapshot.volumes[stream]?.let { percent ->
                    (controls.applyVolume(stream, percent) as? ActionResult.Failed)
                        ?.let { add(it.reason) }
                }
            }
            snapshot.autoBrightness?.let { enabled ->
                (controls.applyAutoBrightness(enabled) as? ActionResult.Failed)
                    ?.let { add(it.reason) }
            }
        }

        // Cleared even if a write failed. Kept, it would be restored at the
        // next departure — a state from some earlier day, stamped over
        // whatever the phone has been set to since. The failure is recorded on
        // the card instead, which is where the user can do something about it.
        repository.setSavedState(automation.id, null)

        val result = if (failures.isEmpty()) {
            "Restored ${snapshot.summary()}"
        } else {
            failures.joinToString(" · ")
        }
        Log.i(TAG, "Restored '${automation.name}': $result")
        repository.recordRun(automation.id, result, succeeded = failures.isEmpty())
    }

    private suspend fun runActions(automation: Automation) {
        val actions = automation.actions
        val failures = buildList {
            actions.sound?.let { setting ->
                (controls.applySound(setting) as? ActionResult.Failed)?.let { add(it.reason) }
            }
            // Volumes after the ringer mode, deliberately. The two can
            // contradict each other — silent plus "ring 50%" is a thing someone
            // can configure — and an explicit level is the more specific
            // instruction of the two, so it is the one that should win. Fixed
            // order also means the result is the same every time rather than
            // depending on which ran first.
            VolumeStream.entries.forEach { stream ->
                actions.volumes[stream]?.let { percent ->
                    (controls.applyVolume(stream, percent) as? ActionResult.Failed)?.let {
                        add(it.reason)
                    }
                }
            }
            actions.autoBrightness?.let { enabled ->
                (controls.applyAutoBrightness(enabled) as? ActionResult.Failed)?.let {
                    add(it.reason)
                }
            }
        }

        val result = if (failures.isEmpty()) {
            actions.summary().ifBlank { "Nothing to do" }
        } else {
            failures.joinToString(" · ")
        }

        Log.i(TAG, "Ran '${automation.name}': $result")
        repository.recordRun(automation.id, result, succeeded = failures.isEmpty())
    }

    private companion object {
        const val TAG = "AutomationEngine"
    }
}

/**
 * Whether this automation is the one the signal describes.
 *
 * Takes the whole automation rather than just its trigger because a geofence
 * signal identifies its automation by id, and a trigger does not know its own
 * id — matching on the trigger alone would fire every location automation on
 * every transition.
 *
 * SSIDs compare case-insensitively because the platform's capitalisation of a
 * network name is not guaranteed to match what the user typed; Bluetooth
 * addresses do too, since the same device is reported as `AA:BB` by one API and
 * `aa:bb` by another.
 */
internal infix fun Automation.matches(signal: AutomationSignal): Boolean =
    watches(signal) && trigger.edge == signal.edge

/**
 * The same network, device or place, crossed the other way — the edge that
 * restores. Kept separate from [matches] rather than folded into it, so a
 * restore can never be mistaken for an apply or the other way round.
 */
internal infix fun Automation.matchesOpposite(signal: AutomationSignal): Boolean =
    watches(signal) && trigger.edge != signal.edge

/** Whether the signal is about the thing this automation watches, in either direction. */
private fun Automation.watches(signal: AutomationSignal): Boolean {
    val trigger = this.trigger
    return when {
        trigger is AutomationTrigger.Wifi && signal is AutomationSignal.Wifi ->
            trigger.ssid.equals(signal.ssid, ignoreCase = true)

        trigger is AutomationTrigger.Bluetooth && signal is AutomationSignal.Bluetooth ->
            trigger.address.equals(signal.address, ignoreCase = true)

        // The id is what attributes a geofence transition — the fence carries
        // no coordinates back to compare.
        trigger is AutomationTrigger.Location && signal is AutomationSignal.Geofence ->
            id == signal.automationId

        else -> false
    }
}
