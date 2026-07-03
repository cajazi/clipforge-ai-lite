package com.clipforge.ai.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TimelineToCompositionTimeMapTest {

    @Test
    fun `identity no-transition timeline`() {
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(5_000L, 5_000L)))

        assertEquals(0L, map.toCompositionMs(-100L))
        assertEquals(0L, map.toCompositionMs(0L))
        assertEquals(1_234L, map.toCompositionMs(1_234L))
        assertEquals(5_000L, map.toCompositionMs(5_000L))
        assertEquals(5_000L, map.toCompositionMs(6_000L))
        assertEquals(5_000L, map.timelineTotalMs)
        assertEquals(5_000L, map.compositionTotalMs)
    }

    @Test
    fun `single overlap exhaustive window positions`() {
        val map = twoClipOverlapMap()

        assertEquals(0L, map.toCompositionMs(0L))
        assertEquals(1_999L, map.toCompositionMs(1_999L))
        assertEquals(2_000L, map.toCompositionMs(2_000L))
        assertEquals(2_125L, map.toCompositionMs(2_250L))
        assertEquals(2_250L, map.toCompositionMs(2_500L))
        assertEquals(2_375L, map.toCompositionMs(2_750L))
        assertEquals(2_500L, map.toCompositionMs(3_000L))
        assertEquals(3_000L, map.toCompositionMs(3_500L))
        assertEquals(4_500L, map.toCompositionMs(5_000L))
    }

    @Test
    fun `B-head window maps through overlap compression`() {
        val map = twoClipOverlapMap()

        val mapped = map.mapWindow(2_550L, 2_800L)

        assertEquals(2_275L, mapped.first)
        assertEquals(2_400L, mapped.last)
    }

    @Test
    fun `window before overlap maps unchanged`() {
        val map = twoClipOverlapMap()

        val mapped = map.mapWindow(500L, 1_500L)

        assertEquals(500L, mapped.first)
        assertEquals(1_500L, mapped.last)
    }

    @Test
    fun `window inside overlap maps through compressed segment`() {
        val map = twoClipOverlapMap()

        val mapped = map.mapWindow(2_250L, 2_750L)

        assertEquals(2_125L, mapped.first)
        assertEquals(2_375L, mapped.last)
    }

    @Test
    fun `window after overlap maps with consumed duration removed`() {
        val map = twoClipOverlapMap()

        val mapped = map.mapWindow(3_500L, 4_500L)

        assertEquals(3_000L, mapped.first)
        assertEquals(4_000L, mapped.last)
    }

    @Test
    fun `window spanning overlap boundary maps start and end across segments`() {
        val map = twoClipOverlapMap()

        val mapped = map.mapWindow(1_750L, 3_250L)

        assertEquals(1_750L, mapped.first)
        assertEquals(2_750L, mapped.last)
    }

    @Test
    fun `dip identity`() {
        val map = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(2_000L, 2_000L),
                TimePiece(1_000L, 1_000L),
                TimePiece(2_000L, 2_000L)
            )
        )

        assertEquals(5_000L, map.timelineTotalMs)
        assertEquals(5_000L, map.compositionTotalMs)
        assertEquals(2_750L, map.toCompositionMs(2_750L))
    }

    @Test
    fun `mixed multi-boundary timeline`() {
        val map = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(1_000L, 1_000L),
                TimePiece(600L, 300L),
                TimePiece(400L, 400L),
                TimePiece(800L, 800L),
                TimePiece(1_000L, 500L)
            )
        )

        assertEquals(3_800L, map.timelineTotalMs)
        assertEquals(3_000L, map.compositionTotalMs)
        assertEquals(1_150L, map.toCompositionMs(1_300L))
        assertEquals(1_700L, map.toCompositionMs(2_000L))
        assertEquals(2_800L, map.toCompositionMs(3_400L))
    }

    @Test
    fun `window spanning multiple boundaries maps both ends`() {
        val map = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(1_000L, 1_000L),
                TimePiece(600L, 300L),
                TimePiece(400L, 400L),
                TimePiece(800L, 800L),
                TimePiece(1_000L, 500L)
            )
        )

        val mapped = map.mapWindow(900L, 3_400L)

        assertEquals(900L, mapped.first)
        assertEquals(2_800L, mapped.last)
    }

    @Test
    fun `property sweep monotonicity`() {
        val map = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(2_000L, 2_000L),
                TimePiece(1_000L, 500L),
                TimePiece(600L, 600L),
                TimePiece(500L, 0L),
                TimePiece(900L, 900L)
            )
        )

        var previous = Long.MIN_VALUE
        for (timelineMs in -100L..5_200L) {
            val current = map.toCompositionMs(timelineMs)
            assertTrue("not monotonic at $timelineMs", current >= previous)
            previous = current
        }
    }

    @Test
    fun `continuity at joints within one millisecond`() {
        val map = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(2_000L, 2_000L),
                TimePiece(1_000L, 500L),
                TimePiece(2_000L, 2_000L)
            )
        )

        listOf(2_000L, 3_000L).forEach { joint ->
            val before = map.toCompositionMs(joint - 1L)
            val at = map.toCompositionMs(joint)
            val after = map.toCompositionMs(joint + 1L)
            assertTrue("before->at discontinuity at $joint", at - before in 0L..1L)
            assertTrue("at->after discontinuity at $joint", after - at in 0L..1L)
        }
    }

    @Test
    fun `mapWindow start is never greater than end`() {
        val map = twoClipOverlapMap()

        val forward = map.mapWindow(2_800L, 2_550L)

        assertTrue(forward.first <= forward.last)
        assertEquals(2_275L, forward.first)
        assertEquals(2_400L, forward.last)
    }

    @Test
    fun `totals oracle overlap and dip`() {
        val overlap = twoClipOverlapMap()
        val dip = TimelineToCompositionTimeMap.build(
            listOf(TimePiece(2_000L, 2_000L), TimePiece(1_000L, 1_000L), TimePiece(2_000L, 2_000L))
        )

        assertEquals(5_000L, overlap.timelineTotalMs)
        assertEquals(4_500L, overlap.compositionTotalMs)
        assertEquals(5_000L, dip.timelineTotalMs)
        assertEquals(5_000L, dip.compositionTotalMs)
    }

    @Test
    fun `reject empty pieces`() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineToCompositionTimeMap.build(emptyList())
        }
    }

    @Test
    fun `reject negative spans`() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineToCompositionTimeMap.build(listOf(TimePiece(-1L, 1L)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimelineToCompositionTimeMap.build(listOf(TimePiece(1L, -1L)))
        }
    }

    @Test
    fun `reject composition from nowhere`() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineToCompositionTimeMap.build(listOf(TimePiece(0L, 1L)))
        }
    }

    @Test
    fun `rounding determinism`() {
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(3L, 2L)))

        assertEquals(0L, map.toCompositionMs(0L))
        assertEquals(0L, map.toCompositionMs(1L))
        assertEquals(1L, map.toCompositionMs(2L))
        assertEquals(2L, map.toCompositionMs(3L))
        assertEquals(map.toCompositionMs(2L), map.toCompositionMs(2L))
    }

    @Test
    fun `zero-composition piece holds composition time`() {
        val map = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(1_000L, 1_000L),
                TimePiece(500L, 0L),
                TimePiece(1_000L, 1_000L)
            )
        )

        assertEquals(1_000L, map.toCompositionMs(1_000L))
        assertEquals(1_000L, map.toCompositionMs(1_250L))
        assertEquals(1_000L, map.toCompositionMs(1_500L))
        assertEquals(1_250L, map.toCompositionMs(1_750L))
    }

    private fun twoClipOverlapMap(): TimelineToCompositionTimeMap =
        TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(2_000L, 2_000L),
                TimePiece(1_000L, 500L),
                TimePiece(2_000L, 2_000L)
            )
        )
}
