package com.clipforge.ai.core.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ParamProviderTest {

    // ---------------------------------------------------------------- ConstantParams

    @Test
    fun `constant params return values regardless of time`() {
        val params = ConstantParams(mapOf("intensity" to 0.7f, "radius" to 12f))
        assertEquals(0.7f, params.valueAt("intensity", 0L), 0f)
        assertEquals(0.7f, params.valueAt("intensity", 99_000_000L), 0f)
        assertEquals(12f, params.valueAt("radius", 5L), 0f)
    }

    @Test
    fun `constant params unknown key throws`() {
        val params = ConstantParams(mapOf("intensity" to 0.7f))
        assertThrows(IllegalArgumentException::class.java) { params.valueAt("missing", 0L) }
    }

    @Test
    fun `constant params are immune to source map mutation`() {
        val source = HashMap<String, Float>()
        source["intensity"] = 0.4f
        val params = ConstantParams(source)
        source["intensity"] = 0.9f
        assertEquals(0.4f, params.valueAt("intensity", 0L), 0f)
    }

    // -------------------------------------------------------------------- LiveParams

    @Test
    fun `live params set then read round-trips`() {
        val params = LiveParams(mapOf("intensity" to 0.5f))
        assertEquals(0.5f, params.valueAt("intensity", 0L), 0f)
        params.set("intensity", 0.93f)
        assertEquals(0.93f, params.valueAt("intensity", 0L), 0f)
    }

    @Test
    fun `live params keys are independent`() {
        val params = LiveParams(mapOf("a" to 0.1f, "b" to 0.2f))
        params.set("a", 0.8f)
        assertEquals(0.8f, params.valueAt("a", 0L), 0f)
        assertEquals(0.2f, params.valueAt("b", 0L), 0f)
    }

    @Test
    fun `live params unknown key throws on read and write`() {
        val params = LiveParams(mapOf("a" to 0.1f))
        assertThrows(IllegalArgumentException::class.java) { params.valueAt("missing", 0L) }
        assertThrows(IllegalArgumentException::class.java) { params.set("missing", 1f) }
    }

    @Test
    fun `live params require at least one key`() {
        assertThrows(IllegalArgumentException::class.java) { LiveParams(emptyMap()) }
    }

    // --------------------------------------------------------------- KeyframedParams

    @Test
    fun `keyframed params interpolate over time`() {
        val params = KeyframedParams(
            mapOf("intensity" to listOf(Keyframe(0L, 0f), Keyframe(1_000_000L, 1f)))
        )
        assertEquals(0f, params.valueAt("intensity", 0L), 0f)
        assertEquals(0.5f, params.valueAt("intensity", 500_000L), 1e-6f)
        assertEquals(1f, params.valueAt("intensity", 2_000_000L), 0f)
    }

    @Test
    fun `keyframed params validate tracks at construction`() {
        val unsorted = mapOf("intensity" to listOf(Keyframe(1_000_000L, 0f), Keyframe(0L, 1f)))
        val thrown = assertThrows(IllegalArgumentException::class.java) { KeyframedParams(unsorted) }
        assertEquals(true, thrown.message!!.contains("intensity"))
    }

    @Test
    fun `keyframed params unknown key throws`() {
        val params = KeyframedParams(mapOf("a" to listOf(Keyframe(0L, 1f))))
        assertThrows(IllegalArgumentException::class.java) { params.valueAt("missing", 0L) }
    }

    @Test
    fun `keyframed params support constant single-frame tracks`() {
        val params = KeyframedParams(mapOf("a" to listOf(Keyframe(0L, 0.42f))))
        assertEquals(0.42f, params.valueAt("a", 77_000_000L), 0f)
    }
}
