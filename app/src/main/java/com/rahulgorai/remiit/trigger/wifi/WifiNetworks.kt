package com.rahulgorai.remiit.trigger.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager

/**
 * Read-only helpers for the rule builder's SSID picker.
 *
 * Deliberately narrow: the app never scans for networks (that needs extra
 * permissions and a user-visible justification it does not need). It only
 * offers the network you are on right now plus whatever you have typed before,
 * which covers the realistic case of setting up an "office Wi-Fi" rule while
 * sitting in the office.
 */
object WifiNetworks {

    /** SSID of the currently connected network, or null if unavailable. */
    fun currentSsid(context: Context): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val info = caps.transportInfo as? WifiInfo ?: return null
        val raw = info.ssid?.trim('"').orEmpty()
        return raw.takeUnless {
            it.isBlank() || it.equals(UNKNOWN_SSID, ignoreCase = true)
        }
    }

    /** See WifiTriggerMonitor.UNKNOWN_SSID — no public constant exists for this. */
    private const val UNKNOWN_SSID = "<unknown ssid>"

    /** Whether Wi-Fi is on at all, so the picker can explain an empty result. */
    fun isWifiEnabled(context: Context): Boolean =
        context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
}
