package com.rahulgorai.remiit.automation

import com.rahulgorai.remiit.data.db.AutomationDao
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.DeviceSnapshot
import com.rahulgorai.remiit.data.model.RingerMode
import com.rahulgorai.remiit.data.model.SoundSetting
import com.rahulgorai.remiit.data.model.VolumeStream
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
    override suspend fun getAll(): List<Automation> =
        automations.value.sortedBy(Automation::createdAtEpochMillis)
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

    override suspend fun setSavedState(id: String, state: DeviceSnapshot?) {
        automations.value = automations.value.map {
            if (it.id == id) it.copy(savedState = state) else it
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

/**
 * A simulated phone.
 *
 * Holds real state that the apply calls change and the read calls report, so a
 * restore test can assert the phone ended up where it started — which is the
 * whole claim restore makes — rather than only that some calls were made. The
 * call logs are kept as well, for the tests that care about order.
 */
class FakeDeviceControls(
    private val dndAccess: Boolean = true,
    private val writeSettings: Boolean = true,
) : DeviceControls {

    var ringer: RingerMode = RingerMode.RING
    var dndOn: Boolean = false
    var autoBrightness: Boolean = true
    val volumes: MutableMap<VolumeStream, Int> =
        VolumeStream.entries.associateWith { 50 }.toMutableMap()

    val soundCalls = mutableListOf<SoundSetting>()
    val brightnessCalls = mutableListOf<Boolean>()

    /** In call order, so the engine's ordering guarantee can be asserted. */
    val volumeCalls = mutableListOf<Pair<VolumeStream, Int>>()

    override fun hasDndAccess() = dndAccess
    override fun canWriteSystemSettings() = writeSettings

    override fun applySound(setting: SoundSetting): ActionResult {
        soundCalls += setting
        if (setting.requiresDndAccess() && !dndAccess) {
            return ActionResult.Failed("Do Not Disturb access not granted")
        }
        when (setting) {
            SoundSetting.SILENT -> ringer = RingerMode.SILENT
            SoundSetting.VIBRATE -> ringer = RingerMode.VIBRATE
            SoundSetting.RING -> ringer = RingerMode.RING
            SoundSetting.DND_ON -> dndOn = true
            SoundSetting.DND_OFF -> dndOn = false
        }
        return ActionResult.Ok
    }

    override fun applyVolume(stream: VolumeStream, percent: Int): ActionResult {
        volumeCalls += stream to percent
        if (volumeRequiresDndAccess(stream, percent) && !dndAccess) {
            return ActionResult.Failed("Muting ${stream.name.lowercase()} needs Do Not Disturb access")
        }
        volumes[stream] = percent
        return ActionResult.Ok
    }

    override fun applyAutoBrightness(enabled: Boolean): ActionResult {
        brightnessCalls += enabled
        if (!writeSettings) return ActionResult.Failed("Modify system settings not granted")
        autoBrightness = enabled
        return ActionResult.Ok
    }

    override fun readRinger(): RingerMode = ringer
    override fun readDndOn(): Boolean = dndOn
    override fun readAutoBrightness(): Boolean = autoBrightness
    override fun readVolume(stream: VolumeStream): Int? = volumes[stream]

    /** Clears the call logs, so a test can look at just what the next signal did. */
    fun clearCalls() {
        soundCalls.clear()
        brightnessCalls.clear()
        volumeCalls.clear()
    }
}
