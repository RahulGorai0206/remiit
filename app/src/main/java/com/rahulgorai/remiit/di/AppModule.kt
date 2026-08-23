package com.rahulgorai.remiit.di

import com.rahulgorai.remiit.ai.KeywordRuleIntentParser
import com.rahulgorai.remiit.ai.RuleIntentParser
import com.rahulgorai.remiit.data.db.RemiitDatabase
import com.rahulgorai.remiit.data.prefs.SettingsStore
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.delivery.ReminderDispatcher
import com.rahulgorai.remiit.engine.RuleEngine
import com.rahulgorai.remiit.engine.TriggerCoordinator
import com.rahulgorai.remiit.engine.TriggerSink
import com.rahulgorai.remiit.trigger.applaunch.AppLaunchDispatcher
import com.rahulgorai.remiit.trigger.location.LocationTriggerMonitor
import com.rahulgorai.remiit.trigger.time.TimeTriggerScheduler
import com.rahulgorai.remiit.trigger.wifi.WifiTriggerMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.rahulgorai.remiit.ui.builder.RuleBuilderViewModel
import com.rahulgorai.remiit.ui.history.HistoryViewModel
import com.rahulgorai.remiit.ui.home.HomeViewModel
import com.rahulgorai.remiit.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.time.Clock

/**
 * Object graph for the app.
 *
 * Everything stateful is a singleton, which matters more here than in a typical
 * app: the trigger monitors hold OS registrations and the engine holds partial
 * match state, so a second instance would mean duplicate reminders or a match
 * that never completes.
 */
val appModule = module {

    // Injected rather than read from the system so the engine's cooldown and
    // per-day-cap logic is testable.
    single<Clock> { Clock.systemDefaultZone() }

    /**
     * Process-lifetime scope for work that outlives any screen — the rule
     * observer, the Wi-Fi callback and the app-launch dispatcher. SupervisorJob
     * so one failing collector cannot take the others down with it.
     */
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { RemiitDatabase.build(androidContext()) }
    single { get<RemiitDatabase>().ruleDao() }
    single { get<RemiitDatabase>().reminderEventDao() }
    single { RuleRepository(ruleDao = get(), eventDao = get(), clock = get()) }
    single { SettingsStore(androidContext()) }

    single { ReminderDispatcher(androidContext()) }

    single { RuleEngine(repository = get(), dispatcher = get(), clock = get()) }
    // The engine is the only TriggerSink. Bound separately so trigger sources
    // depend on the narrow interface rather than the whole engine.
    single<TriggerSink> { get<RuleEngine>() }

    single { TimeTriggerScheduler(context = androidContext(), clock = get()) }
    single { LocationTriggerMonitor(context = androidContext()) }
    single {
        WifiTriggerMonitor(
            context = androidContext(),
            sink = get(),
            scope = get(),
            clock = get(),
        )
    }
    single {
        AppLaunchDispatcher(
            context = androidContext(),
            sink = get(),
            scope = get(),
            clock = get(),
        )
    }

    single {
        TriggerCoordinator(
            context = androidContext(),
            repository = get(),
            settings = get(),
            timeScheduler = get(),
            locationMonitor = get(),
            wifiMonitor = get(),
            appLaunchDispatcher = get(),
            scope = get(),
        )
    }

    // Stub until an on-device model is wired in; see ai/RuleIntentParser.
    single<RuleIntentParser> { KeywordRuleIntentParser() }

    viewModel {
        HomeViewModel(
            repository = get(),
            coordinator = get(),
            engine = get(),
            clock = get(),
        )
    }
    viewModel {
        RuleBuilderViewModel(
            repository = get(),
            coordinator = get(),
            engine = get(),
            settings = get(),
        )
    }
    viewModel { HistoryViewModel(repository = get()) }
    viewModel { SettingsViewModel(settings = get()) }
}
