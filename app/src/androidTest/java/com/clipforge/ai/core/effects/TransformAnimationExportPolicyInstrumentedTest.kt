@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.effects

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.gl.TransformAnimationGlEffect
import com.clipforge.ai.core.overlay.TimePiece
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransformAnimationExportPolicyInstrumentedTest {

    @Test
    fun currentPolicyAttachesTransformAnimationAndEmptyPolicySkipsIt() {
        val registry = EffectRegistry().apply {
            registerTransformAnimationEffect()
        }
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L)))

        val current = EffectExportStage.build(
            effects = listOf(transformItem()),
            registry = registry,
            map = map,
            releasePolicy = EffectExportPolicy.current
        )

        assertEquals(listOf(AnimationEffectRegistrations.TRANSFORM_ANIMATION), current.attachments.map { it.effectId })
        assertTrue(current.effects.single() is TransformAnimationGlEffect)

        val logs = mutableListOf<String>()
        val empty = EffectExportStage.build(
            effects = listOf(transformItem()),
            registry = registry,
            map = map,
            releasePolicy = EffectReleasePolicy(),
            logger = logs::add
        )

        assertTrue(empty.attachments.isEmpty())
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
}
