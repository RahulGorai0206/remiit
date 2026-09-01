package com.rahulgorai.remiit.trigger.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Read-only helpers for the rule builder's network picker.
 *
 * A note on what is *not* here: the list of networks the phone has saved.
 * `WifiManager.getConfiguredNetworks()` has returned only the caller's own
 * networks — so, for a third-party app, an empty list — since Android 10, and
 * there is no replacement. The closest honest equivalent is what this offers:
 * the network you are on, the ones in range right now, and the ones you have
 * already used in a rule.
 */
object WifiNetworks {

    /** SSID of the currently connected network, or null if unavailable. */
    fun currentSsid(context: Context): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val info = caps.transportInfo as? WifiInfo ?: return null
        return info.ssid.normalise()
    }

    /**
     * SSIDs from the last scan the platform happens to have cached.
     *
     * Returns immediately and is often non-empty, because the system scans on
     * its own schedule. Used to fill the picker on open, with [refreshSsids]
     * running behind it.
     */
    fun cachedSsids(context: Context): List<String> {
        val wifi = context.getSystemService(WifiManager::class.java) ?: return emptyList()
        // Wrapped because the platform throws a SecurityException if the
        // permission is declared but not granted, rather than returning empty.
        return runCatching {
            @Suppress("DEPRECATION")
            wifi.scanResults
                .mapNotNull { it.SSID.normalise() }
                .distinct()
                .sortedBy { it.lowercase() }
        }.onFailure { Log.w(TAG, "Cannot read scan results", it) }.getOrDefault(emptyList())
    }

    /**
     * Asks for a fresh scan and waits for the results broadcast.
     *
     * `startScan` is deprecated and throttled to four calls per two minutes for
     * a foreground app, returning false once the throttle bites. Neither is
     * worth surfacing as an error: the platform scans on its own schedule, so
     * the cached results are usually current enough, and every failure path here
     * falls back to them rather than showing an empty list.
     */
    suspend fun refreshSsids(context: Context, timeoutMillis: Long = SCAN_TIMEOUT_MILLIS):
        List<String> {
        val wifi = context.getSystemService(WifiManager::class.java) ?: return emptyList()
        if (!wifi.isWifiEnabled) return emptyList()
        if (!canListNetworks(context)) {
            Log.w(TAG, "Cannot scan: needs location permission and location services on")
            return emptyList()
        }

        @Suppress("DEPRECATION")
        val started = runCatching { wifi.startScan() }
            .onFailure { Log.w(TAG, "startScan refused", it) }
            .getOrDefault(false)
        if (!started) {
            Log.d(TAG, "Scan request refused (throttled); using cached results")
            return cachedSsids(context)
        }

        return try {
            withTimeout(timeoutMillis) { awaitScanResults(context) }
            cachedSsids(context)
        } catch (e: TimeoutCancellationException) {
            Log.d(TAG, "Scan did not report back in time; using cached results")
            cachedSsids(context)
        }
    }

    /**
     * Suspends until the platform announces scan results.
     *
     * A false [WifiManager.EXTRA_RESULTS_UPDATED] means the scan itself failed
     * and the results are the previous ones — still worth returning, since stale
     * names are a better picker than none, but not worth waiting any longer for.
     */
    private suspend fun awaitScanResults(context: Context) =
        suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    val updated =
                        intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true
                    if (!updated) Log.d(TAG, "Scan completed without new results")
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            // NOT_EXPORTED because this is a protected system broadcast; the
            // flag is mandatory for context-registered receivers from API 34.
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }

    /** Whether Wi-Fi is on at all, so the picker can explain an empty result. */
    fun isWifiEnabled(context: Context): Boolean =
        context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true

    /**
     * Whether the app may list networks at all.
     *
     * Both halves are required and both fail the same way — a SecurityException
     * from `startScan`, or an empty list from `getScanResults` — so they are
     * checked together rather than discovered one at a time. NEARBY_WIFI_DEVICES
     * does *not* help here: it covers Wi-Fi Aware, P2P, RTT and local-only
     * hotspot, and the scanning APIs still require location however new the
     * device is.
     *
     * Conveniently this is the same gate the Wi-Fi *trigger* needs, so a picker
     * that cannot list networks is also telling the user their rule would never
     * have fired.
     */
    fun canListNetworks(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
            locationServicesEnabled(context)

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun locationServicesEnabled(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** Strips the quotes the platform wraps SSIDs in, and rejects redacted ones. */
    private fun String?.normalise(): String? {
        val raw = this?.trim('"').orEmpty()
        return raw.takeUnless { it.isBlank() || it.equals(UNKNOWN_SSID, ignoreCase = true) }
    }

    private const val TAG = "WifiNetworks"
    private const val SCAN_TIMEOUT_MILLIS = 8_000L

    /** See WifiTriggerMonitor.UNKNOWN_SSID — no public constant exists for this. */
    private const val UNKNOWN_SSID = "<unknown ssid>"
}
