package com.rahulgorai.remiit.delivery

import com.rahulgorai.remiit.data.model.ReminderRule

/**
 * Shows a reminder to the user.
 *
 * An interface rather than a direct dependency on [ReminderDispatcher] so the
 * engine's decision logic — match modes, cooldowns, daily caps — is testable
 * without an Android Context or a NotificationManager.
 */
interface ReminderDelivery {

    /**
     * @param eventId history row this firing was recorded as, carried through so
     *   a later Complete/Incomplete tap is attributed to this exact firing.
     */
    fun deliver(rule: ReminderRule, eventId: Long, triggerSummary: String)
}
