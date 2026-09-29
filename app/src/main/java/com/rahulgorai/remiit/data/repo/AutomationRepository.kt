package com.rahulgorai.remiit.data.repo

import com.rahulgorai.remiit.data.db.AutomationDao
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.DeviceSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID

/** Single entry point to the automation store. Mirrors [RuleRepository]. */
class AutomationRepository(
    private val dao: AutomationDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeAll(): Flow<List<Automation>> = dao.observeAll()

    fun observeEnabled(): Flow<List<Automation>> = dao.observeEnabled()

    suspend fun enabled(): List<Automation> = dao.getEnabled()

    /** Every automation, for export. */
    suspend fun all(): List<Automation> = dao.getAll()

    suspend fun automation(id: String): Automation? = dao.getById(id)

    /**
     * Inserts or updates, filling in id and timestamps. Returns the stored row.
     *
     * Configuration comes from [automation]; device state comes from the row
     * already in the database. The editor builds a fresh [Automation] from its
     * form, which knows nothing about what happened on this phone, and an
     * upsert replaces the whole row — so without this, editing an automation
     * while you were at the office would throw away the snapshot, and leaving
     * would restore nothing. The run record had the same problem, which is why
     * the card used to lose its "last ran" line after any edit.
     */
    suspend fun save(automation: Automation): Automation {
        val now = clock.millis()
        val existing = automation.id.takeIf { it.isNotBlank() }?.let { dao.getById(it) }
        val stored = automation.copy(
            id = automation.id.ifBlank { UUID.randomUUID().toString() },
            createdAtEpochMillis =
                if (automation.createdAtEpochMillis == 0L) now else automation.createdAtEpochMillis,
            updatedAtEpochMillis = now,
            lastRunAtEpochMillis = existing?.lastRunAtEpochMillis ?: 0L,
            lastResult = existing?.lastResult ?: "",
            lastRunSucceeded = existing?.lastRunSucceeded ?: true,
            // Dropped when restoring is switched off. Kept, it would lie in
            // wait and be restored the next time the switch went back on —
            // putting back a state from some unrelated day.
            savedState = if (automation.restoreOnExit) existing?.savedState else null,
        )
        dao.upsert(stored)
        return stored
    }

    suspend fun setSavedState(id: String, snapshot: DeviceSnapshot?) =
        dao.setSavedState(id, snapshot)

    suspend fun setEnabled(id: String, enabled: Boolean) =
        dao.setEnabled(id, enabled, clock.millis())

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun recordRun(id: String, result: String, succeeded: Boolean) =
        dao.recordRun(id, clock.millis(), result, succeeded)
}
