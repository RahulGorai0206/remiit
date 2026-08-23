package com.rahulgorai.remiit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.rahulgorai.remiit.data.model.ReminderRule
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM reminder_rules ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ReminderRule>>

    /**
     * The engine's source of truth. Every trigger registration is driven off
     * this flow, so toggling `isEnabled` is all it takes to wire or tear down
     * a rule's alarms, geofences and monitors.
     */
    @Query("SELECT * FROM reminder_rules WHERE is_enabled = 1")
    fun observeEnabled(): Flow<List<ReminderRule>>

    @Query("SELECT * FROM reminder_rules WHERE is_enabled = 1")
    suspend fun getEnabled(): List<ReminderRule>

    @Query("SELECT * FROM reminder_rules WHERE id = :id")
    suspend fun getById(id: String): ReminderRule?

    @Query("SELECT * FROM reminder_rules WHERE id = :id")
    fun observeById(id: String): Flow<ReminderRule?>

    @Upsert
    suspend fun upsert(rule: ReminderRule)

    @Delete
    suspend fun delete(rule: ReminderRule)

    @Query("DELETE FROM reminder_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE reminder_rules SET is_enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)
}
