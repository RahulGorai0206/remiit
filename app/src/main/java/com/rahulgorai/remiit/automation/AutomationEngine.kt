package com.rahulgorai.remiit.automation

import android.util.Log
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.AutomationTrigger
import com.rahulgorai.remiit.data.model.summary
import com.rahulgorai.remiit.data.repo.AutomationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
                val matches = repository.enabled().filter { it matches signal }
                if (matches.isEmpty()) {
                    Log.d(TAG, "$signal matched no automation")
                    return
                }
                matches.forEach { runActions(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling $signal", e)
            }
        }
    }

    private suspend fun runActions(automation: Automation) {
        val actions = automation.actions
        val failures = buildList {
            actions.sound?.let { setting ->
                (controls.applySound(setting) as? ActionResult.Failed)?.let { add(it.reason) }
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
internal infix fun Automation.matches(signal: AutomationSignal): Boolean {
    val trigger = this.trigger
    return when {
        trigger is AutomationTrigger.Wifi && signal is AutomationSignal.Wifi ->
            trigger.edge == signal.edge && trigger.ssid.equals(signal.ssid, ignoreCase = true)

        trigger is AutomationTrigger.Bluetooth && signal is AutomationSignal.Bluetooth ->
            trigger.edge == signal.edge &&
                trigger.address.equals(signal.address, ignoreCase = true)

        trigger is AutomationTrigger.Location && signal is AutomationSignal.Geofence ->
            // The id is what attributes the transition; the edge is still
            // checked because the OS can report a transition for a fence it
            // holds from before an edit changed which edge was wanted.
            id == signal.automationId && trigger.edge == signal.edge

        else -> false
    }
}
