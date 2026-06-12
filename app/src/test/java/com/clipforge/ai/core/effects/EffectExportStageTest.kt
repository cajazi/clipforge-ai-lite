@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.effects

import android.content.Context
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import com.clipforge.ai.core.overlay.TimePiece
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectExportStageTest {

    @Test
    fun `attachments are sorted by z order`() {
        val registry = registryWith("tint")
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L)))

        val result = EffectExportStage.build(
            effects = listOf(
                item(id = "late", effectId = "tint", zOrder = 10),
                item(id = "early", effectId = "tint", zOrder = 1),
                item(id = "middle", effectId = "tint", zOrder = 5)
            ),
            registry = registry,
            map = map
        )

        assertEquals(listOf(1, 5, 10), result.attachments.map { it.zOrder })
    }

    @Test
    fun `unknown ids are skipped and logged`() {
        val logs = mutableListOf<String>()
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L)))

        val result = EffectExportStage.build(
            effects = listOf(item(effectId = "missing")),
            registry = EffectRegistry(),
            map = map,
            logger = logs::add
        )

        assertTrue(result.attachments.isEmpty())
        assertTrue(logs.any { it.contains("EFFECT_EXPORT_UNKNOWN_ID") })
    }

    @Test
    fun `defaults are merged for missing stored params`() {
        val registry = registryWith("tint", specs = defaultSpecs())
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L)))

        val provider = EffectExportStage.build(
            effects = listOf(item(effectId = "tint", params = mapOf("intensity" to EffectParamValue.Constant(0.9f)))),
            registry = registry,
            map = map
        ).attachments.single().provider

        assertEquals(0.9f, provider.valueAt("intensity", 0L), 0f)
        assertEquals(12f, provider.valueAt("radius", 0L), 0f)
    }

    @Test
    fun `unknown stored params are dropped and logged`() {
        val logs = mutableListOf<String>()
        val registry = registryWith("tint", specs = defaultSpecs())
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L)))

        val provider = EffectExportStage.build(
            effects = listOf(
                item(
                    effectId = "tint",
                    params = mapOf(
                        "intensity" to EffectParamValue.Constant(0.7f),
                        "unknown" to EffectParamValue.Constant(1f)
                    )
                )
            ),
            registry = registry,
            map = map,
            logger = logs::add
        ).attachments.single().provider

        assertEquals(0.7f, provider.valueAt("intensity", 0L), 0f)
        assertTrue(logs.any { it.contains("EFFECT_EXPORT_DROP_UNKNOWN_PARAM") })
    }

    @Test
    fun `out of range constants are clamped and logged`() {
        val logs = mutableListOf<String>()
        val registry = registryWith("tint", specs = defaultSpecs())
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L)))

        val provider = EffectExportStage.build(
            effects = listOf(item(effectId = "tint", params = mapOf("intensity" to EffectParamValue.Constant(3f)))),
            registry = registry,
            map = map,
            logger = logs::add
        ).attachments.single().provider

        assertEquals(1f, provider.valueAt("intensity", 0L), 0f)
        assertTrue(logs.any { it.contains("EFFECT_EXPORT_CLAMP_PARAM") })
    }

    @Test
    fun `boundary straddling timeline windows map to composition windows`() {
        val registry = registryWith("tint")
        val map = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(2_000L, 2_000L),
                TimePiece(1_000L, 500L),
                TimePiece(2_000L, 2_000L)
            )
        )

        val attachment = EffectExportStage.build(
            effects = listOf(item(effectId = "tint", startMs = 2_550L, endMs = 2_800L)),
            registry = registry,
            map = map
        ).attachments.single()

        assertEquals(2_275_000L, attachment.windowStartUs)
        assertEquals(2_400_000L, attachment.windowEndUs)
    }

    @Test
    fun `keyframes are shifted into composition time`() {
        val registry = registryWith("tint", specs = defaultSpecs())
        val map = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(2_000L, 2_000L),
                TimePiece(1_000L, 500L),
                TimePiece(2_000L, 2_000L)
            )
        )

        val provider = EffectExportStage.build(
            effects = listOf(
                item(
                    effectId = "tint",
                    startMs = 2_550L,
                    endMs = 2_800L,
                    params = mapOf(
                        "intensity" to EffectParamValue.Keyframed(
                            listOf(Keyframe(0L, 0f), Keyframe(100_000L, 1f))
                        )
                    )
                )
            ),
            registry = registry,
            map = map
        ).attachments.single().provider

        assertEquals(0f, provider.valueAt("intensity", 2_275_000L), 0f)
        assertEquals(0.5f, provider.valueAt("intensity", 2_325_000L), 1e-6f)
        assertEquals(1f, provider.valueAt("intensity", 2_500_000L), 0f)
        assertEquals(12f, provider.valueAt("radius", 2_275_000L), 0f)
    }

    @Test
    fun `provider selection follows stored params`() {
        val registry = registryWith("tint", specs = defaultSpecs())
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L)))

        val constant = EffectExportStage.build(
            effects = listOf(item(effectId = "tint", params = mapOf("intensity" to EffectParamValue.Constant(0.8f)))),
            registry = registry,
            map = map
        ).attachments.single().provider
        val keyframed = EffectExportStage.build(
            effects = listOf(
                item(
                    effectId = "tint",
                    params = mapOf("intensity" to EffectParamValue.Keyframed(listOf(Keyframe(0L, 0.1f))))
                )
            ),
            registry = registry,
            map = map
        ).attachments.single().provider

        assertTrue(constant is ConstantParams)
        assertTrue(keyframed is KeyframedParams)
    }

    private fun registryWith(
        id: String,
        specs: List<ParamSpec> = listOf(ParamSpec("intensity", "Intensity", 0f, 1f, 0.5f))
    ): EffectRegistry {
        val registry = EffectRegistry()
        registry.register(
            EffectRegistration(
                descriptor = EffectDescriptor(
                    id = id,
                    displayName = id,
                    category = EffectCategory.TRENDY,
                    paramSpecs = specs
                ),
                factory = EffectFactory { _, _, _ -> FakeGlEffect }
            )
        )
        return registry
    }

    private fun defaultSpecs() = listOf(
        ParamSpec("intensity", "Intensity", 0f, 1f, 0.5f),
        ParamSpec("radius", "Radius", 0f, 20f, 12f)
    )

    private fun item(
        id: String = "effect",
        effectId: String = "effect",
        startMs: Long = 0L,
        endMs: Long = 1_000L,
        zOrder: Int = 0,
        params: Map<String, EffectParamValue> = emptyMap()
    ) = EffectItem(
        id = id,
        projectId = "project",
        effectId = effectId,
        scope = EffectScope.GLOBAL,
        startMs = startMs,
        endMs = endMs,
        zOrder = zOrder,
        params = params
    )

    private object FakeGlEffect : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram {
            error("FakeGlEffect is never rendered in JVM tests")
        }
    }
}
