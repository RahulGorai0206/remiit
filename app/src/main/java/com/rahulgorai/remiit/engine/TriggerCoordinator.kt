package com.rahulgorai.remiit.engine

import android.content.Context
import android.util.Log
import com.rahulgorai.remiit.automation.AutomationGeofences
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.AutomationTrigger
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.repo.AutomationRepository
import com.rahulgorai.remiit.service.RemiitMonitorService
import com.rahulgorai.remiit.trigger.applaunch.AppLaunchDispatcher
import com.rahulgorai.remiit.trigger.location.LocationTriggerMonitor
import com.rahulgorai.remiit.trigger.time.TimeTriggerScheduler
import com.rahulgorai.remiit.trigger.wifi.WifiTriggerMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.rahulgorai.remiit.data.repo.RuleRepository

/**
 * Keeps the OS-side registrations in step with the database.
 *
 * Everything is driven off the enabled-rules and enabled-automations flows, so
 * toggling either is the only action needed to arm or tear down its alarms,
 * geofences and monitors. There is no separate "apply" step that could fall out
 * of sync with what the database says.
 *
 * Both tables are handled here, by one class, for one reason: they share the
 * monitor service. If rules and automations each decided independently whether
 * the service should run, the one with nothing to watch would keep stopping the
 * service the other still needed. Combining the two flows makes that a single
 * decision taken from a single consistent view.
 */
class TriggerCoordinator(
    private val context: Context,
    private val repository: RuleRepository,
    private val timeScheduler: TimeTriggerScheduler,
    private val locationMonitor: LocationTriggerMonitor,
    private val wifiMonitor: WifiTriggerMonitor,
    private val appLaunchDispatcher: AppLaunchDispatcher,
    private val automationRepository: AutomationRepository,
    private val automationGeofences: AutomationGeofences,
    private val scope: CoroutineScope,
) {
    /** Starts following both tables. Called once, from the Application. */
    fun start() {
        combine(
            repository.observeEnabledRules(),
            automationRepository.observeEnabled(),
        ) { rules, automations -> rules to automations }
            .distinctUntilChanged()
            .onEach { (rules, automations) -> apply(rules, automations) }
            .launchIn(scope)
    }

    /**
     * Rebuilds every registration from the current rule table.
     *
     * Called after boot, after an app update, and periodically — all cases where
     * the OS has thrown away alarms and geofences without telling the app.
     */
    suspend fun reconcileAll() {
        apply(repository.enabledRules(), automationRepository.enabled())
    }

    private suspend fun apply(rules: List<ReminderRule>, automations: List<Automation>) {
        try {
            timeScheduler.rescheduleAll(rules)
            locationMonitor.sync(rules)
            automationGeofences.sync(automations)

            wifiMonitor.updateRules(rules)
            appLaunchDispatcher.updateRules(rules)

            // The foreground service is only started when something actually
            // needs a live process. A setup of purely time and location rules
            // runs with no persistent notification at all, because AlarmManager
            // and geofences are evaluated by the OS.
            //
            // Automations add Bluetooth to that list. Its connect/disconnect
            // broadcasts are only delivered to a receiver registered from
            // running code, so a Bluetooth automation needs the service for
            // exactly the same reason a Wi-Fi rule does.
            val needsService = rules.any {
                it.wifiTriggers.isNotEmpty() || it.appLaunchTriggers.isNotEmpty()
            } || automations.any { it.trigger !is AutomationTrigger.Location }

            if (needsService) {
                RemiitMonitorService.start(context)
            } else {
                RemiitMonitorService.stop(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply trigger registrations", e)
        }
    }

    /** Applies a single rule's registrations immediately after a save. */
    fun onRuleSaved(rule: ReminderRule) {
        scope.launch {
            if (rule.isEnabled) timeScheduler.schedule(rule) else timeScheduler.cancel(rule)
            reconcileAll()
        }
    }

    fun onRuleDeleted(rule: ReminderRule) {
        scope.launch {
            timeScheduler.cancel(rule)
            reconcileAll()
        }
    }

    /**
     * Applies an automation's registrations immediately after a save or delete.
     *
     * The combined flow above would get there on its own, but not before the
     * editor closes — and an automation that is not armed by the time the user
     * is back on the list is one they will test and believe is broken.
     */
    fun onAutomationChanged() {
        scope.launch { reconcileAll() }
    }

    private companion object {
        const val TAG = "TriggerCoordinator"
    }
}
