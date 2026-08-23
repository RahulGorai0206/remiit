package com.rahulgorai.remiit.trigger.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.util.Log
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.WifiEvent
import com.rahulgorai.remiit.data.model.shortSummary
import com.rahulgorai.remiit.engine.TriggerEvent
import com.rahulgorai.remiit.engine.TriggerSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference

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

    /** Rules with Wi-Fi triggers, refreshed by the coordinator. */
    @Volatile
    private var rules: List<ReminderRule> = emptyList()

    /**
     * SSID of the network we currently consider ourselves on.
     *
     * Held explicitly because DISCONNECTED has to be attributed to the network
     * being *left*, and by the time onLost fires its capabilities — and so its
     * SSID — are already gone.
     */
    private val currentSsid = AtomicReference<String?>(null)

    private var callback: ConnectivityManager.NetworkCallback? = null

    val isRunning: Boolean get() = callback != null

    fun updateRules(rules: List<ReminderRule>) {
        this.rules = rules.filter { it.wifiTriggers.isNotEmpty() }
    }

    fun start() {
        if (callback != null) return
        val manager = connectivityManager ?: return

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // The SSID is not available on onAvailable — capabilities arrive
                // separately, and only this callback carries the transportInfo.
                val ssid = caps.ssid()
                if (ssid != null && currentSsid.getAndSet(ssid) != ssid) {
                    emit(ssid, WifiEvent.CONNECTED)
                }
            }

            override fun onLost(network: Network) {
                currentSsid.getAndSet(null)?.let { emit(it, WifiEvent.DISCONNECTED) }
            }
        }

        try {
            manager.registerNetworkCallback(request, cb)
            callback = cb
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot register network callback", e)
        }
    }

    fun stop() {
        callback?.let { cb ->
            runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
            callback = null
        }
        currentSsid.set(null)
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
        val raw = info.ssid?.trim('"').orEmpty()
        return when {
            raw.isBlank() -> null
            raw == UNKNOWN_SSID -> null
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
        if (matches.isEmpty()) return

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
