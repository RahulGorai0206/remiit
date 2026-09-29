package com.rahulgorai.remiit.automation

import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.AutomationActions
import com.rahulgorai.remiit.data.model.AutomationEdge
import com.rahulgorai.remiit.data.model.AutomationTrigger
import com.rahulgorai.remiit.data.model.RingerMode
import com.rahulgorai.remiit.data.model.SoundSetting
import com.rahulgorai.remiit.data.model.VolumeStream
import com.rahulgorai.remiit.data.repo.AutomationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Restore-on-exit, against a simulated phone.
 *
 * The claim is simple — leave, and the phone is how it was when you arrived —
 * so these assert on the phone's final state rather than on which calls were
 * made. The ways it can go wrong are all about *which* state gets remembered:
 * a Wi-Fi flap re-snapshotting the silenced phone, an edit wiping the
 * snapshot, a restore stamping over something the user changed by hand.
 */
class RestoreOnExitTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-25T09:00:00Z"), ZoneOffset.UTC)
    private val dao = FakeAutomationDao()
    private val repository = AutomationRepository(dao, clock)
    private val phone = FakeDeviceControls()
    private val engine = AutomationEngine(repository, phone, clock)

    private val office = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER)
    private val arrive = AutomationSignal.Wifi("Office", AutomationEdge.ENTER)
    private val leave = AutomationSignal.Wifi("Office", AutomationEdge.EXIT)

    private fun automation(
        id: String = "a1",
        trigger: AutomationTrigger = office,
        actions: AutomationActions = AutomationActions(sound = SoundSetting.SILENT),
        restore: Boolean = true,
    ) = Automation(
        id = id,
        name = id,
        trigger = trigger,
        actions = actions,
        restoreOnExit = restore,
    )

    @Test
    fun `leaving puts the ringer back`() = runTest {
        dao.upsert(automation())

        engine.onSignal(arrive)
        assertEquals(RingerMode.SILENT, phone.ringer)

        engine.onSignal(leave)
        assertEquals(RingerMode.RING, phone.ringer)
    }

    /**
     * The point of restoring rather than writing a second "ring when I leave"
     * automation: it puts back what the phone was actually on.
     */
    @Test
    fun `it restores what the phone was on, not a fixed ring`() = runTest {
        phone.ringer = RingerMode.VIBRATE
        dao.upsert(automation())

        engine.onSignal(arrive)
        engine.onSignal(leave)

        assertEquals(RingerMode.VIBRATE, phone.ringer)
    }

    @Test
    fun `volumes and brightness come back too`() = runTest {
        phone.volumes[VolumeStream.MEDIA] = 30
        phone.volumes[VolumeStream.RING] = 70
        phone.autoBrightness = true
        dao.upsert(
            automation(
                actions = AutomationActions(
                    volumes = mapOf(VolumeStream.MEDIA to 0, VolumeStream.RING to 10),
                    autoBrightness = false,
                )
            )
        )

        engine.onSignal(arrive)
        assertEquals(0, phone.volumes[VolumeStream.MEDIA])
        assertFalse(phone.autoBrightness)

        engine.onSignal(leave)
        assertEquals(30, phone.volumes[VolumeStream.MEDIA])
        assertEquals(70, phone.volumes[VolumeStream.RING])
        assertTrue(phone.autoBrightness)
    }

    @Test
    fun `do not disturb is put back as it was`() = runTest {
        phone.dndOn = false
        dao.upsert(automation(actions = AutomationActions(sound = SoundSetting.DND_ON)))

        engine.onSignal(arrive)
        assertTrue(phone.dndOn)

        engine.onSignal(leave)
        assertFalse(phone.dndOn)
    }

    /**
     * The bug this exists to prevent. Wi-Fi drops and reconnects constantly —
     * lifts, dead spots, roaming between access points — and every reconnect
     * is another "arrived". Re-snapshotting on the second one records the phone
     * as silent (the first one silenced it), and leaving then restores silence.
     */
    @Test
    fun `a wifi reconnect does not overwrite what was remembered`() = runTest {
        phone.ringer = RingerMode.RING
        dao.upsert(automation())

        engine.onSignal(arrive)
        engine.onSignal(arrive) // a flap: still at the office, now silent
        engine.onSignal(arrive)
        engine.onSignal(leave)

        assertEquals(RingerMode.RING, phone.ringer)
    }

    /**
     * The gap between arriving and leaving is hours, and the process will be
     * killed in between. The snapshot has to come back from the database.
     */
    @Test
    fun `it restores after the process was killed in between`() = runTest {
        phone.ringer = RingerMode.VIBRATE
        dao.upsert(automation())
        engine.onSignal(arrive)

        // A new engine: nothing carried over in memory, only what was stored.
        val afterRestart = AutomationEngine(repository, phone, clock)
        afterRestart.onSignal(leave)

        assertEquals(RingerMode.VIBRATE, phone.ringer)
    }

    /**
     * Only what the automation changed is restored. Someone who turns their
     * media down during a meeting should not have leaving the office turn it
     * back up — the automation never touched media, so it has no business
     * putting it anywhere.
     */
    @Test
    fun `a setting the automation never changed is left alone`() = runTest {
        dao.upsert(automation(actions = AutomationActions(sound = SoundSetting.SILENT)))

        engine.onSignal(arrive)
        phone.volumes[VolumeStream.MEDIA] = 5 // changed by hand at the office
        engine.onSignal(leave)

        assertEquals(5, phone.volumes[VolumeStream.MEDIA])
        assertTrue(phone.volumeCalls.none { it.first == VolumeStream.MEDIA })
    }

    @Test
    fun `without the option nothing happens on leaving`() = runTest {
        dao.upsert(automation(restore = false))

        engine.onSignal(arrive)
        engine.onSignal(leave)

        assertEquals(RingerMode.SILENT, phone.ringer)
    }

    /**
     * Leaving somewhere the automation never saw you arrive — the phone booted
     * already at the office — has nothing of ours to undo, and touching the
     * settings anyway would override whatever the user set.
     */
    @Test
    fun `leaving without having arrived changes nothing`() = runTest {
        phone.ringer = RingerMode.VIBRATE
        dao.upsert(automation())

        engine.onSignal(leave)

        assertEquals(RingerMode.VIBRATE, phone.ringer)
        assertTrue(phone.soundCalls.isEmpty())
    }

    @Test
    fun `the snapshot is used once`() = runTest {
        dao.upsert(automation())
        engine.onSignal(arrive)
        engine.onSignal(leave)
        assertNull(dao.getById("a1")!!.savedState)

        phone.ringer = RingerMode.VIBRATE // changed by hand after leaving
        phone.clearCalls()
        engine.onSignal(leave)

        assertEquals(RingerMode.VIBRATE, phone.ringer)
        assertTrue(phone.soundCalls.isEmpty())
    }

    @Test
    fun `the snapshot is held while the automation is in effect`() = runTest {
        phone.ringer = RingerMode.VIBRATE
        dao.upsert(automation())

        engine.onSignal(arrive)

        val held = dao.getById("a1")!!.savedState
        assertNotNull(held)
        assertEquals(RingerMode.VIBRATE, held!!.ringer)
        // Only the ringer was going to change, so only the ringer was read.
        assertNull(held.dndOn)
        assertNull(held.autoBrightness)
        assertTrue(held.volumes.isEmpty())
    }

    /** Symmetric: an automation on the leave edge restores on the arrive edge. */
    @Test
    fun `an automation on leaving restores on arriving`() = runTest {
        phone.ringer = RingerMode.RING
        dao.upsert(
            automation(
                trigger = AutomationTrigger.Wifi("Office", AutomationEdge.EXIT),
                actions = AutomationActions(sound = SoundSetting.VIBRATE),
            )
        )

        engine.onSignal(leave)
        assertEquals(RingerMode.VIBRATE, phone.ringer)

        engine.onSignal(arrive)
        assertEquals(RingerMode.RING, phone.ringer)
    }

    /**
     * Two automations on the same edge, one putting things back and one
     * setting them outright. The explicit instruction is what the user wrote
     * down, so it runs last and wins.
     */
    @Test
    fun `an explicit automation on the same edge wins over a restore`() = runTest {
        phone.ringer = RingerMode.RING
        dao.upsert(automation(id = "silent-at-office"))
        dao.upsert(
            automation(
                id = "vibrate-on-leaving",
                trigger = AutomationTrigger.Wifi("Office", AutomationEdge.EXIT),
                actions = AutomationActions(sound = SoundSetting.VIBRATE),
                restore = false,
            )
        )

        engine.onSignal(arrive)
        engine.onSignal(leave)

        assertEquals(RingerMode.VIBRATE, phone.ringer)
    }

    @Test
    fun `a restore is recorded on the card`() = runTest {
        phone.ringer = RingerMode.VIBRATE
        dao.upsert(automation())

        engine.onSignal(arrive)
        engine.onSignal(leave)

        val stored = dao.getById("a1")!!
        assertEquals("Restored Vibrate", stored.lastResult)
        assertTrue(stored.lastRunSucceeded)
    }

    /**
     * A failed restore is cleared anyway. Kept, it would be restored at the
     * next departure — a state from some earlier day, over whatever the phone
     * has been set to since. The failure goes on the card instead.
     */
    @Test
    fun `a failed restore is recorded and not retried later`() = runTest {
        // Arrive with access, so the snapshot is taken and silence applied…
        dao.upsert(
            automation(actions = AutomationActions(volumes = mapOf(VolumeStream.RING to 60)))
        )
        phone.volumes[VolumeStream.RING] = 0
        engine.onSignal(arrive)

        // …then leave on a phone where muting ring again is refused.
        val refusing = FakeDeviceControls(dndAccess = false).apply {
            volumes[VolumeStream.RING] = 60
        }
        AutomationEngine(repository, refusing, clock).onSignal(leave)

        val stored = dao.getById("a1")!!
        assertFalse(stored.lastRunSucceeded)
        assertNull("a failed restore must not be held for a later day", stored.savedState)
    }

    /** Geofences carry no coordinates back — attribution is by id, both ways. */
    @Test
    fun `leaving one place restores only that place's automation`() = runTest {
        val home = AutomationTrigger.Location(51.5, -0.1, 150f, "Home", AutomationEdge.ENTER)
        phone.ringer = RingerMode.RING
        dao.upsert(automation(id = "home", trigger = home))
        dao.upsert(
            automation(
                id = "gym",
                trigger = home.copy(label = "Gym"),
                actions = AutomationActions(volumes = mapOf(VolumeStream.MEDIA to 100)),
            )
        )

        engine.onSignal(AutomationSignal.Geofence("home", AutomationEdge.ENTER))
        engine.onSignal(AutomationSignal.Geofence("gym", AutomationEdge.ENTER))
        engine.onSignal(AutomationSignal.Geofence("home", AutomationEdge.EXIT))

        assertEquals("home was restored", RingerMode.RING, phone.ringer)
        assertEquals("the gym is still in effect", 100, phone.volumes[VolumeStream.MEDIA])
        assertNotNull(dao.getById("gym")!!.savedState)
    }

    // ---- The snapshot has to survive the user editing the automation -------

    /**
     * The editor saves a freshly-built automation, and a save is an upsert
     * that replaces the whole row. Without the repository carrying the
     * snapshot across, an edit made at the office would throw it away.
     */
    @Test
    fun `editing the automation while it is in effect keeps the snapshot`() = runTest {
        phone.ringer = RingerMode.VIBRATE
        dao.upsert(automation())
        engine.onSignal(arrive)

        // What the editor produces: configuration only, no device state.
        repository.save(automation().copy(name = "Renamed at the office", savedState = null))
        engine.onSignal(leave)

        assertEquals(RingerMode.VIBRATE, phone.ringer)
    }

    @Test
    fun `turning restore off drops the snapshot`() = runTest {
        dao.upsert(automation())
        engine.onSignal(arrive)

        repository.save(automation(restore = false))

        assertNull(dao.getById("a1")!!.savedState)
    }

    /** The same fix covers the run record, which used to vanish on any edit. */
    @Test
    fun `editing keeps the last-run record`() = runTest {
        dao.upsert(automation(restore = false))
        engine.onSignal(arrive)
        val ran = dao.getById("a1")!!

        repository.save(automation(restore = false).copy(name = "Edited"))

        val edited = dao.getById("a1")!!
        assertEquals("Edited", edited.name)
        assertEquals(ran.lastRunAtEpochMillis, edited.lastRunAtEpochMillis)
        assertEquals(ran.lastResult, edited.lastResult)
    }
}
