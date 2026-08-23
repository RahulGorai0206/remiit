package com.rahulgorai.remiit.engine

import com.rahulgorai.remiit.data.model.MatchMode
import com.rahulgorai.remiit.data.model.QuietHours
import com.rahulgorai.remiit.data.model.Recurrence
import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.RuleConstraints
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.data.model.WifiEvent
import com.rahulgorai.remiit.data.repo.RuleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * The engine's gatekeeping. These are the rules that make the app usable rather
 * than a notification firehose, and every one of them fails silently — a
 * reminder that does not arrive looks identical to one that was never due.
 */
class RuleEngineTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    /** Test clock whose instant is settable, so cooldowns can be stepped over. */
    private class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    }

    private lateinit var ruleDao: FakeRuleDao
    private lateinit var eventDao: FakeEventDao
    private lateinit var delivery: RecordingDelivery
    private lateinit var repository: RuleRepository
    private lateinit var engine: RuleEngine
    private lateinit var clock: MutableClock

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant()

    @Before
    fun setUp() {
        clock = MutableClock(at(2026, 8, 24, 10, 0), zone)
        ruleDao = FakeRuleDao()
        eventDao = FakeEventDao()
        delivery = RecordingDelivery()
        repository = RuleRepository(ruleDao, eventDao, clock)
        engine = RuleEngine(repository, delivery, clock)
    }

    private val timeTrigger = Trigger.Time("t1", Recurrence.Daily(9 * 60))
    private val wifiTrigger = Trigger.Wifi("w1", "Office", WifiEvent.CONNECTED)

    private fun rule(
        triggers: List<Trigger> = listOf(timeTrigger),
        match: MatchMode = MatchMode.ANY,
        constraints: RuleConstraints = RuleConstraints(),
        enabled: Boolean = true,
    ) = ReminderRule(
        id = "rule-1",
        title = "Drink water",
        isEnabled = enabled,
        triggers = triggers,
        match = match,
        constraints = constraints,
    )

    private suspend fun fire(triggerId: String, at: Instant = clock.now) {
        engine.onTriggerFired(TriggerEvent("rule-1", triggerId, "test", at))
    }

    // ---- Basics ------------------------------------------------------------

    @Test
    fun `a matching trigger on an enabled rule fires`() = runTest {
        ruleDao.upsert(rule())
        fire("t1")
        assertEquals(1, delivery.delivered.size)
    }

    @Test
    fun `a disabled rule does not fire`() = runTest {
        ruleDao.upsert(rule(enabled = false))
        fire("t1")
        assertEquals(0, delivery.delivered.size)
    }

    @Test
    fun `an event for an unknown rule is ignored`() = runTest {
        engine.onTriggerFired(TriggerEvent("nope", "t1", "test", clock.now))
        assertEquals(0, delivery.delivered.size)
    }

    @Test
    fun `every fire is recorded in history`() = runTest {
        ruleDao.upsert(rule())
        fire("t1")
        assertEquals(1, eventDao.events.value.size)
        // The delivered eventId must be the history row, or a later
        // Complete tap cannot be attributed to this firing.
        assertEquals(eventDao.events.value.first().id, delivery.delivered.first().eventId)
    }

    // ---- MatchMode ---------------------------------------------------------

    @Test
    fun `ANY fires on a single trigger even when the rule has several`() = runTest {
        ruleDao.upsert(rule(triggers = listOf(timeTrigger, wifiTrigger), match = MatchMode.ANY))
        fire("t1")
        assertEquals(1, delivery.delivered.size)
    }

    @Test
    fun `ALL waits until every trigger has fired`() = runTest {
        ruleDao.upsert(rule(triggers = listOf(timeTrigger, wifiTrigger), match = MatchMode.ALL))

        fire("t1")
        assertEquals("one of two triggers should not fire", 0, delivery.delivered.size)

        fire("w1")
        assertEquals(1, delivery.delivered.size)
    }

    @Test
    fun `ALL ignores a trigger satisfied outside the match window`() = runTest {
        ruleDao.upsert(
            rule(
                triggers = listOf(timeTrigger, wifiTrigger),
                match = MatchMode.ALL,
                constraints = RuleConstraints(matchWindowMinutes = 15),
            )
        )

        fire("t1", at = clock.now)
        // 20 minutes later the first trigger has aged out, so this is a fresh
        // partial match rather than a completion.
        clock.now = clock.now.plus(20, ChronoUnit.MINUTES)
        fire("w1", at = clock.now)

        assertEquals(0, delivery.delivered.size)
    }

    @Test
    fun `ALL resets after firing so the next round needs every trigger again`() = runTest {
        ruleDao.upsert(rule(triggers = listOf(timeTrigger, wifiTrigger), match = MatchMode.ALL))

        fire("t1")
        fire("w1")
        assertEquals(1, delivery.delivered.size)

        // Without a reset, this single trigger would coast on the stale sibling.
        fire("t1")
        assertEquals(1, delivery.delivered.size)
    }

    @Test
    fun `ALL with one trigger behaves like ANY`() = runTest {
        ruleDao.upsert(rule(triggers = listOf(timeTrigger), match = MatchMode.ALL))
        fire("t1")
        assertEquals(1, delivery.delivered.size)
    }

    // ---- Cooldown ----------------------------------------------------------

    @Test
    fun `cooldown suppresses a second fire inside the window`() = runTest {
        ruleDao.upsert(rule(constraints = RuleConstraints(cooldownMinutes = 30)))

        fire("t1")
        clock.now = clock.now.plus(10, ChronoUnit.MINUTES)
        fire("t1", at = clock.now)

        assertEquals(1, delivery.delivered.size)
    }

    @Test
    fun `cooldown allows a fire once the window has passed`() = runTest {
        ruleDao.upsert(rule(constraints = RuleConstraints(cooldownMinutes = 30)))

        fire("t1")
        clock.now = clock.now.plus(31, ChronoUnit.MINUTES)
        fire("t1", at = clock.now)

        assertEquals(2, delivery.delivered.size)
    }

    @Test
    fun `zero cooldown does not suppress anything`() = runTest {
        ruleDao.upsert(rule(constraints = RuleConstraints(cooldownMinutes = 0)))
        fire("t1")
        fire("t1")
        assertEquals(2, delivery.delivered.size)
    }

    // ---- Daily cap ---------------------------------------------------------

    @Test
    fun `the daily cap stops firing once reached`() = runTest {
        ruleDao.upsert(rule(constraints = RuleConstraints(maxFiresPerDay = 2)))

        repeat(4) {
            clock.now = clock.now.plus(1, ChronoUnit.MINUTES)
            fire("t1", at = clock.now)
        }

        assertEquals(2, delivery.delivered.size)
    }

    @Test
    fun `the daily cap resets on the next local day`() = runTest {
        ruleDao.upsert(rule(constraints = RuleConstraints(maxFiresPerDay = 1)))

        fire("t1")
        assertEquals(1, delivery.delivered.size)

        // Next calendar day in the device zone, not 24 hours later — the cap is
        // per local day.
        clock.now = at(2026, 8, 25, 0, 30)
        fire("t1", at = clock.now)

        assertEquals(2, delivery.delivered.size)
    }

    @Test
    fun `an unlimited cap never blocks`() = runTest {
        ruleDao.upsert(rule(constraints = RuleConstraints(maxFiresPerDay = 0)))
        repeat(5) { fire("t1") }
        assertEquals(5, delivery.delivered.size)
    }

    // ---- Quiet hours and active days ---------------------------------------

    @Test
    fun `quiet hours block a fire in the middle of the night`() = runTest {
        ruleDao.upsert(
            rule(constraints = RuleConstraints(quietHours = QuietHours(22 * 60, 7 * 60)))
        )
        clock.now = at(2026, 8, 24, 3, 0)
        fire("t1", at = clock.now)
        assertEquals(0, delivery.delivered.size)
    }

    @Test
    fun `a fire outside quiet hours still goes through`() = runTest {
        ruleDao.upsert(
            rule(constraints = RuleConstraints(quietHours = QuietHours(22 * 60, 7 * 60)))
        )
        clock.now = at(2026, 8, 24, 9, 0)
        fire("t1", at = clock.now)
        assertEquals(1, delivery.delivered.size)
    }

    @Test
    fun `active days exclude the weekend`() = runTest {
        ruleDao.upsert(rule(constraints = RuleConstraints(activeDays = setOf(1, 2, 3, 4, 5))))

        // Saturday.
        clock.now = at(2026, 8, 29, 10, 0)
        fire("t1", at = clock.now)
        assertEquals(0, delivery.delivered.size)

        // Monday.
        clock.now = at(2026, 8, 31, 10, 0)
        fire("t1", at = clock.now)
        assertEquals(1, delivery.delivered.size)
    }

    @Test
    fun `a rule past its validity window stops firing`() = runTest {
        ruleDao.upsert(
            rule(
                constraints = RuleConstraints(
                    validUntilEpochMillis = at(2026, 8, 24, 9, 0).toEpochMilli()
                )
            )
        )
        fire("t1") // clock is 10:00, an hour past the end
        assertEquals(0, delivery.delivered.size)
    }

    // ---- Preview -----------------------------------------------------------

    @Test
    fun `preview fires regardless of constraints`() = runTest {
        val quietAtNight = rule(
            constraints = RuleConstraints(
                quietHours = QuietHours(0, 23 * 60),
                maxFiresPerDay = 1,
                cooldownMinutes = 600,
            )
        )
        ruleDao.upsert(quietAtNight)

        engine.previewNow(quietAtNight)
        engine.previewNow(quietAtNight)

        // The point of Preview is to feel the delivery mode now; gating it on the
        // rule's own limits would make it useless exactly when configuring them.
        assertEquals(2, delivery.delivered.size)
    }

    @Test
    fun `UTC devices are handled the same way`() = runTest {
        val utcClock = MutableClock(
            LocalDateTime.of(2026, 8, 24, 3, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC,
        )
        val utcRepo = RuleRepository(ruleDao, eventDao, utcClock)
        val utcEngine = RuleEngine(utcRepo, delivery, utcClock)
        ruleDao.upsert(
            rule(constraints = RuleConstraints(quietHours = QuietHours(22 * 60, 7 * 60)))
        )

        utcEngine.onTriggerFired(TriggerEvent("rule-1", "t1", "test", utcClock.now))
        assertEquals(0, delivery.delivered.size)
    }
}
