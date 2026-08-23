package com.rahulgorai.remiit.trigger.location

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
import com.rahulgorai.remiit.data.model.LocationEvent
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.util.PENDING_INTENT_FLAGS
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Registers circular geofences for [Trigger.Location].
 *
 * Play Services geofences are used rather than polling location because the OS
 * evaluates them itself: no foreground service, no wakelock, and battery cost
 * close to nothing. The price is ACCESS_BACKGROUND_LOCATION, which on Android
 * 11+ can only be requested as a separate second step after foreground location
 * has already been granted.
 */
class LocationTriggerMonitor(
    private val context: Context,
) {
    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    val hasBackgroundLocation: Boolean
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /**
     * Replaces the registered geofence set with the ones [rules] ask for.
     *
     * Registration is wholesale rather than incremental: geofences are keyed by
     * request id, so re-adding the full set is idempotent and avoids having to
     * track which ones the OS still holds after a reboot or an eviction.
     */
    @SuppressLint("MissingPermission")
    suspend fun sync(rules: List<ReminderRule>): Boolean {
        val geofences = rules
            .filter { it.isEnabled }
            .flatMap { rule -> rule.locationTriggers.map { rule to it } }
            .mapNotNull { (rule, trigger) -> buildGeofence(rule, trigger) }

        // Always clear first so a removed or edited trigger stops firing.
        removeAll()
        if (geofences.isEmpty()) return true

        if (!hasBackgroundLocation) {
            Log.w(TAG, "Background location not granted; ${geofences.size} geofence(s) not registered")
            return false
        }

        val request = GeofencingRequest.Builder()
            // No INITIAL_TRIGGER_ENTER: re-registering on every boot and every
            // periodic reconcile would otherwise fire an "arrived at office"
            // reminder each time simply because you were already there.
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()

        return suspendCancellableCoroutine { cont ->
            client.addGeofences(request, geofencePendingIntent())
                .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register geofences", e)
                    if (cont.isActive) cont.resume(false)
                }
        }
    }

    suspend fun removeAll(): Boolean = suspendCancellableCoroutine { cont ->
        client.removeGeofences(geofencePendingIntent())
            .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
            .addOnFailureListener { if (cont.isActive) cont.resume(false) }
    }

    private fun buildGeofence(rule: ReminderRule, trigger: Trigger.Location): Geofence? {
        val transition = when (trigger.event) {
            LocationEvent.ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
            LocationEvent.EXIT -> Geofence.GEOFENCE_TRANSITION_EXIT
            LocationEvent.DWELL -> Geofence.GEOFENCE_TRANSITION_DWELL
        }

        return runCatching {
            Geofence.Builder()
                .setRequestId(requestId(rule.id, trigger.id))
                // A radius under ~100m produces constant false transitions on
                // fused location accuracy; clamp rather than let a user build a
                // rule that fires at random.
                .setCircularRegion(
                    trigger.latitude,
                    trigger.longitude,
                    trigger.radiusMeters.coerceAtLeast(MIN_RADIUS_METERS),
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(transition)
                .apply {
                    if (trigger.event == LocationEvent.DWELL) {
                        setLoiteringDelay(
                            TimeUnit.MINUTES.toMillis(trigger.dwellMinutes.coerceAtLeast(1).toLong())
                                .toInt()
                        )
                    }
                }
                .build()
        }.onFailure {
            Log.e(TAG, "Invalid geofence for rule ${rule.id}", it)
        }.getOrNull()
    }

    private fun geofencePendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQUEST_CODE,
            Intent(context, GeofenceReceiver::class.java).setAction(GeofenceReceiver.ACTION_TRANSITION),
            PENDING_INTENT_FLAGS,
        )

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "LocationTriggerMonitor"
        private const val GEOFENCE_REQUEST_CODE = 0x6E0F
        const val MIN_RADIUS_METERS = 100f

        /**
         * Geofence request ids encode the rule and trigger so
         * [GeofenceReceiver] can attribute a transition without a lookup table.
         */
        fun requestId(ruleId: String, triggerId: String) = "$ruleId$SEPARATOR$triggerId"

        fun parseRequestId(requestId: String): Pair<String, String>? {
            val index = requestId.lastIndexOf(SEPARATOR)
            if (index <= 0) return null
            return requestId.substring(0, index) to
                requestId.substring(index + SEPARATOR.length)
        }

        // UUIDs contain hyphens, so a single-character separator would be
        // ambiguous when splitting the id back apart.
        private const val SEPARATOR = "::"
    }
}
