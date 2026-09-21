package com.rahulgorai.remiit.data.model

import com.rahulgorai.remiit.data.db.RemiitJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The automation JSON contract.
 *
 * Trigger and action live in JSON columns, so these strings are the persisted
 * format rather than an implementation detail — renaming one orphans every
 * saved automation that used it, and the failure is silent: the row loads,
 * the automation shows in the list, and it watches for nothing.
 */
class AutomationSerializationTest {

    private val triggers = listOf(
        AutomationTrigger.Wifi("Office", AutomationEdge.ENTER),
        AutomationTrigger.Bluetooth("AA:BB:CC:DD:EE:FF", "Car stereo", AutomationEdge.EXIT),
        AutomationTrigger.Location(51.5074, -0.1278, 200f, "Home", AutomationEdge.ENTER),
    )

    @Test
    fun `every trigger kind survives a round trip`() {
        triggers.forEach { trigger ->
            val json = RemiitJson.encodeToString(AutomationTrigger.serializer(), trigger)
            val decoded = RemiitJson.decodeFromString(AutomationTrigger.serializer(), json)
            assertEquals(trigger, decoded)
        }
    }

    @Test
    fun `trigger discriminators are the persisted contract`() {
        val encoded = triggers.map {
            RemiitJson.encodeToString(AutomationTrigger.serializer(), it)
        }
        listOf("\"wifi\"", "\"bluetooth\"", "\"location\"").forEach { discriminator ->
            assertTrue(
                "missing automation trigger discriminator $discriminator",
                encoded.any { discriminator in it },
            )
        }
    }

    @Test
    fun `edge and sound names are the persisted contract`() {
        val json = RemiitJson.encodeToString(
            AutomationActions(sound = SoundSetting.DND_ON, autoBrightness = false)
        )
        assertTrue("DND_ON" in json)

        AutomationEdge.entries.forEach { edge ->
            val encoded = RemiitJson.encodeToString(
                AutomationTrigger.serializer(),
                AutomationTrigger.Wifi("x", edge),
            )
            assertTrue("missing edge ${edge.name}", edge.name in encoded)
        }
    }

    @Test
    fun `actions round trip including the untouched nulls`() {
        listOf(
            AutomationActions(),
            AutomationActions(sound = SoundSetting.VIBRATE),
            AutomationActions(autoBrightness = true),
            AutomationActions(sound = SoundSetting.DND_OFF, autoBrightness = false),
        ).forEach { actions ->
            val json = RemiitJson.encodeToString(actions)
            assertEquals(actions, RemiitJson.decodeFromString<AutomationActions>(json))
        }
    }

    /**
     * A build that adds a field to [AutomationActions] must still read rows
     * written by the previous one — the same leniency the rule columns rely on.
     */
    @Test
    fun `an unknown field does not break decoding`() {
        val decoded = RemiitJson.decodeFromString<AutomationActions>(
            """{"sound":"SILENT","autoBrightness":null,"somethingAddedLater":42}"""
        )
        assertEquals(AutomationActions(sound = SoundSetting.SILENT), decoded)
    }

    /** Volume levels live in the same JSON column, keyed by the stream name. */
    @Test
    fun `volume levels round trip and are keyed by stream name`() {
        // Built from the enum rather than listed, so a stream added later is
        // covered here automatically instead of quietly escaping the check.
        val actions = AutomationActions(
            volumes = VolumeStream.entries.withIndex().associate { (i, stream) ->
                stream to i * 20
            }
        )
        val json = RemiitJson.encodeToString(actions)
        VolumeStream.entries.forEach { assertTrue("missing ${it.name}", it.name in json) }
        assertEquals(actions, RemiitJson.decodeFromString<AutomationActions>(json))
    }

    /**
     * Volumes were added after the first automations shipped, so a row written
     * by the earlier build has no `volumes` key at all and must still load.
     */
    @Test
    fun `actions written before volumes existed still decode`() {
        val decoded = RemiitJson.decodeFromString<AutomationActions>(
            """{"sound":"SILENT","autoBrightness":false}"""
        )
        assertEquals(SoundSetting.SILENT, decoded.sound)
        assertTrue(decoded.volumes.isEmpty())
    }

    @Test
    fun `a whole automation round trips`() {
        val automation = Automation(
            id = "a1",
            name = "Silent at the office",
            trigger = triggers.first(),
            actions = AutomationActions(sound = SoundSetting.SILENT, autoBrightness = false),
            createdAtEpochMillis = 1_700_000_000_000L,
            updatedAtEpochMillis = 1_700_000_000_001L,
        )
        val json = RemiitJson.encodeToString(automation)
        assertEquals(automation, RemiitJson.decodeFromString<Automation>(json))
    }

    @Test
    fun `an automation with no actions is not valid`() {
        val automation = Automation(
            id = "a1",
            name = "Does nothing",
            trigger = triggers.first(),
        )
        assertTrue(!automation.isValid)
    }
}
