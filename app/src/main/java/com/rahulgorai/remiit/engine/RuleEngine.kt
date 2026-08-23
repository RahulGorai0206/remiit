package com.rahulgorai.remiit.engine

import android.util.Log
import com.rahulgorai.remiit.data.model.MatchMode
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.delivery.ReminderDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Decides whether a satisfied trigger actually becomes a reminder.
 *
 * Sits between the trigger sources and [ReminderDispatcher] and owns three
 * things they do not: combining triggers under [MatchMode], enforcing
 * [com.rahulgorai.remiit.data.model.RuleConstraints], and recording the fire.
 */
class RuleEngine(
    private val repository: RuleRepository,
    private val dispatcher: ReminderDispatcher,
    private val clock: Clock = Clock.systemDefaultZone(),
) : TriggerSink {

    /**
     * For [MatchMode.ALL] rules: when each trigger was last satisfied.
     *
     * In memory rather than persisted on purpose. A partial match is a
     * short-lived thing bounded by
     * [com.rahulgorai.remiit.data.model.RuleConstraints.matchWindowMinutes], and
     * carrying one across a process restart would fire a reminder for a
     * condition that is no longer true.
     */
    private val partialMatches = mutableMapOf<String, MutableMap<String, Instant>>()

    /** Serialises evaluation so two triggers firing at once cannot both pass a cooldown check. */
    private val mutex = Mutex()

    override suspend fun onTriggerFired(event: TriggerEvent) {
        mutex.withLock {
            try {
                val rule = repository.rule(event.ruleId) ?: return
                if (!rule.isEnabled) return
                if (!isMatchComplete(rule, event)) return
                if (!passesConstraints(rule, event.firedAt)) return

                fire(rule, event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed evaluating ${event.ruleId}/${event.triggerId}", e)
            }
        }
    }

    /**
     * For ANY, one trigger is enough. For ALL, every trigger on the rule must
     * have been satisfied within the match window — which is what makes
     * "on office Wi-Fi *and* after 3pm" mean both, rather than either.
     */
    private fun isMatchComplete(rule: ReminderRule, event: TriggerEvent): Boolean {
        if (rule.match == MatchMode.ANY) return true
        if (rule.triggers.size <= 1) return true

        val window = rule.constraints.matchWindowMinutes.coerceAtLeast(1).toLong()
        val cutoff = event.firedAt.minus(window, ChronoUnit.MINUTES)

        val satisfied = partialMatches.getOrPut(rule.id) { mutableMapOf() }
        satisfied[event.triggerId] = event.firedAt
        // Drop anything that has aged out, and anything belonging to a trigger
        // the rule no longer has after an edit.
        val liveIds = rule.triggers.map { it.id }.toSet()
        satisfied.entries.removeAll { (id, at) -> at.isBefore(cutoff) || id !in liveIds }

        val complete = satisfied.keys.containsAll(liveIds)
        // Reset once complete so the next full round has to satisfy everything
        // again instead of coasting on one stale trigger.
        if (complete) satisfied.clear()
        return complete
    }

    private suspend fun passesConstraints(rule: ReminderRule, at: Instant): Boolean {
        val constraints = rule.constraints
        if (!constraints.allows(at, clock.zone)) return false

        if (constraints.cooldownMinutes > 0) {
            val lastFired = repository.lastFiredAt(rule.id)
            if (lastFired != null) {
                val elapsed = at.toEpochMilli() - lastFired
                if (elapsed < constraints.cooldownMinutes * 60_000L) return false
            }
        }

        if (constraints.maxFiresPerDay > 0) {
            val startOfDay = at.atZone(clock.zone).toLocalDate()
                .atStartOfDay(clock.zone).toInstant().toEpochMilli()
            if (repository.countFiredSince(rule.id, startOfDay) >= constraints.maxFiresPerDay) {
                return false
            }
        }

        return true
    }

    private suspend fun fire(rule: ReminderRule, event: TriggerEvent) {
        // The history row is written before delivery so the notification can
        // carry its id — that is how a Complete tap minutes later is attributed
        // to this exact firing rather than the rule in general.
        val eventId = repository.recordFire(rule, event.summary)
        dispatcher.deliver(rule, eventId, event.summary)
    }

    /** Fires a rule immediately, ignoring constraints. Backs the builder's Preview button. */
    suspend fun previewNow(rule: ReminderRule) {
        val eventId = repository.recordFire(rule, "Preview")
        dispatcher.deliver(rule, eventId, "Preview")
    }

    private companion object {
        const val TAG = "RuleEngine"
    }
}
