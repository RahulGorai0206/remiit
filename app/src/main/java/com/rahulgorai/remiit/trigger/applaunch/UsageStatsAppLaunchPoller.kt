package com.rahulgorai.remiit.trigger.applaunch

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * App-launch detection by polling [UsageStatsManager].
 *
 * The Play-policy-safe alternative to the accessibility route. It needs only
 * the "usage access" special grant, but there is no callback — the only way to
 * learn about a foreground change is to ask, so this polls on a short interval
 * from inside the monitor foreground service.
 *
 * That has real costs the accessibility path does not: up to
 * [POLL_INTERVAL_MILLIS] of latency, a permanent foreground-service
 * notification, and steady (if small) battery use. Which is why the choice is
 * surfaced to the user rather than decided here.
 */
class UsageStatsAppLaunchPoller(
    private val context: Context,
    private val dispatcher: AppLaunchDispatcher,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        if (!hasUsageAccess(context)) {
            Log.w(TAG, "Usage access not granted; app-launch rules will not fire")
            return
        }

        job = scope.launch {
            // Start from now, not from an arbitrary window in the past, so the
            // app does not replay launches that happened before it started.
            var since = clock.millis()
            while (isActive) {
                val now = clock.millis()
                try {
                    since = pollOnce(since, now)
                } catch (e: Exception) {
                    Log.e(TAG, "Usage stats poll failed", e)
                    since = now
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Reads events in (since, now] and reports the last package to come
     * forward. Returns the timestamp to resume from.
     *
     * Only the newest ACTIVITY_RESUMED in the window is reported: a poll that
     * spans several switches means the user moved through apps faster than the
     * interval, and firing a reminder for each intermediate one would be noise.
     */
    private fun pollOnce(since: Long, now: Long): Long {
        val manager = usageStatsManager ?: return now
        // Query slightly wider than the window: the service reports events with
        // a small lag, and a tight range drops launches near the boundary.
        val events = manager.queryEvents(since - QUERY_SLACK_MILLIS, now)
        val event = UsageEvents.Event()

        var latestPackage: String? = null
        var latestAt = since

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            if (event.timeStamp <= since) continue
            if (event.timeStamp >= latestAt) {
                latestAt = event.timeStamp
                latestPackage = event.packageName
            }
        }

        latestPackage?.let(dispatcher::onAppForegrounded)
        return maxOf(now, latestAt)
    }

    companion object {
        private const val TAG = "UsageStatsPoller"

        /**
         * One second is the practical floor: longer and switching apps quickly
         * misses launches, shorter and the battery cost stops being defensible
         * for what is still a polling loop.
         */
        private const val POLL_INTERVAL_MILLIS = 1_000L
        private const val QUERY_SLACK_MILLIS = 2_000L

        /**
         * Whether the "usage access" grant is held. Checked through AppOps
         * rather than a permission check because PACKAGE_USAGE_STATS is a
         * special-access permission that never appears as granted to
         * checkSelfPermission.
         */
        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
