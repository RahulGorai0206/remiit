package com.rahulgorai.remiit.engine

import android.content.Context
import android.util.Log
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.prefs.AppLaunchDetectorKind
import com.rahulgorai.remiit.data.prefs.SettingsStore
import com.rahulgorai.remiit.service.RemiitMonitorService
import com.rahulgorai.remiit.trigger.applaunch.AppLaunchDispatcher
import com.rahulgorai.remiit.trigger.location.LocationTriggerMonitor
import com.rahulgorai.remiit.trigger.time.TimeTriggerScheduler
import com.rahulgorai.remiit.trigger.wifi.WifiTriggerMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.rahulgorai.remiit.data.repo.RuleRepository

/**
 * Keeps the OS-side registrations in step with the rule table.
 *
 * Everything is driven off the enabled-rules flow, so toggling a rule is the
 * only action needed to arm or tear down its alarms, geofences and monitors.
 * There is no separate "apply" step that could fall out of sync with what the
 * database says.
 */
class TriggerCoordinator(
    private val context: Context,
    private val repository: RuleRepository,
    private val settings: SettingsStore,
    private val timeScheduler: TimeTriggerScheduler,
    private val locationMonitor: LocationTriggerMonitor,
    private val wifiMonitor: WifiTriggerMonitor,
    private val appLaunchDispatcher: AppLaunchDispatcher,
    private val scope: CoroutineScope,
) {
    /** Starts following the rule table. Called once, from the Application. */
    fun start() {
        combine(
            repository.observeEnabledRules(),
            settings.appLaunchDetector,
        ) { rules, detector -> rules to detector }
            .distinctUntilChanged()
            .onEach { (rules, detector) -> apply(rules, detector) }
            .launchIn(scope)
    }

    /**
     * Rebuilds every registration from the current rule table.
     *
     * Called after boot, after an app update, and periodically — all cases where
     * the OS has thrown away alarms and geofences without telling the app.
     */
    suspend fun reconcileAll() {
        val rules = repository.enabledRules()
        val detector = currentDetectorKind()
        apply(rules, detector)
    }

    private suspend fun currentDetectorKind(): AppLaunchDetectorKind =
        runCatching { settings.appLaunchDetector.first() }
            .getOrDefault(AppLaunchDetectorKind.ACCESSIBILITY)

    private suspend fun apply(rules: List<ReminderRule>, detector: AppLaunchDetectorKind) {
        try {
            timeScheduler.rescheduleAll(rules)
            locationMonitor.sync(rules)

            wifiMonitor.updateRules(rules)
            appLaunchDispatcher.updateRules(rules)

            // The foreground service is only started when something actually
            // needs a live process. A setup of purely time and location rules
            // runs with no persistent notification at all, because AlarmManager
            // and geofences are evaluated by the OS.
            val needsService = rules.any { it.wifiTriggers.isNotEmpty() } ||
                (detector == AppLaunchDetectorKind.USAGE_STATS &&
                    rules.any { it.appLaunchTriggers.isNotEmpty() })

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

    private companion object {
        const val TAG = "TriggerCoordinator"
    }
}
