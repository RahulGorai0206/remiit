package com.rahulgorai.remiit.trigger.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.WifiEvent
import com.rahulgorai.remiit.data.model.shortSummary
import com.rahulgorai.remiit.engine.TriggerEvent
import com.rahulgorai.remiit.engine.TriggerSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches Wi-Fi connect/disconnect and matches it against [Trigger.Wifi] rules.
 *
 * Uses a live [ConnectivityManager] network callback rather than a
 * manifest-declared receiver: the implicit Wi-Fi state broadcasts are
 * unavailable to manifest receivers on modern Android, so this only works from
 * a running process — which is why [com.rahulgorai.remiit.service.RemiitMonitorService]
 * exists.
 */
class WifiTriggerMonitor(
    private val context: Context,
    private val sink: TriggerSink,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)

    /** Rules with Wi-Fi triggers, refreshed by the coordinator. */
    @Volatile
    private var rules: List<ReminderRule> = emptyList()

    /**
     * SSID per network we are currently attached to.
     *
     * Keyed by [Network] rather than held as a single value for two reasons.
     * A disconnect has to be attributed to the network being *left*, and by the
     * time `onLost` fires its capabilities — and so its SSID — are already gone.
     * And when the phone roams between two APs the callbacks interleave: the
     * new network's `onCapabilitiesChanged` regularly arrives before the old
     * one's `onLost`, so a single field would have the disconnect wipe out the
     * SSID of the network just joined.
     */
    private val networkSsids = ConcurrentHashMap<Network, String>()

    private var callback: ConnectivityManager.NetworkCallback? = null

    val isRunning: Boolean get() = callback != null

    fun updateRules(rules: List<ReminderRule>) {
        this.rules = rules.filter { it.wifiTriggers.isNotEmpty() }
        Log.d(TAG, "Now watching ${this.rules.size} rule(s) with Wi-Fi triggers")
    }

    /**
     * Registers the callback. Safe to call repeatedly — the monitor service
     * calls it on every start command so a callback lost to a service restart
     * (or one that failed to register because a permission was still missing)
     * is re-armed rather than staying silently dead.
     */
    fun start() {
        if (callback != null) return
        val manager = connectivityManager ?: run {
            Log.e(TAG, "No ConnectivityManager; Wi-Fi rules cannot run")
            return
        }

        // Seed the network we are already on *before* registering. Registering a
        // callback replays the current network immediately, and without this
        // baseline that replay looks like a fresh connection — so saving an
        // "on connecting to X" rule while sitting on X would fire it at once,
        // and again on every process restart.
        seedCurrentNetwork(manager)

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // The SSID is not available in onAvailable — capabilities arrive
                // separately, and only this callback carries the transportInfo.
                val ssid = caps.ssid() ?: fallbackSsid()
                if (ssid == null) {
                    Log.w(TAG, "Connected to Wi-Fi but the SSID is unreadable; rule cannot match")
                    return
                }
                // put() returns the previous value: equal means this is just
                // another capabilities update (bandwidth, validation, captive
                // portal) for a network we are already counting as connected.
                val previous = networkSsids.put(network, ssid)
                if (previous == ssid) return

                Log.i(TAG, "Connected to $ssid")
                emit(ssid, WifiEvent.CONNECTED)
            }

            override fun onLost(network: Network) {
                val ssid = networkSsids.remove(network)
                if (ssid == null) {
                    Log.d(TAG, "Lost a Wi-Fi network we never had an SSID for")
                    return
                }
                Log.i(TAG, "Disconnected from $ssid")
                emit(ssid, WifiEvent.DISCONNECTED)
            }
        }

        try {
            manager.registerNetworkCallback(request, cb)
            callback = cb
            Log.i(TAG, "Wi-Fi callback registered")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot register network callback", e)
        }
    }

    fun stop() {
        callback?.let { cb ->
            runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
            callback = null
        }
        networkSsids.clear()
    }

    /** Records the currently connected network so its replay is not a "connect". */
    private fun seedCurrentNetwork(manager: ConnectivityManager) {
        val network = manager.activeNetwork ?: return
        val caps = manager.getNetworkCapabilities(network) ?: return
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
        val ssid = caps.ssid() ?: fallbackSsid() ?: return
        networkSsids[network] = ssid
        Log.d(TAG, "Baseline: already on $ssid")
    }

    /**
     * Reads the SSID out of the network's transport info.
     *
     * Requires ACCESS_FINE_LOCATION *and* location services switched on. When
     * either is missing the platform hands back the literal string
     * "<unknown ssid>" rather than throwing — so the rule silently never
     * matches, and that redaction has to be detected explicitly here.
     */
    private fun NetworkCapabilities.ssid(): String? {
        val info = transportInfo as? WifiInfo ?: return null
        return info.ssid.normaliseSsid()
    }

    /**
     * Last resort when `transportInfo` is absent, which happens on some OEM
     * builds. Subject to exactly the same location gating, so it recovers a
     * missing transport info but not a missing permission.
     */
    private fun fallbackSsid(): String? = runCatching {
        @Suppress("DEPRECATION")
        wifiManager?.connectionInfo?.ssid.normaliseSsid()
    }.getOrNull()

    private fun String?.normaliseSsid(): String? {
        val raw = this?.trim('"').orEmpty()
        return when {
            raw.isBlank() -> null
            raw.equals(UNKNOWN_SSID, ignoreCase = true) -> {
                Log.w(TAG, "SSID redacted — location permission or location services are off")
                null
            }
            else -> raw
        }
    }

    private fun emit(ssid: String, event: WifiEvent) {
        val matches = rules.flatMap { rule ->
            rule.wifiTriggers
                .filter { it.event == event && it.ssid.equals(ssid, ignoreCase = true) }
                .map { rule to it }
        }
        if (matches.isEmpty()) {
            Log.d(TAG, "$event $ssid matched no rule")
            return
        }

        scope.launch {
            matches.forEach { (rule, trigger) ->
                sink.onTriggerFired(
                    TriggerEvent(
                        ruleId = rule.id,
                        triggerId = trigger.id,
                        summary = trigger.shortSummary(),
                        firedAt = clock.instant(),
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "WifiTriggerMonitor"

        /**
         * What the platform substitutes for a real SSID when the caller lacks
         * location permission or location services are off. There is no public
         * constant for it on WifiInfo, so the literal is matched directly.
         */
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
