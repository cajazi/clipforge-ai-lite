@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.effects

import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.gl.AnimationEffectFactory
import com.clipforge.ai.core.gl.TransformAnimationGlEffect
import com.clipforge.ai.core.overlay.TimePiece
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationEffectRegistrationsTest {

    @Test
    fun `transform animation registration is idempotent and creates gl effect`() {
        val registry = EffectRegistry()

        registry.registerTransformAnimationEffect()
        registry.registerTransformAnimationEffect()

        val registration = registry.get(AnimationEffectRegistrations.TRANSFORM_ANIMATION)!!
        assertEquals(1, registry.all().size)
        assertSame(AnimationEffectFactory, registration.factory)
        assertEquals(TransformAnimationGlEffect::class.java, registration.factory.create(0L, 1_000L, ConstantParams(emptyMap()))::class.java)
    }

    @Test
    fun `transform animation descriptor uses animation property defaults`() {
        val specs = AnimationEffectRegistrations.transformAnimationDescriptor.paramSpecs

        assertEquals(
            listOf(
                AnimationPropertyKeys.POSITION_X,
                AnimationPropertyKeys.POSITION_Y,
                AnimationPropertyKeys.SCALE_X,
                AnimationPropertyKeys.SCALE_Y,
                AnimationPropertyKeys.ROTATION,
                AnimationPropertyKeys.OPACITY,
                AnimationPropertyKeys.ANCHOR_X,
                AnimationPropertyKeys.ANCHOR_Y
            ),
            specs.map { it.key }
        )
        specs.forEach { spec ->
            assertEquals(AnimationPropertyKeys.defaultValue(spec.key)!!, spec.default, 0f)
        }
    }

    @Test
    fun `registration alone does not make transform animation export ready or released`() {
        val registry = EffectRegistry().apply { registerTransformAnimationEffect() }
        val policy = EffectReleasePolicy()
        val logs = mutableListOf<String>()

        val result = EffectExportStage.build(
            effects = listOf(transformItem()),
            registry = registry,
            map = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L))),
            releasePolicy = policy,
            logger = logs::add
        )

        assertFalse(policy.isExportReady(AnimationEffectRegistrations.TRANSFORM_ANIMATION))
        assertFalse(policy.isReleased(AnimationEffectRegistrations.TRANSFORM_ANIMATION))
        assertTrue(result.attachments.isEmpty())
        assertTrue(logs.any { it.contains("EFFECT_EXPORT_SKIPPED reason=not_export_ready") })
    }

    private fun transformItem() = EffectItem(
        id = "transform-item",
        projectId = "project",
        effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
        scope = EffectScope.GLOBAL,
        startMs = 0L,
        endMs = 1_000L,
        zOrder = 0,
        params = mapOf(
            AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(0.5f)
        )
    )
}
