package com.rahulgorai.remiit.data.backup

import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.ReminderRule
import kotlinx.serialization.Serializable

/**
 * Everything worth carrying to a new install, as one document.
 *
 * This is possible at all because a rule is already a self-contained document
 * in the database — triggers, delivery and constraints are JSON columns rather
 * than rows in side tables, so an export is the rows themselves and not a
 * bespoke serialisation that could drift from what is stored.
 *
 * What is deliberately *not* here: the reminder history. It is a log of what
 * happened on one device, not configuration, and restoring it onto a fresh
 * install would be inventing a past for a phone that does not have one. Same
 * for an automation's last-run record, which is stripped on import.
 */
@Serializable
data class RemiitBackup(
    /**
     * Marks the file as ours.
     *
     * Load-bearing, not decoration. The decoder ignores unknown keys — which is
     * what lets an older build read a newer file — and the consequence is that
     * almost any JSON, `{}` included, decodes cleanly into an empty backup.
     * Without this field to check, importing a stranger's file would report
     * cheerful success having restored nothing.
     */
    val format: String = FORMAT,

    /** Bumped only for a change an older build could not read correctly. */
    val version: Int = VERSION,

    val exportedAtEpochMillis: Long = 0L,

    /** Informational, for when someone opens the file to see where it came from. */
    val appVersionName: String = "",

    val rules: List<ReminderRule> = emptyList(),

    val automations: List<Automation> = emptyList(),
) {
    val isEmpty: Boolean get() = rules.isEmpty() && automations.isEmpty()

    companion object {
        const val FORMAT = "remiit.backup"
        const val VERSION = 1
    }
}

/** What an import did, in terms the user can be shown directly. */
sealed interface ImportResult {

    data class Success(
        val rulesAdded: Int,
        val rulesUpdated: Int,
        val automationsAdded: Int,
        val automationsUpdated: Int,
    ) : ImportResult {
        val total: Int get() = rulesAdded + rulesUpdated + automationsAdded + automationsUpdated
    }

    /** [reason] is shown verbatim, so it says what to do rather than what broke. */
    data class Failure(val reason: String) : ImportResult
}
