@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.player

import android.content.Context
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.ConstantParams
import com.clipforge.ai.core.effects.EffectCategory
import com.clipforge.ai.core.effects.EffectDescriptor
import com.clipforge.ai.core.effects.EffectFactory
import com.clipforge.ai.core.effects.EffectRegistration
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframedParams
import com.clipforge.ai.core.effects.LiveParams
import com.clipforge.ai.core.effects.ParamSpec
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectPreviewPlanTest {

    @Test
    fun `constant param values are excluded from structural key`() {
        val registry = registry()

        val a = EffectPreviewPlan.build(
            effects = listOf(item(params = mapOf("intensity" to EffectParamValue.Constant(0.1f)))),
            registry = registry
        )
        val b = EffectPreviewPlan.build(
            effects = listOf(item(params = mapOf("intensity" to EffectParamValue.Constant(0.9f)))),
            registry = registry
        )

        assertEquals(a.structuralKeys, b.structuralKeys)
    }

    @Test
    fun `structural key covers required fields`() {
        val registry = registry()

        val key = EffectPreviewPlan.build(
            effects = listOf(
                item(
                    id = "id-1",
                    effectId = "effect",
                    startMs = 10L,
                    endMs = 20L,
                    zOrder = 7,
                    params = mapOf("intensity" to EffectParamValue.Keyframed(listOf(Keyframe(0L, 0.2f))))
                )
            ),
            registry = registry
        ).structuralKeys.single()

        assertEquals("id-1", key.id)
        assertEquals("effect", key.effectId)
        assertEquals(10L, key.startMs)
        assertEquals(20L, key.endMs)
        assertEquals(7, key.zOrder)
        assertEquals("intensity=[0:0.2:LINEAR]", key.keyframedParamsSignature)
    }

    @Test
    fun `plan caps to three effects after z order sorting`() {
        val logs = mutableListOf<String>()
        val registry = registry()

        val result = EffectPreviewPlan.build(
            effects = listOf(
                item(id = "four", zOrder = 4),
                item(id = "one", zOrder = 1),
                item(id = "three", zOrder = 3),
                item(id = "two", zOrder = 2)
            ),
            registry = registry,
            logger = logs::add
        )

        assertEquals(listOf("one", "two", "three"), result.structuralKeys.map { it.id })
        assertTrue(logs.any { it.contains("EFFECT_PREVIEW_CAPPED") })
    }

    @Test
    fun `preview plan reuses merge and clamp resolver`() {
        val logs = mutableListOf<String>()
        val registry = registry()

        val provider = EffectPreviewPlan.build(
            effects = listOf(
                item(
                    params = mapOf(
                        "intensity" to EffectParamValue.Constant(3f),
                        "unknown" to EffectParamValue.Constant(1f)
                    )
                )
            ),
            registry = registry,
            logger = logs::add
        ).attachments.single().provider

        assertEquals(1f, provider.valueAt("intensity", 0L), 0f)
        assertEquals(12f, provider.valueAt("radius", 0L), 0f)
        assertTrue(logs.any { it.contains("EFFECT_PREVIEW_CLAMP_PARAM") })
        assertTrue(logs.any { it.contains("EFFECT_PREVIEW_DROP_UNKNOWN_PARAM") })
    }

    @Test
    fun `keyframes shift by timeline window start`() {
        val registry = registry()

        val provider = EffectPreviewPlan.build(
            effects = listOf(
                item(
                    startMs = 1_000L,
                    endMs = 2_000L,
                    params = mapOf(
                        "intensity" to EffectParamValue.Keyframed(
                            listOf(Keyframe(0L, 0f), Keyframe(100_000L, 1f))
                        )
                    )
                )
            ),
            registry = registry
        ).attachments.single().provider

        assertTrue(provider is KeyframedParams)
        assertEquals(0f, provider.valueAt("intensity", 1_000_000L), 0f)
        assertEquals(0.5f, provider.valueAt("intensity", 1_050_000L), 1e-6f)
    }

    @Test
    fun `pending overlay reseeds live params without changing structural key`() {
        val registry = registry()
        val effect = item(params = mapOf("intensity" to EffectParamValue.Constant(0.2f)))

        val base = EffectPreviewPlan.build(effects = listOf(effect), registry = registry)
        val overlaid = EffectPreviewPlan.build(
            effects = listOf(effect),
            registry = registry,
            pendingLiveValues = mapOf(effect.id to mapOf("intensity" to 0.8f))
        )

        assertEquals(base.structuralKeys, overlaid.structuralKeys)
        assertTrue(overlaid.attachments.single().provider is LiveParams)
        assertEquals(0.8f, overlaid.attachments.single().provider.valueAt("intensity", 0L), 0f)
    }

    @Test
    fun `snapshot mode remains available for export resolver users`() {
        val registry = registry()

        val provider = com.clipforge.ai.core.effects.EffectParamResolver.resolve(
            itemId = "effect",
            storedParams = emptyMap(),
            specs = registry.get("effect")!!.descriptor.paramSpecs,
            windowStartUs = 0L,
            constantMode = com.clipforge.ai.core.effects.EffectParamResolver.ConstantMode.Snapshot,
            logPrefix = "TEST"
        ).provider

        assertTrue(provider is ConstantParams)
    }

    @Test
    fun `clip transform animations are included`() {
        val registry = registry(AnimationEffectRegistrations.TRANSFORM_ANIMATION)

        val result = EffectPreviewPlan.build(
            effects = listOf(
                item(
                    id = AnimationEffectId.of("clip-1", AnimationRole.IN),
                    effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
                    scope = EffectScope.CLIP
                )
            ),
            registry = registry
        )

        assertEquals(listOf(AnimationEffectId.of("clip-1", AnimationRole.IN)), result.structuralKeys.map { it.id })
    }

    @Test
    fun `non-transform clip effects are still skipped`() {
        val logs = mutableListOf<String>()
        val registry = registry()

        val result = EffectPreviewPlan.build(
            effects = listOf(item(scope = EffectScope.CLIP)),
            registry = registry,
            logger = logs::add
        )

        assertTrue(result.attachments.isEmpty())
        assertTrue(logs.any { it.contains("EFFECT_PREVIEW_SKIP_SCOPE") })
    }

    private fun registry(id: String = "effect"): EffectRegistry {
        val registry = EffectRegistry()
        registry.register(
            EffectRegistration(
                descriptor = EffectDescriptor(
                    id = id,
                    displayName = "Effect",
                    category = EffectCategory.TRENDY,
                    paramSpecs = listOf(
                        ParamSpec("intensity", "Intensity", 0f, 1f, 0.5f),
                        ParamSpec("radius", "Radius", 0f, 20f, 12f)
                    )
                ),
                factory = EffectFactory { _, _, _ -> FakeGlEffect }
            )
        )
        return registry
    }

    private fun item(
        id: String = "effect-item",
        effectId: String = "effect",
        startMs: Long = 0L,
        endMs: Long = 1_000L,
        zOrder: Int = 0,
        scope: EffectScope = EffectScope.GLOBAL,
        params: Map<String, EffectParamValue> = emptyMap()
    ) = EffectItem(
        id = id,
        projectId = "project",
        effectId = effectId,
        scope = scope,
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
