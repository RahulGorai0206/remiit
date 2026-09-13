package com.rahulgorai.remiit.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.rahulgorai.remiit.data.model.AutomationEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Turns a geofence transition into an [AutomationSignal.Geofence]. */
class AutomationGeofenceReceiver : BroadcastReceiver(), KoinComponent {

    private val sink: AutomationSink by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "Geofence error code ${event.errorCode}")
            return
        }

        val edge = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> AutomationEdge.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> AutomationEdge.EXIT
            else -> return
        }

        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return

        // goAsync, because applying a settings change is not instant and a
        // receiver that returns first has its process eligible for death
        // mid-write.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ids.forEach { sink.onSignal(AutomationSignal.Geofence(it, edge)) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling automation geofence transition", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TRANSITION = "com.rahulgorai.remiit.action.AUTOMATION_GEOFENCE"
        private const val TAG = "AutomationGeofence"
    }
}
