package com.rahulgorai.remiit.automation

import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.AutomationActions
import com.rahulgorai.remiit.data.model.AutomationEdge
import com.rahulgorai.remiit.data.model.AutomationTrigger
import com.rahulgorai.remiit.data.model.SoundSetting
import com.rahulgorai.remiit.data.repo.AutomationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AutomationEngineTest {

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:00:00Z"), ZoneOffset.UTC)
    private val dao = FakeAutomationDao()
    private val repository = AutomationRepository(dao, clock)

    private fun engine(controls: FakeDeviceControls) = AutomationEngine(repository, controls)

    private fun automation(
        id: String = "a1",
        trigger: AutomationTrigger,
        actions: AutomationActions = AutomationActions(sound = SoundSetting.SILENT),
        enabled: Boolean = true,
    ) = Automation(
        id = id,
        name = id,
        isEnabled = enabled,
        trigger = trigger,
        actions = actions,
    )

    @Test
    fun `wifi connect runs a matching automation`() = runTest {
        dao.upsert(
            automation(trigger = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER))
        )
        val controls = FakeDeviceControls()

        engine(controls).onSignal(AutomationSignal.Wifi("Office", AutomationEdge.ENTER))

        assertEquals(listOf(SoundSetting.SILENT), controls.soundCalls)
    }

    @Test
    fun `the opposite edge does not run it`() = runTest {
        dao.upsert(
            automation(trigger = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER))
        )
        val controls = FakeDeviceControls()

        engine(controls).onSignal(AutomationSignal.Wifi("Office", AutomationEdge.EXIT))

        assertTrue(controls.soundCalls.isEmpty())
    }

    @Test
    fun `ssids match regardless of case`() = runTest {
        dao.upsert(
            automation(trigger = AutomationTrigger.Wifi("Office WiFi", AutomationEdge.ENTER))
        )
        val controls = FakeDeviceControls()

        engine(controls).onSignal(AutomationSignal.Wifi("OFFICE WIFI", AutomationEdge.ENTER))

        assertEquals(1, controls.soundCalls.size)
    }

    @Test
    fun `a disabled automation never runs`() = runTest {
        dao.upsert(
            automation(
                trigger = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER),
                enabled = false,
            )
        )
        val controls = FakeDeviceControls()

        engine(controls).onSignal(AutomationSignal.Wifi("Office", AutomationEdge.ENTER))

        assertTrue(controls.soundCalls.isEmpty())
    }

    @Test
    fun `a wifi signal never runs a bluetooth automation`() = runTest {
        dao.upsert(
            automation(trigger = AutomationTrigger.Bluetooth("AA:BB", "Car", AutomationEdge.ENTER))
        )
        val controls = FakeDeviceControls()

        engine(controls).onSignal(AutomationSignal.Wifi("AA:BB", AutomationEdge.ENTER))

        assertTrue(controls.soundCalls.isEmpty())
    }

    @Test
    fun `bluetooth addresses match regardless of case`() = runTest {
        dao.upsert(
            automation(trigger = AutomationTrigger.Bluetooth("aa:bb:cc", "Car", AutomationEdge.EXIT))
        )
        val controls = FakeDeviceControls()

        engine(controls).onSignal(AutomationSignal.Bluetooth("AA:BB:CC", AutomationEdge.EXIT))

        assertEquals(1, controls.soundCalls.size)
    }

    /**
     * The bug this pins: matching a geofence transition on the trigger's shape
     * alone would run *every* location automation on every transition, because
     * a geofence signal carries no coordinates to compare.
     */
    @Test
    fun `a geofence transition only runs the automation it was registered for`() = runTest {
        val place = AutomationTrigger.Location(51.5, -0.1, 150f, "Home", AutomationEdge.ENTER)
        dao.upsert(automation(id = "mine", trigger = place))
        dao.upsert(
            automation(
                id = "someone-elses",
                trigger = place.copy(label = "Office"),
                actions = AutomationActions(sound = SoundSetting.RING),
            )
        )
        val controls = FakeDeviceControls()

        engine(controls).onSignal(AutomationSignal.Geofence("mine", AutomationEdge.ENTER))

        assertEquals(listOf(SoundSetting.SILENT), controls.soundCalls)
    }

    @Test
    fun `both actions are applied`() = runTest {
        dao.upsert(
            automation(
                trigger = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER),
                actions = AutomationActions(
                    sound = SoundSetting.VIBRATE,
                    autoBrightness = false,
                ),
            )
        )
        val controls = FakeDeviceControls()

        engine(controls).onSignal(AutomationSignal.Wifi("Office", AutomationEdge.ENTER))

        assertEquals(listOf(SoundSetting.VIBRATE), controls.soundCalls)
        assertEquals(listOf(false), controls.brightnessCalls)
    }

    @Test
    fun `a successful run records what it changed`() = runTest {
        dao.upsert(
            automation(
                trigger = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER),
                actions = AutomationActions(sound = SoundSetting.SILENT, autoBrightness = true),
            )
        )

        engine(FakeDeviceControls()).onSignal(
            AutomationSignal.Wifi("Office", AutomationEdge.ENTER)
        )

        val stored = dao.getById("a1")!!
        assertEquals("Silent · Adaptive brightness on", stored.lastResult)
        assertEquals(clock.millis(), stored.lastRunAtEpochMillis)
        assertTrue(stored.lastRunSucceeded)
    }

    /**
     * The whole reason the run record is persisted: an automation that could not
     * do its job has to say so, because nothing else will. A revoked grant is
     * otherwise indistinguishable from a trigger that has not happened yet.
     */
    @Test
    fun `a refused action is recorded as the reason, not as success`() = runTest {
        dao.upsert(
            automation(
                trigger = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER),
                actions = AutomationActions(sound = SoundSetting.SILENT),
            )
        )

        engine(FakeDeviceControls(dndAccess = false)).onSignal(
            AutomationSignal.Wifi("Office", AutomationEdge.ENTER)
        )

        val stored = dao.getById("a1")!!
        assertEquals("Do Not Disturb access not granted", stored.lastResult)
        // Recorded as a failure explicitly, not inferred from the text — an
        // edit to the actions must not turn a past success into a red line.
        assertTrue(!stored.lastRunSucceeded)
    }

    @Test
    fun `one failing action does not stop the other`() = runTest {
        dao.upsert(
            automation(
                trigger = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER),
                actions = AutomationActions(
                    sound = SoundSetting.SILENT,
                    autoBrightness = false,
                ),
            )
        )
        val controls = FakeDeviceControls(dndAccess = false)

        engine(controls).onSignal(AutomationSignal.Wifi("Office", AutomationEdge.ENTER))

        assertEquals(listOf(false), controls.brightnessCalls)
    }

    @Test
    fun `every automation watching the same edge runs`() = runTest {
        val trigger = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER)
        dao.upsert(automation(id = "a1", trigger = trigger))
        dao.upsert(
            automation(
                id = "a2",
                trigger = trigger,
                actions = AutomationActions(autoBrightness = true),
            )
        )
        val controls = FakeDeviceControls()

        engine(controls).onSignal(AutomationSignal.Wifi("Office", AutomationEdge.ENTER))

        assertEquals(listOf(SoundSetting.SILENT), controls.soundCalls)
        assertEquals(listOf(true), controls.brightnessCalls)
    }
}
