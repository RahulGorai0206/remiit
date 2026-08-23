package com.rahulgorai.remiit.trigger.time

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.engine.TriggerCoordinator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Safety net. Re-registers every alarm and geofence, and tidies history.
 *
 * Registrations are normally maintained on save, on fire and on boot. This
 * catches the cases none of those cover: an alarm dropped because the process
 * was killed between firing and re-arming, geofences evicted by the OS under
 * memory pressure, or a manufacturer's aggressive battery manager clearing them
 * outright. All of those fail silently, which is why a periodic sweep is worth
 * the wakeup.
 */
class TriggerReconcileWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val coordinator: TriggerCoordinator by inject()
    private val repository: RuleRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            coordinator.reconcileAll()
            repository.pruneAndExpire()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "remiit-trigger-reconcile"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<TriggerReconcileWorker>(
                6, TimeUnit.HOURS,
            ).setConstraints(
                // No network or charging requirement — this is local bookkeeping
                // and delaying it defeats the point.
                Constraints.Builder().build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
