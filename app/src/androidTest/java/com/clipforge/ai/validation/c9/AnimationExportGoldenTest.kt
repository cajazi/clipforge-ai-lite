@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.validation.c9

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.RequiresGpuExport
import com.clipforge.ai.RequiresRealGpu
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ExportEffectRegistry
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.core.export.ExportManagerState
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.model.RenderJobStatus
import com.clipforge.ai.validation.c9.support.GoldenComparator
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
 * C9.0 validation contract B: export frame golden validation. Renders a baseline (no animation)
 * export and an animated export of the identical seed clip, then asserts:
 *  - export duration is invariant to the presence of an animation (within encoder jitter)
 *  - frames outside the animation window match the baseline golden (SSIM >= 0.98)
 *  - frames inside the animation window visibly differ from the baseline (the effect rendered)
 */
@RunWith(AndroidJUnit4::class)
@RequiresGpuExport
@RequiresRealGpu
class AnimationExportGoldenTest {

    @Before
    fun registerTransformAnimation() {
        ExportEffectRegistry.registry.registerTransformAnimationEffect()
    }

    @Test
    fun export_duration_invariant_and_animation_confined_to_window() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = SeedMediaFactory.seedPath(app)
        val clipDurationMs = minOf(SeedMediaFactory.readDurationMs(mediaPath), 2_500L)
        val suffix = System.currentTimeMillis()

        val baselineProjectId = "c9_golden_baseline_$suffix"
        seedSingleClipProject(app, baselineProjectId, mediaPath, clipDurationMs, effect = null)
        val baselineResult = runExport(app, baselineProjectId)
        assertEquals(RenderJobStatus.COMPLETED, baselineResult.status)
        val baselineOutput = File(baselineResult.outputUrl!!)
        val baselineDurationMs = readDurationMs(baselineOutput.absolutePath)

        val animatedProjectId = "c9_golden_animated_$suffix"
        val animatedEffect = EffectItem(
            id = "${animatedProjectId}_effect",
            projectId = animatedProjectId,
            effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
            scope = EffectScope.GLOBAL,
            startMs = 500L,
            endMs = 1_500L,
            zOrder = 0,
            params = transformParams(scale = 0.5f)
        )
        seedSingleClipProject(app, animatedProjectId, mediaPath, clipDurationMs, effect = animatedEffect)
        val animatedResult = runExport(app, animatedProjectId)
        assertEquals(RenderJobStatus.COMPLETED, animatedResult.status)
        val animatedOutput = File(animatedResult.outputUrl!!)
        val animatedDurationMs = readDurationMs(animatedOutput.absolutePath)

        val durationDeltaMs = abs(animatedDurationMs - baselineDurationMs)
        Log.d(
            TAG,
            "C9_EXPORT_GOLDEN baselineDurationMs=$baselineDurationMs animatedDurationMs=$animatedDurationMs deltaMs=$durationDeltaMs"
        )
        assertTrue(
            "export duration must remain invariant to animation presence (deltaMs=$durationDeltaMs)",
            durationDeltaMs <= DURATION_TOLERANCE_MS
        )

        val outsideBaseline = frameAt(baselineOutput.absolutePath, OUTSIDE_WINDOW_TIME_US)
        val outsideAnimated = frameAt(animatedOutput.absolutePath, OUTSIDE_WINDOW_TIME_US)
        val outsideSsim = GoldenComparator.compare(outsideBaseline, outsideAnimated)
        Log.d(TAG, "C9_EXPORT_GOLDEN outsideWindowSsim=${outsideSsim.score}")
        assertTrue(
            "frames outside the animation window must match the golden baseline (ssim=${outsideSsim.score})",
            outsideSsim.pass
        )

