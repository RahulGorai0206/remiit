package com.rahulgorai.remiit.trigger.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rahulgorai.remiit.engine.TriggerCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Rebuilds all OS-side trigger registrations after the events that wipe them.
 *
 * Alarms and geofences do not survive a reboot, and an app update clears them
 * too. Neither is visible to the user — the app looks fine and simply stops
 * reminding — so this receiver is the difference between a working app and one
 * that quietly dies after the first restart.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val coordinator: TriggerCoordinator by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val relevant = intent.action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            // Some HTC/Huawei builds send only this.
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
        if (!relevant) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                coordinator.reconcileAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore triggers after ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
