package com.rahulgorai.remiit.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class QuietHoursTest {

    @Test
    fun `a same-day window contains only its own range`() {
        val quiet = QuietHours(startMinuteOfDay = 13 * 60, endMinuteOfDay = 14 * 60)
        assertTrue(quiet.contains(13 * 60))
        assertTrue(quiet.contains(13 * 60 + 30))
        assertFalse(quiet.contains(14 * 60)) // end is exclusive
        assertFalse(quiet.contains(12 * 60 + 59))
    }

    @Test
    fun `a window spanning midnight contains times on both sides of it`() {
        // 22:00-07:00 is the case that a naive start..end range gets wrong: it
        // would match nothing at all.
        val quiet = QuietHours(startMinuteOfDay = 22 * 60, endMinuteOfDay = 7 * 60)
        assertTrue(quiet.contains(22 * 60))
        assertTrue(quiet.contains(23 * 60 + 59))
        assertTrue(quiet.contains(0))
        assertTrue(quiet.contains(6 * 60 + 59))
        assertFalse(quiet.contains(7 * 60))
        assertFalse(quiet.contains(12 * 60))
    }
}

class RuleConstraintsTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant()

    @Test
    fun `default constraints allow everything`() {
        assertTrue(RuleConstraints().allows(at(2026, 8, 24, 3, 0), zone))
    }

    @Test
    fun `quiet hours block a fire inside the window`() {
        val constraints = RuleConstraints(quietHours = QuietHours(22 * 60, 7 * 60))
        assertFalse(constraints.allows(at(2026, 8, 24, 3, 0), zone))
        assertTrue(constraints.allows(at(2026, 8, 24, 9, 0), zone))
    }

    @Test
    fun `active days restrict to the listed weekdays`() {
        // 2026-08-24 is a Monday, 2026-08-29 a Saturday.
        val weekdaysOnly = RuleConstraints(activeDays = setOf(1, 2, 3, 4, 5))
        assertTrue(weekdaysOnly.allows(at(2026, 8, 24, 9, 0), zone))
        assertFalse(weekdaysOnly.allows(at(2026, 8, 29, 9, 0), zone))
    }

    @Test
    fun `an empty active-days set means every day`() {
        val any = RuleConstraints(activeDays = emptySet())
        assertTrue(any.allows(at(2026, 8, 29, 9, 0), zone))
    }

    @Test
    fun `validity range bounds the rule on both sides`() {
        val constraints = RuleConstraints(
            validFromEpochMillis = at(2026, 8, 20, 0, 0).toEpochMilli(),
            validUntilEpochMillis = at(2026, 8, 25, 0, 0).toEpochMilli(),
        )
        assertFalse(constraints.allows(at(2026, 8, 19, 12, 0), zone))
        assertTrue(constraints.allows(at(2026, 8, 22, 12, 0), zone))
        assertFalse(constraints.allows(at(2026, 8, 26, 12, 0), zone))
    }
}
