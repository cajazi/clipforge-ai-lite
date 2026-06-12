package com.clipforge.ai.core.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KeyframeSamplerTest {

    private val track = listOf(
        Keyframe(0L, 0f),
        Keyframe(1_000_000L, 1f),
        Keyframe(2_000_000L, 0.5f)
    )

    @Test
    fun `linear midpoint interpolates halfway`() {
        assertEquals(0.5f, KeyframeSampler.sample(track, 500_000L), 1e-6f)
        assertEquals(0.75f, KeyframeSampler.sample(track, 1_500_000L), 1e-6f)
    }

    @Test
    fun `linear quarter point interpolates proportionally`() {
        assertEquals(0.25f, KeyframeSampler.sample(track, 250_000L), 1e-6f)
    }

    @Test
    fun `smoothstep easing shapes the fraction`() {
        val eased = listOf(
            Keyframe(0L, 0f, KeyframeEasing.SMOOTHSTEP),
            Keyframe(1_000_000L, 1f)
        )
        // smoothstep(0.25) = 0.25^2 * (3 - 0.5) = 0.15625
        assertEquals(0.15625f, KeyframeSampler.sample(eased, 250_000L), 1e-6f)
        // smoothstep(0.5) = 0.5
        assertEquals(0.5f, KeyframeSampler.sample(eased, 500_000L), 1e-6f)
        // smoothstep(0.75) = 0.84375
        assertEquals(0.84375f, KeyframeSampler.sample(eased, 750_000L), 1e-6f)
    }

    @Test
    fun `clamps before first and after last frame`() {
        assertEquals(0f, KeyframeSampler.sample(track, -5_000_000L), 0f)
        assertEquals(0.5f, KeyframeSampler.sample(track, 99_000_000L), 0f)
    }

    @Test
    fun `exact keyframe times return exact values`() {
        assertEquals(0f, KeyframeSampler.sample(track, 0L), 0f)
        assertEquals(1f, KeyframeSampler.sample(track, 1_000_000L), 0f)
        assertEquals(0.5f, KeyframeSampler.sample(track, 2_000_000L), 0f)
    }

    @Test
    fun `single frame track is constant`() {
        val single = listOf(Keyframe(500_000L, 0.7f))
        assertEquals(0.7f, KeyframeSampler.sample(single, 0L), 0f)
        assertEquals(0.7f, KeyframeSampler.sample(single, 500_000L), 0f)
        assertEquals(0.7f, KeyframeSampler.sample(single, 9_000_000L), 0f)
    }

    @Test
    fun `empty track throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyframeSampler.sample(emptyList(), 0L)
        }
    }

    @Test
    fun `unsorted track throws`() {
        val unsorted = listOf(Keyframe(1_000_000L, 0f), Keyframe(0L, 1f))
        assertThrows(IllegalArgumentException::class.java) {
            KeyframeSampler.sample(unsorted, 500_000L)
        }
    }

    @Test
    fun `duplicate-time track throws`() {
        val duplicate = listOf(Keyframe(0L, 0f), Keyframe(0L, 1f))
        assertThrows(IllegalArgumentException::class.java) {
            KeyframeSampler.sample(duplicate, 0L)
        }
    }

    @Test
    fun `binary search agrees with linear scan on a long track`() {
        val long = (0 until 100).map { Keyframe(it * 100_000L, it.toFloat()) }
        for (tUs in 0L..9_900_000L step 37_000L) {
            val viaSampler = KeyframeSampler.sample(long, tUs)
            // linear-scan reference
            val i = long.indexOfLast { it.timeUs <= tUs }
            val expected = if (i == long.lastIndex) {
                long[i].value
            } else {
                val from = long[i]
                val to = long[i + 1]
                val f = (tUs - from.timeUs).toFloat() / (to.timeUs - from.timeUs).toFloat()
                from.value + (to.value - from.value) * f
            }
            assertEquals("tUs=$tUs", expected, viaSampler, 1e-4f)
        }
    }
}
