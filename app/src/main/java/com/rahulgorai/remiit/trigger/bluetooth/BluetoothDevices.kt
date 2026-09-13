package com.rahulgorai.remiit.trigger.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** A paired device, as shown in the automation editor's picker. */
data class PairedDevice(val address: String, val name: String)

/**
 * The paired-devices list behind the Bluetooth picker.
 *
 * Paired rather than in-range: an automation is written once and runs for
 * months, so it has to name a device the user owns rather than whatever
 * happened to be discoverable while they were on the editor screen. Bonded
 * devices are also the only list obtainable without a scan, and a scan would
 * mean location permission for no benefit.
 */
object BluetoothDevices {

    /** BLUETOOTH_CONNECT, which from Android 12 gates both the list and the names. */
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    /**
     * Paired devices, sorted by name. Empty when the permission is missing,
     * when Bluetooth is off, or when the device has no radio at all — the
     * picker distinguishes those cases through [hasPermission] and [isEnabled]
     * rather than by guessing from an empty list.
     */
    // Guarded by hasPermission on the first line, which lint cannot follow
    // through a helper — the same suppression WifiNetworks carries for the
    // same reason.
    @SuppressLint("MissingPermission")
    fun paired(context: Context): List<PairedDevice> {
        if (!hasPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        return runCatching {
            adapter.bondedDevices.orEmpty().mapNotNull { device ->
                val address = device.address ?: return@mapNotNull null
                PairedDevice(address = address, name = device.name?.takeIf { it.isNotBlank() } ?: address)
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }
}
