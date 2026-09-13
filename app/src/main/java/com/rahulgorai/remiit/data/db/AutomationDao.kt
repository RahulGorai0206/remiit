package com.rahulgorai.remiit.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rahulgorai.remiit.data.model.Automation
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {

    @Query("SELECT * FROM automations ORDER BY created_at DESC")
    fun observeAll(): Flow<List<Automation>>

    /**
     * Drives registration, exactly as [RuleDao.observeEnabled] does for rules:
     * flipping `is_enabled` is the only action needed to arm or tear down an
     * automation's geofence and its place in the monitors' watch lists.
     */
    @Query("SELECT * FROM automations WHERE is_enabled = 1")
    fun observeEnabled(): Flow<List<Automation>>

    @Query("SELECT * FROM automations WHERE is_enabled = 1")
    suspend fun getEnabled(): List<Automation>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): Automation?

    @Upsert
    suspend fun upsert(automation: Automation)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE automations SET is_enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    /**
     * Written from the engine on every run, including failed ones.
     *
     * A targeted UPDATE rather than an upsert of the whole row: the engine runs
     * off a snapshot taken when the signal arrived, and rewriting every column
     * from it would quietly undo an edit the user made in between.
     */
    @Query(
        "UPDATE automations SET last_run_at = :at, last_result = :result, " +
            "last_run_ok = :succeeded WHERE id = :id"
    )
    suspend fun recordRun(id: String, at: Long, result: String, succeeded: Boolean)
}
