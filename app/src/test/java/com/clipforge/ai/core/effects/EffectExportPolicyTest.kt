@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.effects

import android.content.Context
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.gl.TransformAnimationGlEffect
import com.clipforge.ai.core.overlay.TimePiece
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectExportPolicyTest {

    @Test
    fun `current policy makes transform animation export ready but unreleased`() {
        val policy = EffectExportPolicy.current

        assertTrue(policy.isExportReady(AnimationEffectRegistrations.TRANSFORM_ANIMATION))
        assertTrue(policy.exportReadyIds.contains(AnimationEffectRegistrations.TRANSFORM_ANIMATION))
        assertFalse(policy.isReleased(AnimationEffectRegistrations.TRANSFORM_ANIMATION))
        assertTrue(policy.releasedIds.isEmpty())
    }

    @Test
    fun `current policy attaches transform animation in export stage`() {
        val result = EffectExportStage.build(
            effects = listOf(transformItem()),
            registry = transformRegistry(),
            map = timeMap(),
            releasePolicy = EffectExportPolicy.current
        )

        assertEquals(listOf(AnimationEffectRegistrations.TRANSFORM_ANIMATION), result.attachments.map { it.effectId })
        assertTrue(result.effects.single() is TransformAnimationGlEffect)
    }

    @Test
    fun `empty policy skips transform animation in export stage`() {
        val logs = mutableListOf<String>()

        val result = EffectExportStage.build(
            effects = listOf(transformItem()),
            registry = transformRegistry(),
            map = timeMap(),
            releasePolicy = EffectReleasePolicy(),
            logger = logs::add
        )

        assertTrue(result.attachments.isEmpty())
        assertTrue(logs.any { it.contains("EFFECT_EXPORT_SKIPPED reason=not_export_ready") })
    }

    @Test
    fun `unreleased transform animation remains hidden from release policy consumers`() {
        assertFalse(EffectExportPolicy.current.isReleased(AnimationEffectRegistrations.TRANSFORM_ANIMATION))
    }

    @Test
    fun `non transform registered effect remains skipped when not export ready`() {
        val logs = mutableListOf<String>()
        val registry = EffectRegistry().apply {
            register(
                EffectRegistration(
                    descriptor = EffectDescriptor(
                        id = "unreleased_effect",
                        displayName = "Unreleased",
                        category = EffectCategory.TRENDY,
                        paramSpecs = emptyList()
                    ),
                    factory = EffectFactory { _, _, _ -> FakeGlEffect }
                )
            )
        }

        val result = EffectExportStage.build(
            effects = listOf(item(effectId = "unreleased_effect")),
            registry = registry,
            map = timeMap(),
            releasePolicy = EffectExportPolicy.current,
            logger = logs::add
        )

        assertTrue(result.attachments.isEmpty())
        assertTrue(logs.any { it.contains("EFFECT_EXPORT_SKIPPED reason=not_export_ready") })
    }

    private fun transformRegistry() = EffectRegistry().apply {
        registerTransformAnimationEffect()
    }

    private fun timeMap() = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L)))

    private fun transformItem() = item(
        effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
        params = mapOf(
            AnimationPropertyKeys.POSITION_X to EffectParamValue.Constant(0.1f),
            AnimationPropertyKeys.POSITION_Y to EffectParamValue.Constant(-0.1f),
            AnimationPropertyKeys.SCALE_X to EffectParamValue.Constant(0.5f),
            AnimationPropertyKeys.SCALE_Y to EffectParamValue.Constant(1f),
            AnimationPropertyKeys.ROTATION to EffectParamValue.Constant(15f),
            AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(0.75f),
            AnimationPropertyKeys.ANCHOR_X to EffectParamValue.Constant(0.5f),
            AnimationPropertyKeys.ANCHOR_Y to EffectParamValue.Constant(0.5f)
        )
    )

    private fun item(
        effectId: String,
        params: Map<String, EffectParamValue> = emptyMap()
    ) = EffectItem(
        id = "effect-item",
        projectId = "project",
        effectId = effectId,
        scope = EffectScope.GLOBAL,
        startMs = 0L,
        endMs = 1_000L,
        zOrder = 0,
        params = params
    )

    private object FakeGlEffect : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram {
            error("FakeGlEffect is never rendered in JVM tests")
        }
    }
}
