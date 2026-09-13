package com.rahulgorai.remiit.automation

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.AutomationEdge
import com.rahulgorai.remiit.data.model.AutomationTrigger
import com.rahulgorai.remiit.trigger.location.LocationTriggerMonitor
import com.rahulgorai.remiit.util.PENDING_INTENT_FLAGS
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Geofences for location automations.
 *
 * A second registration alongside
 * [com.rahulgorai.remiit.trigger.location.LocationTriggerMonitor] rather than a
 * shared one, and the separation is the point: Play Services identifies a
 * geofence set by the PendingIntent it was registered with, and both sides here
 * re-register their *whole* set whenever their table changes. Sharing one
 * PendingIntent would mean every automation edit wiping out the reminder
 * geofences and vice versa. Two intents, two receivers, two independent sets.
 */
class AutomationGeofences(private val context: Context) {

    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    val hasBackgroundLocation: Boolean
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** Replaces the registered set with the fences [automations] ask for. */
    @SuppressLint("MissingPermission")
    suspend fun sync(automations: List<Automation>): Boolean {
        val fences = automations
            .filter { it.isEnabled }
            .mapNotNull { automation ->
                (automation.trigger as? AutomationTrigger.Location)
                    ?.let { buildGeofence(automation.id, it) }
            }

        // Always clear first, so an edited or deleted automation stops firing.
        removeAll()
        if (fences.isEmpty()) return true

        if (!hasBackgroundLocation) {
            Log.w(TAG, "Background location missing; ${fences.size} automation fence(s) not set")
            return false
        }

        val request = GeofencingRequest.Builder()
            // No initial trigger: re-registering on every edit and every reboot
            // would otherwise re-run "arrived at home" each time simply because
            // the phone is already at home.
            .setInitialTrigger(0)
            .addGeofences(fences)
            .build()

        return suspendCancellableCoroutine { cont ->
            client.addGeofences(request, pendingIntent())
                .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register automation geofences", e)
                    if (cont.isActive) cont.resume(false)
                }
        }
    }

    suspend fun removeAll(): Boolean = suspendCancellableCoroutine { cont ->
        client.removeGeofences(pendingIntent())
            .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
            .addOnFailureListener { if (cont.isActive) cont.resume(false) }
    }

    private fun buildGeofence(automationId: String, trigger: AutomationTrigger.Location): Geofence? =
        runCatching {
            Geofence.Builder()
                // The automation id alone — unlike a rule, an automation has
                // exactly one trigger, so there is nothing further to encode.
                .setRequestId(automationId)
                .setCircularRegion(
                    trigger.latitude,
                    trigger.longitude,
                    // Shares the reminder side's floor: below roughly 100m,
                    // fused-location accuracy produces transitions at random.
                    trigger.radiusMeters.coerceAtLeast(LocationTriggerMonitor.MIN_RADIUS_METERS),
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    when (trigger.edge) {
                        AutomationEdge.ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
                        AutomationEdge.EXIT -> Geofence.GEOFENCE_TRANSITION_EXIT
                    }
                )
                .build()
        }.onFailure { Log.e(TAG, "Invalid geofence for automation $automationId", it) }.getOrNull()

    private fun pendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AutomationGeofenceReceiver::class.java)
                .setAction(AutomationGeofenceReceiver.ACTION_TRANSITION),
            PENDING_INTENT_FLAGS,
        )

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "AutomationGeofences"

        /** Distinct from the reminder side's, so the two PendingIntents differ. */
        const val REQUEST_CODE = 0x6E10
    }
}
