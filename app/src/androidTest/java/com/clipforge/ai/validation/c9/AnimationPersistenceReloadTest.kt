package com.clipforge.ai.validation.c9

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectExportPolicy
import com.clipforge.ai.core.effects.EffectExportStage
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.core.overlay.TimePiece
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.repository.EffectRepositoryImpl
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.validation.c9.support.MatrixSampler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C9.0: deterministic clip-animation IDs and resolved matrix/opacity state survive a simulated
 * app restart - reading back through a brand-new [EffectRepositoryImpl] instance (no shared
 * in-memory state) backed by the same on-device Room database, never the codec alone.
 */
@RunWith(AndroidJUnit4::class)
class AnimationPersistenceReloadTest {

    private val registry = EffectRegistry().apply { registerTransformAnimationEffect() }

    @Test
    fun deterministic_ids_and_resolved_state_survive_simulated_restart() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val projectId = "c9_reload_${System.currentTimeMillis()}"
        val clipId = "${projectId}_clip"
        val now = System.currentTimeMillis()

        app.database.projectDao().upsertProject(
            ProjectEntity(
                id = projectId,
                title = "C9 Reload $projectId",
                aspectRatio = "RATIO_9_16",
                exportQuality = "QUALITY_720P",
                planType = "FREE",
                thumbnailUri = null,
                createdAt = now,
                updatedAt = now
            )
        )
        app.database.effectItemDao().deleteForProject(projectId)

        val original = EffectItem(
            id = AnimationEffectId.of(clipId, AnimationRole.COMBO),
            projectId = projectId,
            effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
            scope = EffectScope.CLIP,
            startMs = 100L,
            endMs = 900L,
            zOrder = 0,
            params = easingCoverageParams()
        )
        app.effectRepository.upsertEffect(original)

        // Simulate an app restart: a brand-new repository instance with no shared in-memory
        // state, backed by the same Room database file, must read back identical data.
        val reloadedRepository = EffectRepositoryImpl(app.database.effectItemDao())
        val reloaded = reloadedRepository.getEffectsForProject(projectId).single { it.id == original.id }

        assertEquals("deterministic id survives reload", original.id, reloaded.id)
        val parsed = AnimationEffectId.parse(reloaded.id)
        assertNotNull("reloaded id must remain parseable", parsed)
        assertEquals(clipId, parsed!!.clipId)
        assertEquals(AnimationRole.COMBO, parsed.role)
        assertEquals(original.startMs, reloaded.startMs)
        assertEquals(original.endMs, reloaded.endMs)

        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(1_000L, 1_000L)))
        val originalAttachment =
            EffectExportStage.build(listOf(original), registry, map, EffectExportPolicy.current).attachments.single()
        val reloadedAttachment =
            EffectExportStage.build(listOf(reloaded), registry, map, EffectExportPolicy.current).attachments.single()

        assertEquals("persisted == exported windowStartUs", originalAttachment.windowStartUs, reloadedAttachment.windowStartUs)
        assertEquals("persisted == exported windowEndUs", originalAttachment.windowEndUs, reloadedAttachment.windowEndUs)

        val originalSamples = MatrixSampler.sample(
            originalAttachment.provider, originalAttachment.windowStartUs, originalAttachment.windowEndUs, ASPECT
        )
        val reloadedSamples = MatrixSampler.sample(
            reloadedAttachment.provider, reloadedAttachment.windowStartUs, reloadedAttachment.windowEndUs, ASPECT
        )
        assertEquals(originalSamples.size, reloadedSamples.size)
        originalSamples.zip(reloadedSamples).forEach { (o, r) ->
            assertEquals("persisted == exported matrix @${o.timeUs}us", 0f, MatrixSampler.matrixDelta(o.matrix, r.matrix), EPSILON)
            assertEquals("persisted == exported opacity @${o.timeUs}us", o.opacity, r.opacity, EPSILON)
        }

        val projectRow = app.database.projectDao().getProjectById(projectId)
        assertNotNull("project must survive simulated restart", projectRow)
        assertTrue(projectRow!!.id == projectId)
    }

    private fun easingCoverageParams(): Map<String, EffectParamValue> = mapOf(
        AnimationPropertyKeys.ROTATION to EffectParamValue.Keyframed(
            listOf(
                Keyframe(0L, 0f, KeyframeEasing.LINEAR),
                Keyframe(300_000L, 10f, KeyframeEasing.SMOOTHSTEP),
                Keyframe(600_000L, -10f, KeyframeEasing.BACK_OUT),
                Keyframe(800_000L, 0f, KeyframeEasing.BOUNCE_OUT)
            )
        ),
        AnimationPropertyKeys.SCALE_X to EffectParamValue.Keyframed(
            listOf(Keyframe(0L, 0.5f, KeyframeEasing.ELASTIC_OUT), Keyframe(800_000L, 1f, KeyframeEasing.CUBIC_OUT))
        ),
        AnimationPropertyKeys.OPACITY to EffectParamValue.Keyframed(
            listOf(Keyframe(0L, 0f, KeyframeEasing.CUBIC_IN_OUT), Keyframe(800_000L, 1f))
        )
    )

    private companion object {
        const val EPSILON = 1e-4f
        const val ASPECT = 9f / 16f
    }
}
