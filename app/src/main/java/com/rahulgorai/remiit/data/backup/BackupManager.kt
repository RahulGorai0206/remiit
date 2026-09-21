package com.rahulgorai.remiit.data.backup

import android.util.Log
import com.rahulgorai.remiit.BuildConfig
import com.rahulgorai.remiit.data.db.RemiitJson
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.repo.AutomationRepository
import com.rahulgorai.remiit.data.repo.RuleRepository
import java.time.Clock

/**
 * Reads and writes the backup document.
 *
 * Knows nothing about files or URIs — it deals in strings, so the whole of the
 * interesting behaviour (what a valid file is, what happens to an id that
 * already exists, what gets stripped) is testable without Android.
 */
class BackupManager(
    private val rules: RuleRepository,
    private val automations: AutomationRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** The whole configuration as pretty-printed JSON. */
    suspend fun export(): String {
        val backup = RemiitBackup(
            exportedAtEpochMillis = clock.millis(),
            appVersionName = BuildConfig.VERSION_NAME,
            rules = rules.allRules(),
            // Stripped here as well as on import, so the file itself is pure
            // configuration. Exporting a run record only to discard it on the
            // way back in would put a misleading "last ran" line in a document
            // people are invited to open and read.
            automations = automations.all().map { it.withoutRunRecord() },
        )
        return BackupJson.encodeToString(backup)
    }

    /**
     * Restores a backup document.
     *
     * Matching on id rather than appending copies. On a fresh install — the case
     * this feature exists for — nothing collides and every row is new. On a
     * phone that already has rules, importing the same file twice leaves one
     * copy of each rather than two, which is the behaviour someone gets when
     * they are not sure whether the first attempt worked.
     */
    suspend fun import(json: String): ImportResult {
        val backup = runCatching {
            BackupJson.decodeFromString<RemiitBackup>(json)
        }.getOrElse {
            Log.w(TAG, "Could not parse backup", it)
            return ImportResult.Failure("This file could not be read as a Remiit backup.")
        }

        if (backup.format != RemiitBackup.FORMAT) {
            return ImportResult.Failure("This is not a Remiit backup file.")
        }
        if (backup.version > RemiitBackup.VERSION) {
            // Refused rather than attempted. Unknown keys are ignored by the
            // decoder, so a newer file would import as a plausible-looking rule
            // with whatever the new build added silently missing — worse than
            // not importing at all, because it looks like it worked.
            return ImportResult.Failure(
                "This backup was made by a newer version of Remiit. Update the app, " +
                    "then import it again."
            )
        }
        if (backup.isEmpty) {
            return ImportResult.Failure("This backup is empty — there is nothing to import.")
        }

        return runCatching {
            var rulesAdded = 0
            var rulesUpdated = 0
            backup.rules.forEach { rule ->
                if (rules.rule(rule.id) == null) rulesAdded++ else rulesUpdated++
                rules.save(rule)
            }

            var automationsAdded = 0
            var automationsUpdated = 0
            backup.automations.forEach { automation ->
                if (automations.automation(automation.id) == null) {
                    automationsAdded++
                } else {
                    automationsUpdated++
                }
                automations.save(automation.withoutRunRecord())
            }

            ImportResult.Success(
                rulesAdded = rulesAdded,
                rulesUpdated = rulesUpdated,
                automationsAdded = automationsAdded,
                automationsUpdated = automationsUpdated,
            )
        }.getOrElse {
            Log.e(TAG, "Import failed part-way", it)
            ImportResult.Failure("Something went wrong while importing. Some rules may " +
                "have been restored — check the list.")
        }
    }

    /**
     * Strips the last-run record.
     *
     * It describes what happened on the phone the backup came from. Carried
     * across it would show "Ran 3 days ago" on an automation this device has
     * never run, which is the one piece of feedback automations have and the
     * one place a lie is expensive. Applied on the way out *and* on the way in,
     * so a file written by a build that did not strip it is still safe to read.
     */
    private fun Automation.withoutRunRecord() = copy(
        lastRunAtEpochMillis = 0L,
        lastResult = "",
        lastRunSucceeded = true,
    )

    private companion object {
        const val TAG = "BackupManager"
    }
}

/**
 * Indented, unlike [RemiitJson].
 *
 * A backup is a file a person may well open, read, hand-edit or diff — the
 * database columns are not. Everything else is inherited: unknown keys are
 * ignored so an older build can read a newer file, and defaults are written so
 * a newer build reading an older file sees every field.
 */
private val BackupJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    prettyPrint = true
}
