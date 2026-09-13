package com.rahulgorai.remiit.automation

import com.rahulgorai.remiit.data.db.AutomationDao
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.SoundSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory DAO, so the engine runs against the real
 * [com.rahulgorai.remiit.data.repo.AutomationRepository] rather than a stub of
 * it — the "only enabled automations run" rule is a DAO query, and stubbing the
 * repository would test the stub instead.
 */
class FakeAutomationDao : AutomationDao {
    val automations = MutableStateFlow<List<Automation>>(emptyList())

    override fun observeAll(): Flow<List<Automation>> = automations
    override fun observeEnabled(): Flow<List<Automation>> =
        automations.map { list -> list.filter { it.isEnabled } }

    override suspend fun getEnabled(): List<Automation> = automations.value.filter { it.isEnabled }
    override suspend fun getById(id: String): Automation? = automations.value.find { it.id == id }

    override suspend fun upsert(automation: Automation) {
        automations.value = automations.value.filterNot { it.id == automation.id } + automation
    }

    override suspend fun deleteById(id: String) {
        automations.value = automations.value.filterNot { it.id == id }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long) {
        automations.value = automations.value.map {
            if (it.id == id) it.copy(isEnabled = enabled, updatedAtEpochMillis = updatedAt) else it
        }
    }

    override suspend fun recordRun(id: String, at: Long, result: String, succeeded: Boolean) {
        automations.value = automations.value.map {
            if (it.id == id) {
                it.copy(
                    lastRunAtEpochMillis = at,
                    lastResult = result,
                    lastRunSucceeded = succeeded,
                )
            } else {
                it
            }
        }
    }
}

/** Records what an automation asked the phone to do, and can refuse it. */
class FakeDeviceControls(
    private val dndAccess: Boolean = true,
    private val writeSettings: Boolean = true,
) : DeviceControls {

    val soundCalls = mutableListOf<SoundSetting>()
    val brightnessCalls = mutableListOf<Boolean>()

    override fun hasDndAccess() = dndAccess
    override fun canWriteSystemSettings() = writeSettings

    override fun applySound(setting: SoundSetting): ActionResult {
        soundCalls += setting
        return if (setting.requiresDndAccess() && !dndAccess) {
            ActionResult.Failed("Do Not Disturb access not granted")
        } else {
            ActionResult.Ok
        }
    }

    override fun applyAutoBrightness(enabled: Boolean): ActionResult {
        brightnessCalls += enabled
        return if (writeSettings) {
            ActionResult.Ok
        } else {
            ActionResult.Failed("Modify system settings not granted")
        }
    }
}
