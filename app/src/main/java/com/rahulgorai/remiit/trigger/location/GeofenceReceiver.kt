package com.rahulgorai.remiit.trigger.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.rahulgorai.remiit.data.model.LocationEvent
import com.rahulgorai.remiit.data.model.shortSummary
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.engine.TriggerEvent
import com.rahulgorai.remiit.engine.TriggerSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Clock

/** Turns a geofence transition into a [TriggerEvent]. */
class GeofenceReceiver : BroadcastReceiver(), KoinComponent {

    private val repository: RuleRepository by inject()
    private val sink: TriggerSink by inject()
    private val clock: Clock by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "Geofence error code ${event.errorCode}")
            return
        }

        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> LocationEvent.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> LocationEvent.EXIT
            Geofence.GEOFENCE_TRANSITION_DWELL -> LocationEvent.DWELL
            else -> return
        }

        val ids = event.triggeringGeofences
            ?.mapNotNull { LocationTriggerMonitor.parseRequestId(it.requestId) }
            .orEmpty()
        if (ids.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ids.forEach { (ruleId, triggerId) ->
                    val rule = repository.rule(ruleId) ?: return@forEach
                    if (!rule.isEnabled) return@forEach
                    val trigger = rule.locationTriggers
                        .firstOrNull { it.id == triggerId && it.event == transition }
                        ?: return@forEach

                    sink.onTriggerFired(
                        TriggerEvent(
                            ruleId = ruleId,
                            triggerId = triggerId,
                            summary = trigger.shortSummary(),
                            firedAt = clock.instant(),
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling geofence transition", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TRANSITION = "com.rahulgorai.remiit.action.GEOFENCE"
        private const val TAG = "GeofenceReceiver"
    }
}
