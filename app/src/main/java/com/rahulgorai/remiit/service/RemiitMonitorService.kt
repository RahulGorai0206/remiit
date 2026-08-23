package com.rahulgorai.remiit.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rahulgorai.remiit.R
import com.rahulgorai.remiit.data.prefs.AppLaunchDetectorKind
import com.rahulgorai.remiit.data.prefs.SettingsStore
import com.rahulgorai.remiit.delivery.NotificationChannels
import com.rahulgorai.remiit.trigger.applaunch.AppLaunchDispatcher
import com.rahulgorai.remiit.trigger.applaunch.UsageStatsAppLaunchPoller
import com.rahulgorai.remiit.trigger.wifi.WifiTriggerMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject

/**
 * Hosts the trigger sources that need a live process.
 *
 * Only Wi-Fi callbacks and usage-stats polling need this. Time triggers go
 * through AlarmManager and location through geofences, both evaluated by the OS,
 * so a user with only those rules never sees this service or its notification —
 * [com.rahulgorai.remiit.engine.TriggerCoordinator] starts it only when a rule
 * actually requires it.
 */
class RemiitMonitorService : Service() {

    private val wifiMonitor: WifiTriggerMonitor by inject()
    private val appLaunchDispatcher: AppLaunchDispatcher by inject()
    private val settings: SettingsStore by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var usageStatsPoller: UsageStatsAppLaunchPoller? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        startForegroundCompat()

        wifiMonitor.start()

        // The poller is created and torn down as the preference changes, so
        // switching detectors in Settings takes effect without a restart.
        settings.appLaunchDetector
            .onEach { kind ->
                if (kind == AppLaunchDetectorKind.USAGE_STATS) {
                    if (usageStatsPoller == null) {
                        usageStatsPoller = UsageStatsAppLaunchPoller(
                            context = this,
                            dispatcher = appLaunchDispatcher,
                            scope = scope,
                        ).also { it.start() }
                    }
                } else {
                    usageStatsPoller?.stop()
                    usageStatsPoller = null
                }
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY so the OS brings the service back if it is killed for
        // memory: a monitor that silently stays dead means rules stop firing
        // with no visible symptom.
        return START_STICKY
    }

    override fun onDestroy() {
        wifiMonitor.stop()
        usageStatsPoller?.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val notification: Notification =
            NotificationCompat.Builder(this, NotificationChannels.MONITOR)
                .setContentTitle(getString(R.string.channel_monitor_name))
                .setContentText(getString(R.string.channel_monitor_description))
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setOngoing(true)
                // Low priority and no sound: this notification is a disclosure
                // requirement, not something the user should be nudged by.
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build()

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            // Android 14+ throws if the app is not allowed to start a
            // foreground service from the background. Nothing to recover to, so
            // stop cleanly rather than leaving a half-started service.
            Log.e(TAG, "Could not enter foreground; stopping", e)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "RemiitMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.rahulgorai.remiit.action.STOP_MONITOR"

        fun start(context: Context) {
            val intent = Intent(context, RemiitMonitorService::class.java)
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.w(TAG, "Could not start monitor service", it) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, RemiitMonitorService::class.java).setAction(ACTION_STOP)
                )
            }.onFailure {
                // Starting a service to stop it fails if the app is in the
                // background; stopService is the fallback.
                runCatching { context.stopService(Intent(context, RemiitMonitorService::class.java)) }
            }
        }
    }
}