        val insideBaseline = frameAt(baselineOutput.absolutePath, INSIDE_WINDOW_TIME_US)
        val insideAnimated = frameAt(animatedOutput.absolutePath, INSIDE_WINDOW_TIME_US)
        val insideSsim = GoldenComparator.compare(insideBaseline, insideAnimated)
        Log.d(TAG, "C9_EXPORT_GOLDEN insideWindowSsim=${insideSsim.score}")
        assertTrue(
            "the animation window must visibly alter exported frames (ssim=${insideSsim.score} should be below threshold)",
            insideSsim.score < GoldenComparator.DEFAULT_THRESHOLD
        )
    }

    private fun transformParams(scale: Float): Map<String, EffectParamValue> = mapOf(
        AnimationPropertyKeys.POSITION_X to EffectParamValue.Constant(0f),
        AnimationPropertyKeys.POSITION_Y to EffectParamValue.Constant(0f),
        AnimationPropertyKeys.SCALE_X to EffectParamValue.Constant(scale),
        AnimationPropertyKeys.SCALE_Y to EffectParamValue.Constant(scale),
        AnimationPropertyKeys.ROTATION to EffectParamValue.Constant(0f),
        AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(1f),
        AnimationPropertyKeys.ANCHOR_X to EffectParamValue.Constant(0.5f),
        AnimationPropertyKeys.ANCHOR_Y to EffectParamValue.Constant(0.5f)
    )

    private suspend fun seedSingleClipProject(
        app: ClipForgeApp,
        projectId: String,
        mediaPath: String,
        clipDurationMs: Long,
        effect: EffectItem?
    ) {
        val now = System.currentTimeMillis()
        val db = app.database
        db.projectDao().upsertProject(
            ProjectEntity(
                id = projectId,
                title = "C9 $projectId",
                aspectRatio = "RATIO_9_16",
                exportQuality = "QUALITY_720P",
                planType = "FREE",
                thumbnailUri = null,
                createdAt = now,
                updatedAt = now
            )
        )
        val assetId = "${projectId}_asset"
        db.mediaAssetDao().upsertAll(
            listOf(
                MediaAssetEntity(
                    assetId, projectId, "VIDEO", mediaPath, null,
                    clipDurationMs, File(mediaPath).length(), "video/mp4", now
                )
            )
        )
        db.timelineDao().deleteAllForProject(projectId)
        db.effectItemDao().deleteForProject(projectId)
        db.timelineDao().upsertAll(
            listOf(
                TimelineItemEntity(
                    id = "${projectId}_clip",
                    projectId = projectId,
                    mediaAssetId = assetId,
                    trackIndex = 0,
                    orderIndex = 0,
                    startMs = 0L,
                    endMs = clipDurationMs,
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
        if (effect != null) {
            app.effectRepository.upsertEffect(effect)
        }
    }

    private fun runExport(app: ClipForgeApp, projectId: String): ExportManagerState {
        app.exportManager.startExport(projectId)
        val deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = app.exportManager.state.value
            if (state.projectId == projectId && state.status in TERMINAL_STATUSES) {
                if (state.status == RenderJobStatus.FAILED) {
                    Log.e(TAG, "C9_EXPORT_GOLDEN_FAILED projectId=$projectId error=${state.errorMessage}")
                }
                return state
            }
            Thread.sleep(POLL_MS)
        }
        error("Timed out waiting for export project=$projectId state=${app.exportManager.state.value}")
    }

    private fun frameAt(path: String, timeUs: Long): Bitmap {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: error("No frame at $timeUs us for $path")
        } finally {
            runCatching { mmr.release() }
        }
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
        const val TAG = "C9_ExportGolden"
        const val EXPORT_TIMEOUT_MS = 240_000L
        const val POLL_MS = 500L
        const val DURATION_TOLERANCE_MS = 250L
        const val OUTSIDE_WINDOW_TIME_US = 200_000L
        const val INSIDE_WINDOW_TIME_US = 1_000_000L
        val TERMINAL_STATUSES = setOf(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED, RenderJobStatus.CANCELLED)
    }
}
