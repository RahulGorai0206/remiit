package com.rahulgorai.remiit.engine

import java.time.Instant

/**
 * One trigger on one rule having been satisfied.
 *
 * Trigger sources produce these; [RuleEngine] decides whether they add up to a
 * reminder. Sources deliberately know nothing about match modes, cooldowns or
 * delivery — a Wi-Fi callback's only job is to say "the office network trigger
 * on rule X just matched".
 */
data class TriggerEvent(
    val ruleId: String,
    val triggerId: String,
    /** Human-readable cause, stored on the history row. */
    val summary: String,
    val firedAt: Instant,
)

/** Receives [TriggerEvent]s. Implemented by [RuleEngine]. */
interface TriggerSink {
    suspend fun onTriggerFired(event: TriggerEvent)
}
