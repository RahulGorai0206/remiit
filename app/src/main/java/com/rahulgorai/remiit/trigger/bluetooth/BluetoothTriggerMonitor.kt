package com.rahulgorai.remiit.trigger.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.rahulgorai.remiit.automation.AutomationSignal
import com.rahulgorai.remiit.automation.AutomationSink
import com.rahulgorai.remiit.data.model.AutomationEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Watches Bluetooth devices connecting and disconnecting.
 *
 * ACL connect/disconnect are system broadcasts and have to be received by a
 * receiver registered from running code — a manifest-declared one is not
 * delivered these, which is why this lives in
 * [com.rahulgorai.remiit.service.RemiitMonitorService] alongside the Wi-Fi
 * callback rather than standing on its own.
 *
 * Unlike Wi-Fi there is no baseline to seed. A network callback replays the
 * network you are already on the moment it registers, and that replay has to be
 * told apart from a real connection; ACL broadcasts are only ever sent on an
 * actual transition, so a device already connected when the service starts
 * stays silent, which is the correct behaviour.
 *
 * Reading the device *address* needs no permission. Reading its name does, from
 * Android 12 — so nothing here touches the name, and the label shown in the UI
 * comes from what was cached when the automation was created.
 */
class BluetoothTriggerMonitor(
    private val context: Context,
    private val sink: AutomationSink,
    private val scope: CoroutineScope,
) {
    private var receiver: BroadcastReceiver? = null

    val isRunning: Boolean get() = receiver != null

    /** Safe to call repeatedly; the service calls it on every start command. */
    fun start() {
        if (receiver != null) return

        val rx = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val edge = when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> AutomationEdge.ENTER
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> AutomationEdge.EXIT
                    else -> return
                }
                val device = intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java,
                ) ?: return

                val address = device.address ?: return
                Log.i(TAG, "$edge $address")
                scope.launch {
                    sink.onSignal(AutomationSignal.Bluetooth(address, edge))
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        // These are protected system broadcasts, so NOT_EXPORTED is both
        // correct and sufficient — nothing but the system can send them. The
        // fallback exists because a handful of OEM builds have been seen to
        // reject that flag for system actions, and losing Bluetooth automations
        // on those devices is worse than accepting a broadcast that, being a
        // protected action, another app still cannot send.
        receiver = runCatching {
            ContextCompat.registerReceiver(
                context, rx, filter, ContextCompat.RECEIVER_NOT_EXPORTED
            )
            rx
        }.recoverCatching {
            Log.w(TAG, "NOT_EXPORTED registration refused; retrying as exported", it)
            ContextCompat.registerReceiver(
                context, rx, filter, ContextCompat.RECEIVER_EXPORTED
            )
            rx
        }.onFailure {
            Log.e(TAG, "Could not register Bluetooth receiver", it)
        }.getOrNull()

        if (receiver != null) Log.i(TAG, "Bluetooth receiver registered")
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private companion object {
        const val TAG = "BluetoothTriggerMonitor"
    }
}
