@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.validation.c9

import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.animation.AnimationWindowResolver
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ExportEffectRegistry
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.RequiresGpuExport
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.model.RenderJobStatus
import com.clipforge.ai.validation.c9.support.SeedMediaFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * C9.0: clip animations adjacent to a transition (the OUT role on the outgoing clip and the IN
 * role on the incoming clip of the same transition pair) must never overlap the transition's own
 * window and must never change export duration or break the transition itself. Covers all 10
 * named transitions from the C9.0 spec; [com.clipforge.ai.TransitionExportAbValidationTest]
 * (run separately as a validation gate) already proves the transitions themselves render without
 * animations present.
 */
@RunWith(AndroidJUnit4::class)
@RequiresGpuExport
class AnimationTransitionAdjacencyTest {

    @Before
    fun registerTransformAnimation() {
        ExportEffectRegistry.registry.registerTransformAnimationEffect()
    }

    @Test
    fun animation_windows_never_overlap_adjacent_transition_window() {
        TRANSITIONS.forEach { transitionType ->
            ROLES.forEach { role ->
                val clipStartMs = 0L
                val clipEndMs = CLIP_DURATION_MS
                val window = AnimationWindowResolver.resolve(
                    clipStartMs = clipStartMs,
                    clipEndMs = clipEndMs,
                    requestedDurationMs = ANIMATION_DURATION_MS,
                    role = role,
                    incomingTransitionDurationMs = TRANSITION_DURATION_MS,
                    outgoingTransitionDurationMs = TRANSITION_DURATION_MS
                )
                assertTrue(
                    "$transitionType/$role: window must resolve for a clip with margin for both transitions",
                    window != null
                )
                requireNotNull(window)
                assertTrue(
                    "$transitionType/$role: window start ($window.startMs) overlaps incoming transition (ends ${clipStartMs + TRANSITION_DURATION_MS})",
                    window.startMs >= clipStartMs + TRANSITION_DURATION_MS
                )
                assertTrue(
                    "$transitionType/$role: window end (${window.endMs}) overlaps outgoing transition (starts ${clipEndMs - TRANSITION_DURATION_MS})",
                    window.endMs <= clipEndMs - TRANSITION_DURATION_MS
                )
            }
        }
    }

    @Test
    fun export_duration_and_transition_unaffected_by_adjacent_animations() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = SeedMediaFactory.seedPath(app)
        val clipDurationMs = minOf(SeedMediaFactory.readDurationMs(mediaPath), 2_500L)

