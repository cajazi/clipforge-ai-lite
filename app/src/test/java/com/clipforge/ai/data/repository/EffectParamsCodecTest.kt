package com.clipforge.ai.data.repository

import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing
import com.clipforge.ai.data.local.entity.EffectItemEntity
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class EffectParamsCodecTest {

    @Test
    fun `codec round trip constant params`() {
        val params = mapOf("intensity" to EffectParamValue.Constant(0.7f))
        assertEquals(params, EffectParamsCodec.decode(EffectParamsCodec.encode(params)))
    }

    @Test
    fun `codec round trip keyframed params`() {
        val params = mapOf(
            "radius" to EffectParamValue.Keyframed(
                listOf(
                    Keyframe(0L, 2f, KeyframeEasing.LINEAR),
                    Keyframe(1_000_000L, 12f, KeyframeEasing.SMOOTHSTEP)
                )
            )
        )
        assertEquals(params, EffectParamsCodec.decode(EffectParamsCodec.encode(params)))
    }

    @Test
    fun `codec round trip mixed params`() {
        val params = mapOf(
            "intensity" to EffectParamValue.Constant(0.7f),
            "radius" to EffectParamValue.Keyframed(listOf(Keyframe(0L, 2f)))
        )
        assertEquals(params, EffectParamsCodec.decode(EffectParamsCodec.encode(params)))
    }

    @Test
    fun `empty params legal`() {
        val decoded = EffectParamsCodec.decode(EffectParamsCodec.encode(emptyMap()))
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `reject missing v`() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectParamsCodec.decode("""{"params":{}}""")
        }
    }

    @Test
    fun `reject unknown v`() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectParamsCodec.decode("""{"v":2,"params":{}}""")
        }
    }

    @Test
    fun `reject missing value`() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectParamsCodec.decode("""{"v":1,"params":{"a":{"type":"constant"}}}""")
        }
    }

    @Test
    fun `reject missing frames`() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectParamsCodec.decode("""{"v":1,"params":{"a":{"type":"keyframes"}}}""")
        }
    }

    @Test
    fun `reject unknown type`() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectParamsCodec.decode("""{"v":1,"params":{"a":{"type":"mystery","value":1}}}""")
        }
    }

    @Test
    fun `reject unknown easing`() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectParamsCodec.decode(
                """{"v":1,"params":{"a":{"type":"keyframes","frames":[{"timeUs":0,"value":1,"easing":"BOUNCE"}]}}}"""
            )
        }
    }

    @Test
    fun `reject unsorted keyframes`() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectParamsCodec.decode(
                """{"v":1,"params":{"a":{"type":"keyframes","frames":[{"timeUs":10,"value":1,"easing":"LINEAR"},{"timeUs":5,"value":2,"easing":"LINEAR"}]}}}"""
            )
        }
    }

    @Test
    fun `entity domain mapping round trips`() {
        val item = EffectItem(
            id = "effect-item-1",
            projectId = "project-1",
            effectId = "vhs",
            scope = EffectScope.GLOBAL,
            startMs = 100L,
            endMs = 900L,
            zOrder = 3,
            params = mapOf("intensity" to EffectParamValue.Constant(0.8f))
        )

        val entity = item.toEntity()
        assertEquals("GLOBAL", entity.scope)
        assertEquals(item, entity.toDomain())
    }

    @Test
    fun `clip scope entity is rejected by mapper`() {
        val entity = EffectItemEntity(
            id = "effect-item-1",
            projectId = "project-1",
            effectId = "vhs",
            scope = "CLIP",
            startMs = 0L,
            endMs = 100L,
            zOrder = 0,
            paramsJson = EffectParamsCodec.encode(emptyMap())
        )

        assertThrows(IllegalArgumentException::class.java) { entity.toDomain() }
    }
}
