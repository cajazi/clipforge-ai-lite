package com.clipforge.ai.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeterministicOverlayFrameClockTest {

    @Test
    fun `time to frame mapping uses explicit floor timestamps`() {
        val clock = DeterministicOverlayFrameClock(OverlayFrameRate.FPS_30)

        assertEquals(0L, clock.frameIndexAt(-1L))
        assertEquals(0L, clock.frameIndexAt(0L))
        assertEquals(0L, clock.frameIndexAt(33_332L))
        assertEquals(1L, clock.frameIndexAt(33_333L))
        assertEquals(29L, clock.frameIndexAt(999_999L))
        assertEquals(30L, clock.frameIndexAt(1_000_000L))
    }

    @Test
    fun `frame to time mapping is deterministic`() {
        val clock = DeterministicOverlayFrameClock(OverlayFrameRate.FPS_30)

        assertEquals(0L, clock.frameTimeUs(0L))
        assertEquals(33_333L, clock.frameTimeUs(1L))
        assertEquals(66_666L, clock.frameTimeUs(2L))
        assertEquals(100_000L, clock.frameTimeUs(3L))
        assertEquals(1_000_000L, clock.frameTimeUs(30L))
    }

    @Test
    fun `frame timestamps do not drift over many frames`() {
        val clock = DeterministicOverlayFrameClock(OverlayFrameRate.FPS_30)

        assertEquals(600_000_000L, clock.frameTimeUs(18_000L))
        assertEquals(18_000L, clock.frameIndexAt(clock.frameTimeUs(18_000L)))
    }

    @Test
    fun `frame index round-trips from timestamp for a long sequence`() {
        val clock = DeterministicOverlayFrameClock(OverlayFrameRate.NTSC_29_97)

        for (frameIndex in 0L..10_000L) {
            assertEquals(frameIndex, clock.frameIndexAt(clock.frameTimeUs(frameIndex)))
        }
    }

    @Test
    fun `common ntsc frame rate is represented as rational without decimal drift`() {
        val clock = DeterministicOverlayFrameClock(OverlayFrameRate.NTSC_29_97)

        assertEquals(0L, clock.frameTimeUs(0L))
        assertEquals(33_366L, clock.frameTimeUs(1L))
        assertEquals(1_001_000L, clock.frameTimeUs(30L))
    }

    @Test
    fun `negative frame index is rejected`() {
        val clock = DeterministicOverlayFrameClock()

        assertThrows(IllegalArgumentException::class.java) {
            clock.frameTimeUs(-1L)
        }
    }
}
