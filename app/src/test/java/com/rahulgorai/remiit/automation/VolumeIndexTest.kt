package com.rahulgorai.remiit.automation

import com.rahulgorai.remiit.data.model.VolumeStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The percentage-to-index conversion.
 *
 * Worth pinning because the scales involved are tiny — a ring stream commonly
 * has seven steps — so rounding is the difference between "60%" landing on 4
 * or on 5, and because a stream's floor is not always zero.
 */
class VolumeIndexTest {

    @Test
    fun `the ends of the range map exactly`() {
        assertEquals(0, volumeIndexFor(percent = 0, min = 0, max = 7))
        assertEquals(7, volumeIndexFor(percent = 100, min = 0, max = 7))
    }

    @Test
    fun `the middle rounds to the nearest step`() {
        // 50% of 0..7 is 3.5, which rounds up.
        assertEquals(4, volumeIndexFor(percent = 50, min = 0, max = 7))
        // 60% of 0..7 is 4.2.
        assertEquals(4, volumeIndexFor(percent = 60, min = 0, max = 7))
        // 65% of 0..7 is 4.55.
        assertEquals(5, volumeIndexFor(percent = 65, min = 0, max = 7))
    }

    @Test
    fun `a coarse scale still reaches every step`() {
        val reached = (0..100 step 5).map { volumeIndexFor(it, min = 0, max = 7) }.toSet()
        assertEquals((0..7).toSet(), reached)
    }

    @Test
    fun `a fine scale maps proportionally`() {
        assertEquals(0, volumeIndexFor(0, min = 0, max = 25))
        assertEquals(5, volumeIndexFor(20, min = 0, max = 25))
        assertEquals(13, volumeIndexFor(50, min = 0, max = 25))
        assertEquals(25, volumeIndexFor(100, min = 0, max = 25))
    }

    /**
     * Some devices refuse to mute a stream and report a minimum above zero.
     * Interpolating across min..max rather than 0..max is what keeps 0%
     * meaning "as quiet as this stream goes" instead of an invalid index.
     */
    @Test
    fun `a non-zero floor is respected`() {
        assertEquals(1, volumeIndexFor(percent = 0, min = 1, max = 7))
        assertEquals(7, volumeIndexFor(percent = 100, min = 1, max = 7))
        assertEquals(4, volumeIndexFor(percent = 50, min = 1, max = 7))
    }

    @Test
    fun `a degenerate range does not divide by zero`() {
        assertEquals(3, volumeIndexFor(percent = 50, min = 3, max = 3))
        assertEquals(3, volumeIndexFor(percent = 50, min = 3, max = 1))
    }

    @Test
    fun `out of range percentages are clamped rather than producing bad indices`() {
        assertEquals(0, volumeIndexFor(percent = -20, min = 0, max = 7))
        assertEquals(7, volumeIndexFor(percent = 500, min = 0, max = 7))
    }

    @Test
    fun `never produces an index outside the device's range`() {
        for (max in 1..30) {
            for (percent in -10..110) {
                val index = volumeIndexFor(percent, min = 0, max = max)
                assertTrue("$percent% of 0..$max gave $index", index in 0..max)
            }
        }
    }

    /**
     * Muting the ringer is a Do Not Disturb operation however it is reached.
     * Alarms and media sit outside the ringer — which is why they have their
     * own streams — so silencing those is nobody's business but the user's.
     */
    @Test
    fun `only muting a ringer-linked stream needs Do Not Disturb access`() {
        assertTrue(volumeRequiresDndAccess(VolumeStream.RING, 0))
        assertTrue(volumeRequiresDndAccess(VolumeStream.NOTIFICATION, 0))
        assertTrue(volumeRequiresDndAccess(VolumeStream.SYSTEM, 0))

        assertTrue(!volumeRequiresDndAccess(VolumeStream.ALARM, 0))
        assertTrue(!volumeRequiresDndAccess(VolumeStream.MEDIA, 0))

        // Only muting is gated; any audible level is not.
        assertTrue(!volumeRequiresDndAccess(VolumeStream.RING, 10))
    }

    @Test
    fun `media maps to its own level without touching the ringer rules`() {
        assertEquals(0, volumeIndexFor(percent = 0, min = 0, max = 15))
        assertEquals(8, volumeIndexFor(percent = 50, min = 0, max = 15))
        assertEquals(15, volumeIndexFor(percent = 100, min = 0, max = 15))
    }
}
