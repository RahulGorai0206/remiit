package com.rahulgorai.remiit

import android.app.Application
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.delivery.NotificationChannels
import com.rahulgorai.remiit.di.appModule
import com.rahulgorai.remiit.engine.TriggerCoordinator
import com.rahulgorai.remiit.trigger.time.TriggerReconcileWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RemiitApplication : Application() {

    private val coordinator: TriggerCoordinator by inject()
    private val repository: RuleRepository by inject()
    private val scope: CoroutineScope by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@RemiitApplication)
            modules(appModule)
        }

        NotificationChannels.ensureCreated(this)

        // Start following the rule table. Every alarm, geofence and monitor is
        // registered from here, so a cold start is enough to bring the whole
        // app back to a working state.
        coordinator.start()

        // Safety net for alarms and geofences the OS dropped without telling us.
        TriggerReconcileWorker.enqueue(this)

        scope.launch {
            // Close out reminders the process died holding, so History does not
            // fill with rows stuck at PENDING.
            runCatching { repository.pruneAndExpire() }
        }
    }
}
