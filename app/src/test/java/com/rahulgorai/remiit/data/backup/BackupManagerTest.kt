package com.rahulgorai.remiit.data.backup

import com.rahulgorai.remiit.automation.FakeAutomationDao
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.AutomationActions
import com.rahulgorai.remiit.data.model.AutomationEdge
import com.rahulgorai.remiit.data.model.AutomationTrigger
import com.rahulgorai.remiit.data.model.DeliveryConfig
import com.rahulgorai.remiit.data.model.DeliveryMode
import com.rahulgorai.remiit.data.model.LocationEvent
import com.rahulgorai.remiit.data.model.Recurrence
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.RuleConstraints
import com.rahulgorai.remiit.data.model.SoundSetting
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.data.model.WifiEvent
import com.rahulgorai.remiit.data.repo.AutomationRepository
import com.rahulgorai.remiit.data.repo.RuleRepository
import com.rahulgorai.remiit.engine.FakeEventDao
import com.rahulgorai.remiit.engine.FakeRuleDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The backup file is a compatibility surface: a file written today has to be
 * readable by a build shipped a year from now, on a phone whose database was
 * created fresh. These pin the parts of that which are easy to break silently.
 */
class BackupManagerTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC)

    private val ruleDao = FakeRuleDao()
    private val automationDao = FakeAutomationDao()
    private val rules = RuleRepository(ruleDao, FakeEventDao(), clock)
    private val automations = AutomationRepository(automationDao, clock)
    private val manager = BackupManager(rules, automations, clock)

    private val rule = ReminderRule(
        id = "r1",
        title = "Stand up",
        body = "Every hour",
        triggers = listOf(
            Trigger.Time("t1", Recurrence.Daily(minuteOfDay = 540)),
            Trigger.Wifi("t2", "Office", WifiEvent.CONNECTED),
            Trigger.Location("t3", 51.5, -0.1, 150f, LocationEvent.ENTER, "Home"),
            Trigger.AppLaunch("t4", packages = setOf("com.example")),
        ),
        delivery = DeliveryConfig(mode = DeliveryMode.ALARM, soundUri = "content://alarm/7"),
        constraints = RuleConstraints(cooldownMinutes = 30),
        createdAtEpochMillis = 1_000L,
    )

    private val automation = Automation(
        id = "a1",
        name = "Silent at the office",
        trigger = AutomationTrigger.Wifi("Office", AutomationEdge.ENTER),
        actions = AutomationActions(sound = SoundSetting.SILENT, autoBrightness = false),
        createdAtEpochMillis = 2_000L,
    )

    /** A fresh install: nothing in the database, everything comes from the file. */
    private suspend fun exportThenImportIntoEmpty(): ImportResult {
        ruleDao.upsert(rule)
        automationDao.upsert(automation)
        val json = manager.export()

        ruleDao.rules.value = emptyList()
        automationDao.automations.value = emptyList()
        return manager.import(json)
    }

    @Test
    fun `a rule survives export and import unchanged`() = runTest {
        exportThenImportIntoEmpty()

        val restored = rules.rule("r1")!!
        // updatedAt is stamped by the save, so it is the only field allowed to
        // differ; everything the user actually configured must come back exactly.
        assertEquals(rule.copy(updatedAtEpochMillis = restored.updatedAtEpochMillis), restored)
    }

    @Test
    fun `every trigger kind survives`() = runTest {
        exportThenImportIntoEmpty()
        assertEquals(rule.triggers, rules.rule("r1")!!.triggers)
    }

    @Test
    fun `an automation survives`() = runTest {
        exportThenImportIntoEmpty()

        val restored = automations.automation("a1")!!
        assertEquals(automation.trigger, restored.trigger)
        assertEquals(automation.actions, restored.actions)
    }

    @Test
    fun `import reports what it restored`() = runTest {
        val result = exportThenImportIntoEmpty() as ImportResult.Success

        assertEquals(1, result.rulesAdded)
        assertEquals(1, result.automationsAdded)
        assertEquals(0, result.rulesUpdated)
        assertEquals(0, result.automationsUpdated)
    }

    @Test
    fun `a paused rule is exported too`() = runTest {
        ruleDao.upsert(rule.copy(isEnabled = false))
        val json = manager.export()
        ruleDao.rules.value = emptyList()

        manager.import(json)

        assertEquals(false, rules.rule("r1")!!.isEnabled)
    }

    /**
     * Someone unsure whether the first import worked will do it again. Matching
     * on id means they end up with one copy of each rule, not two.
     */
    @Test
    fun `importing the same file twice does not duplicate`() = runTest {
        ruleDao.upsert(rule)
        automationDao.upsert(automation)
        val json = manager.export()

        manager.import(json)
        val result = manager.import(json) as ImportResult.Success

        assertEquals(1, rules.allRules().size)
        assertEquals(1, automations.all().size)
        assertEquals(1, result.rulesUpdated)
        assertEquals(0, result.rulesAdded)
    }

    /**
     * The last-run record describes the phone the backup came from. Carried
     * across it would claim an automation had run on a device that has never
     * run it — and that line is the only feedback automations give.
     */
    @Test
    fun `an automation's run history does not travel`() = runTest {
        automationDao.upsert(
            automation.copy(
                lastRunAtEpochMillis = 5_000L,
                lastResult = "Do Not Disturb access not granted",
                lastRunSucceeded = false,
            )
        )
        val json = manager.export()
        automationDao.automations.value = emptyList()

        manager.import(json)

        val restored = automations.automation("a1")!!
        assertEquals(0L, restored.lastRunAtEpochMillis)
        assertEquals("", restored.lastResult)
        assertTrue(restored.lastRunSucceeded)
    }

    @Test
    fun `a file that is not a backup is refused`() = runTest {
        val result = manager.import("""{"hello":"world"}""")
        assertTrue(result is ImportResult.Failure)
    }

    /**
     * The decoder ignores unknown keys, so `{}` parses into a perfectly valid
     * empty backup. Without the format marker this would report success having
     * restored nothing — the format check is what makes it an error.
     */
    @Test
    fun `an empty json object is refused rather than silently importing nothing`() = runTest {
        assertTrue(manager.import("{}") is ImportResult.Failure)
    }

    @Test
    fun `malformed json is refused with a readable reason`() = runTest {
        val result = manager.import("not json at all") as ImportResult.Failure
        assertTrue(result.reason.isNotBlank())
    }

    @Test
    fun `a backup from a newer version is refused rather than half-read`() = runTest {
        ruleDao.upsert(rule)
        val json = manager.export()
            .replace("\"version\": ${RemiitBackup.VERSION}", "\"version\": 99")
        ruleDao.rules.value = emptyList()

        val result = manager.import(json)

        assertTrue(result is ImportResult.Failure)
        assertTrue("nothing should have been imported", rules.allRules().isEmpty())
    }

    /** An older build must be able to read a file a newer one wrote extra keys into. */
    @Test
    fun `unknown fields are ignored`() = runTest {
        ruleDao.upsert(rule)
        val json = manager.export()
            .replace("\"format\"", "\"somethingAddedLater\": true,\n  \"format\"")
        ruleDao.rules.value = emptyList()

        assertTrue(manager.import(json) is ImportResult.Success)
    }

    @Test
    fun `an export carries the format marker and version`() = runTest {
        ruleDao.upsert(rule)
        val json = manager.export()

        assertTrue(RemiitBackup.FORMAT in json)
        assertTrue("\"version\": ${RemiitBackup.VERSION}" in json)
    }

    @Test
    fun `an empty backup is refused`() = runTest {
        val json = manager.export()
        assertTrue(manager.import(json) is ImportResult.Failure)
    }
}
