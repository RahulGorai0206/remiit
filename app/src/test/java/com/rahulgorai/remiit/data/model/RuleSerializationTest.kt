package com.rahulgorai.remiit.data.model

import com.rahulgorai.remiit.data.db.RemiitJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule model is persisted as JSON, so serialization is a storage format,
 * not an implementation detail. A regression here corrupts every saved rule,
 * and it fails at read time — long after the change that caused it.
 */
class RuleSerializationTest {

    private val triggers = ListSerializer(Trigger.serializer())

    private val everyTriggerKind = listOf(
        Trigger.Time(id = "t1", recurrence = Recurrence.Daily(9 * 60)),
        Trigger.Time(id = "t2", recurrence = Recurrence.Once(1_700_000_000_000L)),
        Trigger.Time(id = "t3", recurrence = Recurrence.Weekly(setOf(1, 3, 5), 8 * 60)),
        Trigger.Time(id = "t4", recurrence = Recurrence.Monthly(1, 10 * 60)),
        Trigger.Time(
            id = "t5",
            recurrence = Recurrence.Interval(60, 9 * 60, 18 * 60),
            timeZoneId = "Asia/Kolkata",
        ),
        Trigger.Wifi(id = "w1", ssid = "Office-5G", event = WifiEvent.CONNECTED),
        Trigger.Location(
            id = "l1",
            latitude = 22.5726,
            longitude = 88.3639,
            radiusMeters = 150f,
            event = LocationEvent.ENTER,
            label = "Office",
        ),
        Trigger.AppLaunch(
            id = "a1",
            packages = setOf("com.instagram.android"),
            excludes = setOf("com.rahulgorai.remiit"),
        ),
    )

    @Test
    fun `every trigger kind survives a round trip`() {
        val json = RemiitJson.encodeToString(triggers, everyTriggerKind)
        assertEquals(everyTriggerKind, RemiitJson.decodeFromString(triggers, json))
    }

    @Test
    fun `a whole rule survives a round trip`() {
        val rule = ReminderRule(
            id = "rule-1",
            title = "Drink water",
            body = "Refill the bottle",
            triggers = everyTriggerKind,
            match = MatchMode.ALL,
            delivery = DeliveryConfig(
                mode = DeliveryMode.ALARM,
                snoozeMinutes = 5,
                autoDismissSeconds = 60,
                vibrationPattern = listOf(0L, 100L, 100L),
            ),
            constraints = RuleConstraints(
                cooldownMinutes = 30,
                maxFiresPerDay = 8,
                quietHours = QuietHours(22 * 60, 7 * 60),
                activeDays = setOf(1, 2, 3, 4, 5),
                matchWindowMinutes = 20,
            ),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )
        val json = RemiitJson.encodeToString(ReminderRule.serializer(), rule)
        assertEquals(rule, RemiitJson.decodeFromString(ReminderRule.serializer(), json))
    }

    @Test
    fun `discriminator names are the persisted contract`() {
        // These strings are written into the database and into the AI prompt
        // schema. Renaming one orphans saved rules, so pin them explicitly.
        val json = RemiitJson.encodeToString(triggers, everyTriggerKind)
        listOf("\"time\"", "\"wifi\"", "\"location\"", "\"app_launch\"").forEach {
            assertTrue("missing trigger discriminator $it", it in json)
        }
        listOf("\"daily\"", "\"once\"", "\"weekly\"", "\"monthly\"", "\"interval\"").forEach {
            assertTrue("missing recurrence discriminator $it", it in json)
        }
    }

    @Test
    fun `an unknown field written by a newer build is ignored, not fatal`() {
        // Forward compatibility: an APK downgrade must still read rows the
        // newer build wrote, or the app crashes on launch with no way back.
        val withFutureField = """
            {"type":"wifi","id":"w1","ssid":"Office-5G","event":"CONNECTED",
             "somethingAddedLater":{"nested":true}}
        """.trimIndent()
        val decoded = RemiitJson.decodeFromString(Trigger.serializer(), withFutureField)
        assertEquals(Trigger.Wifi("w1", "Office-5G", WifiEvent.CONNECTED), decoded)
    }

    @Test
    fun `a field absent from older data falls back to its default`() {
        val minimal = """{"type":"location","id":"l1","latitude":1.0,"longitude":2.0,
            "radiusMeters":100.0,"event":"EXIT"}""".trimIndent()
        val decoded = RemiitJson.decodeFromString(Trigger.serializer(), minimal) as Trigger.Location
        assertEquals("", decoded.label)
        assertEquals(5, decoded.dwellMinutes)
    }

    @Test
    fun `app launch trigger matches any package when none are listed`() {
        val anyApp = Trigger.AppLaunch(id = "a", excludes = setOf("com.rahulgorai.remiit"))
        assertTrue(anyApp.matches("com.instagram.android"))
        assertTrue(!anyApp.matches("com.rahulgorai.remiit"))
    }

    @Test
    fun `app launch trigger honours an explicit allow list`() {
        val specific = Trigger.AppLaunch(id = "a", packages = setOf("com.instagram.android"))
        assertTrue(specific.matches("com.instagram.android"))
        assertTrue(!specific.matches("com.whatsapp"))
    }
}