        TRANSITIONS.forEach { transitionType ->
            val suffix = "${transitionType.lowercase()}_${System.currentTimeMillis()}"

            val baselineProjectId = "c9_adjacency_baseline_$suffix"
            seedTransitionProject(app, baselineProjectId, mediaPath, clipDurationMs, transitionType, effects = emptyList())
            val baselineResult = runExport(app, baselineProjectId)
            assertEquals("$transitionType baseline export status", RenderJobStatus.COMPLETED, baselineResult.status)
            val baselineDurationMs = readDurationMs(File(baselineResult.outputUrl!!).absolutePath)

            val animatedProjectId = "c9_adjacency_animated_$suffix"
            val outEffect = adjacentEffect(animatedProjectId, "${animatedProjectId}_clip_a", AnimationRole.OUT, clipDurationMs)
            val inEffect = adjacentEffect(animatedProjectId, "${animatedProjectId}_clip_b", AnimationRole.IN, clipDurationMs)
            seedTransitionProject(
                app, animatedProjectId, mediaPath, clipDurationMs, transitionType, effects = listOf(outEffect, inEffect)
            )
            val animatedResult = runExport(app, animatedProjectId)
            assertEquals("$transitionType animated export status", RenderJobStatus.COMPLETED, animatedResult.status)
            val animatedDurationMs = readDurationMs(File(animatedResult.outputUrl!!).absolutePath)

            val deltaMs = abs(animatedDurationMs - baselineDurationMs)
            Log.d(
                TAG,
                "C9_TRANSITION_ADJACENCY transition=$transitionType baselineMs=$baselineDurationMs " +
                    "animatedMs=$animatedDurationMs deltaMs=$deltaMs"
            )
            assertTrue(
                "$transitionType: export duration must be unaffected by adjacent animations (deltaMs=$deltaMs)",
                deltaMs <= DURATION_TOLERANCE_MS
            )
        }
    }

    private fun adjacentEffect(projectId: String, clipId: String, role: AnimationRole, clipDurationMs: Long): EffectItem {
        val window = requireNotNull(
            AnimationWindowResolver.resolve(
                clipStartMs = 0L,
                clipEndMs = clipDurationMs,
                requestedDurationMs = ANIMATION_DURATION_MS,
                role = role,
                incomingTransitionDurationMs = if (role == AnimationRole.IN) TRANSITION_DURATION_MS else 0L,
                outgoingTransitionDurationMs = if (role == AnimationRole.OUT) TRANSITION_DURATION_MS else 0L
            )
        ) { "adjacency window must resolve for $clipId/$role" }
        return EffectItem(
            id = AnimationEffectId.of(clipId, role),
            projectId = projectId,
            effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
            scope = EffectScope.CLIP,
            startMs = window.startMs,
            endMs = window.endMs,
            zOrder = 0,
            params = mapOf(
                AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(if (role == AnimationRole.IN) 0.4f else 0.6f),
                AnimationPropertyKeys.SCALE_X to EffectParamValue.Constant(0.85f),
                AnimationPropertyKeys.SCALE_Y to EffectParamValue.Constant(0.85f)
            )
        )
    }

    private suspend fun seedTransitionProject(
        app: ClipForgeApp,
        projectId: String,
        mediaPath: String,
        clipDurationMs: Long,
        transitionType: String,
        effects: List<EffectItem>
    ) {
        val now = System.currentTimeMillis()
        val db = app.database
        db.projectDao().upsertProject(
            ProjectEntity(
                id = projectId,
                title = "C9 Adjacency $transitionType",
                aspectRatio = "RATIO_9_16",
                exportQuality = "QUALITY_720P",
                planType = "FREE",
                thumbnailUri = null,
                createdAt = now,
                updatedAt = now
            )
        )
        val assetA = "${projectId}_asset_a"
        val assetB = "${projectId}_asset_b"
        db.mediaAssetDao().upsertAll(
            listOf(
                MediaAssetEntity(assetA, projectId, "VIDEO", mediaPath, null, clipDurationMs, File(mediaPath).length(), "video/mp4", now),
                MediaAssetEntity(assetB, projectId, "VIDEO", mediaPath, null, clipDurationMs, File(mediaPath).length(), "video/mp4", now + 1L)
            )
        )
        db.timelineDao().deleteAllForProject(projectId)
        db.timelineDao().upsertAll(
            listOf(
                TimelineItemEntity(
                    id = "${projectId}_clip_a",
                    projectId = projectId,
                    mediaAssetId = assetA,
                    trackIndex = 0,
                    orderIndex = 0,
                    startMs = 0L,
                    endMs = clipDurationMs,
                    trimStartMs = 0L,
                    trimEndMs = clipDurationMs,
                    fitMode = "FIT",
                    transitionType = transitionType,
                    transitionDurationMs = TRANSITION_DURATION_MS,
                    volume = 1f,
                    opacity = 1f
                ),
                TimelineItemEntity(
                    id = "${projectId}_clip_b",
                    projectId = projectId,
                    mediaAssetId = assetB,
                    trackIndex = 0,
                    orderIndex = 1,
                    startMs = clipDurationMs,
                    endMs = clipDurationMs * 2L,
                    trimStartMs = 0L,
                    trimEndMs = clipDurationMs,
                    fitMode = "FIT",
                    transitionType = null,
                    transitionDurationMs = null,
                    volume = 1f,
                    opacity = 1f
                )
            )
        )
        db.effectItemDao().deleteForProject(projectId)
        effects.forEach { app.effectRepository.upsertEffect(it) }
    }

    private fun runExport(app: ClipForgeApp, projectId: String): com.clipforge.ai.core.export.ExportManagerState {
        app.exportManager.startExport(projectId)
        val deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = app.exportManager.state.value
            if (state.projectId == projectId && state.status in TERMINAL_STATUSES) {
                if (state.status == RenderJobStatus.FAILED) {
                    Log.e(TAG, "C9_TRANSITION_ADJACENCY_FAILED projectId=$projectId error=${state.errorMessage}")
                }
                return state
            }
            Thread.sleep(POLL_MS)
        }
        error("Timed out waiting for export project=$projectId state=${app.exportManager.state.value}")
    }

    private fun readDurationMs(path: String): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { mmr.release() }
        }
    }

    private companion object {
        const val TAG = "C9_TransitionAdjacency"
        const val EXPORT_TIMEOUT_MS = 240_000L
        const val POLL_MS = 500L
        const val DURATION_TOLERANCE_MS = 250L
        const val CLIP_DURATION_MS = 2_000L
        const val ANIMATION_DURATION_MS = 300L
        const val TRANSITION_DURATION_MS = 500L
        val ROLES = listOf(AnimationRole.IN, AnimationRole.OUT, AnimationRole.COMBO)
        val TRANSITIONS = listOf(
            "DISSOLVE", "FADE_BLACK", "FADE_WHITE",
            "PUSH_LEFT", "PUSH_RIGHT", "PUSH_UP", "PUSH_DOWN",
            "ZOOM_IN", "BOUNCE", "FILM_BURN"
        )
        val TERMINAL_STATUSES = setOf(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED, RenderJobStatus.CANCELLED)
    }
}
