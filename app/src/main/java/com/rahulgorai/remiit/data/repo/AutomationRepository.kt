package com.rahulgorai.remiit.data.repo

import com.rahulgorai.remiit.data.db.AutomationDao
import com.rahulgorai.remiit.data.model.Automation
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

    /** Inserts or updates, filling in id and timestamps. Returns the stored row. */
    suspend fun save(automation: Automation): Automation {
        val now = clock.millis()
        val stored = automation.copy(
            id = automation.id.ifBlank { UUID.randomUUID().toString() },
            createdAtEpochMillis =
                if (automation.createdAtEpochMillis == 0L) now else automation.createdAtEpochMillis,
            updatedAtEpochMillis = now,
        )
        dao.upsert(stored)
        return stored
    }

    suspend fun setEnabled(id: String, enabled: Boolean) =
        dao.setEnabled(id, enabled, clock.millis())

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun recordRun(id: String, result: String, succeeded: Boolean) =
        dao.recordRun(id, clock.millis(), result, succeeded)
}
