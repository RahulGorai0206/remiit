package com.rahulgorai.remiit.trigger.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rahulgorai.remiit.data.model.shortSummary
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.engine.TriggerEvent
import com.rahulgorai.remiit.engine.TriggerSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Clock

/**
 * Receives a fired time alarm, hands it to the engine, and immediately arms the
 * recurrence's next occurrence.
 *
 * Re-arming here rather than on a schedule is what makes recurrences work at
 * all: each fire sets up exactly one successor, so a missed re-arm affects one
 * rule instead of stalling every recurrence in the app. The periodic reconcile
 * worker exists as a safety net for exactly that case.
 */
class TimeTriggerReceiver : BroadcastReceiver(), KoinComponent {

    private val repository: RuleRepository by inject()
    private val sink: TriggerSink by inject()
    private val clock: Clock by inject()
    private val scheduler: TimeTriggerScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: return
        val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL)

        // goAsync keeps the broadcast alive across the database read. Without
        // it, onReceive returns before the coroutine runs and the process
        // becomes eligible for death mid-dispatch.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val rule = repository.rule(ruleId)
                if (rule == null || !rule.isEnabled) return@launch

                val trigger = rule.timeTriggers.firstOrNull { it.id == triggerId }

                sink.onTriggerFired(
                    TriggerEvent(
                        ruleId = ruleId,
                        triggerId = triggerId,
                        summary = label ?: trigger?.shortSummary() ?: "Scheduled",
                        firedAt = clock.instant(),
                    )
                )

                // Advance the recurrence. A snooze alarm has no matching trigger
                // and needs no successor.
                if (trigger != null) {
                    scheduler.scheduleTrigger(rule, trigger, after = clock.instant())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling time trigger $ruleId/$triggerId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.rahulgorai.remiit.action.TIME_TRIGGER"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_TRIGGER_ID = "trigger_id"
        const val EXTRA_LABEL = "label"
        private const val TAG = "TimeTriggerReceiver"
    }
}
