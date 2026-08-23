package com.rahulgorai.remiit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Next-fire computation. Every trigger in the app ultimately depends on this
 * being right, and the failure mode is a reminder that silently never arrives.
 */
class RecurrenceTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    /** Zones with a DST transition, used for the spring-forward/fall-back cases. */
    private val london = ZoneId.of("Europe/London")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: ZoneId = kolkata) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant()

    // ---- Once --------------------------------------------------------------

    @Test
    fun `once fires at its instant when still in the future`() {
        val target = at(2026, 9, 1, 10, 0)
        val next = Recurrence.Once(target.toEpochMilli())
            .nextOccurrenceAfter(at(2026, 8, 24, 12, 0), kolkata)
        assertEquals(target, next)
    }

    @Test
    fun `once in the past never fires again`() {
        val next = Recurrence.Once(at(2026, 8, 1, 10, 0).toEpochMilli())
            .nextOccurrenceAfter(at(2026, 8, 24, 12, 0), kolkata)
        assertNull(next)
    }

    @Test
    fun `once does not re-fire at the exact instant it just fired`() {
        // The scheduler re-arms with now == the fire time. An inclusive
        // comparison here would arm an alarm for the past and spin.
        val target = at(2026, 9, 1, 10, 0)
        assertNull(Recurrence.Once(target.toEpochMilli()).nextOccurrenceAfter(target, kolkata))
    }

    // ---- Daily -------------------------------------------------------------

    @Test
    fun `daily fires later the same day when the time has not passed`() {
        val next = Recurrence.Daily(minuteOfDay = 9 * 60)
            .nextOccurrenceAfter(at(2026, 8, 24, 7, 30), kolkata)
        assertEquals(at(2026, 8, 24, 9, 0), next)
    }

    @Test
    fun `daily rolls to tomorrow once the time has passed`() {
        val next = Recurrence.Daily(minuteOfDay = 9 * 60)
            .nextOccurrenceAfter(at(2026, 8, 24, 9, 0), kolkata)
        assertEquals(at(2026, 8, 25, 9, 0), next)
    }

    @Test
    fun `daily at midnight resolves to the start of the next day`() {
        val next = Recurrence.Daily(minuteOfDay = 0)
            .nextOccurrenceAfter(at(2026, 8, 24, 23, 59), kolkata)
        assertEquals(at(2026, 8, 25, 0, 0), next)
    }

    // ---- Weekly ------------------------------------------------------------

    @Test
    fun `weekly picks the next selected weekday`() {
        // 2026-08-24 is a Monday. Wednesday(3) and Friday(5) selected.
        val next = Recurrence.Weekly(daysOfWeek = setOf(3, 5), minuteOfDay = 8 * 60)
            .nextOccurrenceAfter(at(2026, 8, 24, 12, 0), kolkata)
        assertEquals(at(2026, 8, 26, 8, 0), next)
    }

    @Test
    fun `weekly wraps to next week when today is the only day and it has passed`() {
        // Monday selected, and Monday 08:00 is already gone.
        val next = Recurrence.Weekly(daysOfWeek = setOf(1), minuteOfDay = 8 * 60)
            .nextOccurrenceAfter(at(2026, 8, 24, 12, 0), kolkata)
        assertEquals(at(2026, 8, 31, 8, 0), next)
    }

    @Test
    fun `weekly with no days selected never fires`() {
        assertNull(
            Recurrence.Weekly(daysOfWeek = emptySet(), minuteOfDay = 480)
                .nextOccurrenceAfter(at(2026, 8, 24, 12, 0), kolkata)
        )
    }

    @Test
    fun `weekly ignores out-of-range day numbers`() {
        assertNull(
            Recurrence.Weekly(daysOfWeek = setOf(0, 8, 99), minuteOfDay = 480)
                .nextOccurrenceAfter(at(2026, 8, 24, 12, 0), kolkata)
        )
    }

    // ---- Monthly -----------------------------------------------------------

    @Test
    fun `monthly fires this month when the day is still ahead`() {
        val next = Recurrence.Monthly(dayOfMonth = 28, minuteOfDay = 10 * 60)
            .nextOccurrenceAfter(at(2026, 8, 24, 12, 0), kolkata)
        assertEquals(at(2026, 8, 28, 10, 0), next)
    }

    @Test
    fun `monthly rolls to next month once the day has passed`() {
        val next = Recurrence.Monthly(dayOfMonth = 10, minuteOfDay = 10 * 60)
            .nextOccurrenceAfter(at(2026, 8, 24, 12, 0), kolkata)
        assertEquals(at(2026, 9, 10, 10, 0), next)
    }

    @Test
    fun `monthly day 31 clamps to the last day of a short month`() {
        // February 2027 has 28 days. Day 31 must still fire, not be skipped.
        val next = Recurrence.Monthly(dayOfMonth = 31, minuteOfDay = 9 * 60)
            .nextOccurrenceAfter(at(2027, 2, 1, 0, 0), kolkata)
        assertEquals(at(2027, 2, 28, 9, 0), next)
    }

    // ---- Interval ----------------------------------------------------------

    @Test
    fun `interval steps through its daily window`() {
        // Hourly, 09:00-18:00. At 09:30 the next slot is 10:00.
        val next = Recurrence.Interval(everyMinutes = 60, startMinuteOfDay = 540, endMinuteOfDay = 1080)
            .nextOccurrenceAfter(at(2026, 8, 24, 9, 30), kolkata)
        assertEquals(at(2026, 8, 24, 10, 0), next)
    }

    @Test
    fun `interval before the window opens fires at the window start`() {
        val next = Recurrence.Interval(everyMinutes = 60, startMinuteOfDay = 540, endMinuteOfDay = 1080)
            .nextOccurrenceAfter(at(2026, 8, 24, 6, 0), kolkata)
        assertEquals(at(2026, 8, 24, 9, 0), next)
    }

    @Test
    fun `interval past the window close rolls to the next day`() {
        val next = Recurrence.Interval(everyMinutes = 60, startMinuteOfDay = 540, endMinuteOfDay = 1080)
            .nextOccurrenceAfter(at(2026, 8, 24, 19, 0), kolkata)
        assertEquals(at(2026, 8, 25, 9, 0), next)
    }

    @Test
    fun `interval does not overshoot the window end`() {
        // Last slot inside 09:00-18:00 stepping 60 is 18:00 itself.
        val next = Recurrence.Interval(everyMinutes = 60, startMinuteOfDay = 540, endMinuteOfDay = 1080)
            .nextOccurrenceAfter(at(2026, 8, 24, 17, 30), kolkata)
        assertEquals(at(2026, 8, 24, 18, 0), next)
    }

    @Test
    fun `interval with start not before end is treated as all day`() {
        val next = Recurrence.Interval(everyMinutes = 30, startMinuteOfDay = 600, endMinuteOfDay = 600)
            .nextOccurrenceAfter(at(2026, 8, 24, 0, 10), kolkata)
        assertEquals(at(2026, 8, 24, 0, 30), next)
    }

    @Test
    fun `interval clamps a zero step instead of looping forever`() {
        val next = Recurrence.Interval(everyMinutes = 0, startMinuteOfDay = 0, endMinuteOfDay = 60)
            .nextOccurrenceAfter(at(2026, 8, 24, 0, 0), kolkata)
        assertEquals(at(2026, 8, 24, 0, 1), next)
    }

    // ---- Daylight saving ---------------------------------------------------

    @Test
    fun `daily keeps its local wall-clock time across spring forward`() {
        // London springs forward 2027-03-28 01:00 -> 02:00. A 09:00 reminder
        // must stay 09:00 local, not drift to 10:00 the way adding elapsed
        // minutes to the start of day would.
        val next = Recurrence.Daily(minuteOfDay = 9 * 60)
            .nextOccurrenceAfter(at(2027, 3, 27, 12, 0, london), london)
        assertEquals(9, next!!.atZone(london).hour)
        assertEquals(LocalDate.of(2027, 3, 28), next.atZone(london).toLocalDate())
    }

    @Test
    fun `daily keeps its local wall-clock time across fall back`() {
        // New York falls back 2026-11-01 02:00 -> 01:00.
        val next = Recurrence.Daily(minuteOfDay = 9 * 60)
            .nextOccurrenceAfter(at(2026, 10, 31, 12, 0, newYork), newYork)
        assertEquals(9, next!!.atZone(newYork).hour)
        assertEquals(LocalDate.of(2026, 11, 1), next.atZone(newYork).toLocalDate())
    }

    @Test
    fun `a time inside the spring-forward gap resolves forward rather than failing`() {
        // London jumps 01:00 -> 02:00 on 2027-03-28, so 01:30 does not exist
        // that day. The reminder must still produce an instant — shifted past
        // the gap to 02:30 — instead of throwing or being silently dropped.
        val next = Recurrence.Daily(minuteOfDay = 60 + 30)
            .nextOccurrenceAfter(at(2027, 3, 28, 0, 30, london), london)
        val local = next!!.atZone(london)
        assertEquals(LocalDate.of(2027, 3, 28), local.toLocalDate())
        assertEquals(2, local.hour)
        assertEquals(30, local.minute)
    }
}
