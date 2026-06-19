@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.validation.c9

import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectExportPolicy
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ExportEffectRegistry
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.core.animation.TransformMath
import com.clipforge.ai.core.overlay.TimePiece
import com.clipforge.ai.RequiresGpuExport
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.core.player.EffectPreviewPlan
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.model.RenderJobStatus
import com.clipforge.ai.validation.c9.support.PerfRecorder
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
 * C9.0 performance budget validation.
 *
 * "Preview" cost is measured as a CPU-side frame-resolution-cost proxy: for N concurrently active
 * clip animations, repeatedly resolving [EffectPreviewPlan] attachments through [TransformMath] at
 * a simulated 30fps cadence ([PerfRecorder.FrameTimeTracker]) - this isolates the cost the
 * animation system itself adds per frame (matrix/opacity resolution), since the GPU draw cost per
 * frame is the same shader path whether or not the resolved values are animated. No on-device
 * compositor FPS counter is wired in this codebase, so this proxy is the validation evidence
 * available without inventing new production instrumentation.
 *
 * "Export" duration regression and memory overhead are measured directly through the real,
 * unmodified [com.clipforge.ai.core.export.ExportManager] pipeline.
 */
@RunWith(AndroidJUnit4::class)
@RequiresGpuExport
class AnimationPerformanceTest {

    @Before
    fun registerTransformAnimation() {
        ExportEffectRegistry.registry.registerTransformAnimationEffect()
    }

    @Test
    fun preview_frame_resolution_cost_meets_budget_across_concurrency_levels() {
        val registry = EffectRegistry().apply { registerTransformAnimationEffect() }
        val results = CONCURRENCY_LEVELS.associateWith { count -> measureFrameResolutionCost(registry, count) }

        results.forEach { (count, stats) ->
            Log.d(
                TAG,
                "C9_PERF_REPORT metric=preview device=${Build.MANUFACTURER}/${Build.MODEL} " +
                    "activeAnimations=$count sampleCount=${stats.sampleCount} " +
                    "p50FrameTimeMs=${stats.p50FrameTimeMs} p95FrameTimeMs=${stats.p95FrameTimeMs} " +
                    "averageFps=${stats.averageFps} sustainedFps=${stats.sustainedFps}"
            )
        }

        val safeCap = CONCURRENCY_LEVELS.lastOrNull { count ->
            val stats = results.getValue(count)
            stats.p95FrameTimeMs <= P95_BUDGET_MS && stats.sustainedFps >= MIN_SUSTAINED_FPS
        } ?: 0
        Log.d(TAG, "C9_PERF_REPORT metric=safe_concurrency_cap activeAnimations=$safeCap")

        val baseline = results.getValue(CONCURRENCY_LEVELS.first())
        assertTrue(
            "P95 frame time at baseline concurrency must be <= ${P95_BUDGET_MS}ms: ${baseline.p95FrameTimeMs}",
            baseline.p95FrameTimeMs <= P95_BUDGET_MS
        )
        assertTrue(
            "sustained FPS at baseline concurrency must be >= $MIN_SUSTAINED_FPS: ${baseline.sustainedFps}",
            baseline.sustainedFps >= MIN_SUSTAINED_FPS
        )
        assertTrue("at least the lowest concurrency level must be within budget", safeCap >= CONCURRENCY_LEVELS.first())
    }

    @Test
    fun export_duration_regression_and_memory_overhead_within_budget() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = SeedMediaFactory.seedPath(app)
        val clipDurationMs = minOf(SeedMediaFactory.readDurationMs(mediaPath), 2_500L)
        val suffix = System.currentTimeMillis()

        val baselineProjectId = "c9_perf_baseline_$suffix"
        seedSingleClipProject(app, baselineProjectId, mediaPath, clipDurationMs, effect = null)
        System.gc()
        val memoryBeforeBaseline = PerfRecorder.currentMemory()
        val (baselineResult, baselineWallMs) = PerfRecorder.timeWallClockMs { runExport(app, baselineProjectId) }
        val memoryAfterBaseline = PerfRecorder.currentMemory()
        assertEquals("baseline export must complete (no OOM/watchdog stall)", RenderJobStatus.COMPLETED, baselineResult.status)

        val animatedProjectId = "c9_perf_animated_$suffix"
        val animatedEffect = EffectItem(
            id = "${animatedProjectId}_effect",
            projectId = animatedProjectId,
            effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
            scope = EffectScope.GLOBAL,
            startMs = 0L,
            endMs = clipDurationMs,
            zOrder = 0,
            params = animatedParams(clipDurationMs)
        )
        seedSingleClipProject(app, animatedProjectId, mediaPath, clipDurationMs, effect = animatedEffect)
        System.gc()
        val memoryBeforeAnimated = PerfRecorder.currentMemory()
        val (animatedResult, animatedWallMs) = PerfRecorder.timeWallClockMs { runExport(app, animatedProjectId) }
        val memoryAfterAnimated = PerfRecorder.currentMemory()
        assertEquals("animated export must complete (no OOM/watchdog stall)", RenderJobStatus.COMPLETED, animatedResult.status)

        val regressionRatio = if (baselineWallMs > 0) (animatedWallMs - baselineWallMs).toDouble() / baselineWallMs else 0.0
        val baselineOverheadKb = abs(memoryAfterBaseline.totalKb - memoryBeforeBaseline.totalKb)
        val animatedOverheadKb = abs(memoryAfterAnimated.totalKb - memoryBeforeAnimated.totalKb)
        val additionalOverheadKb = (animatedOverheadKb - baselineOverheadKb).coerceAtLeast(0L)

        Log.d(
            TAG,
            "C9_PERF_REPORT metric=export device=${Build.MANUFACTURER}/${Build.MODEL} " +
                "baselineWallMs=$baselineWallMs animatedWallMs=$animatedWallMs regressionRatio=$regressionRatio " +
                "baselineMemoryOverheadKb=$baselineOverheadKb animatedMemoryOverheadKb=$animatedOverheadKb " +
                "additionalAnimationOverheadKb=$additionalOverheadKb"
        )

        assertTrue(
            "export duration regression must be <= ${MAX_DURATION_REGRESSION_RATIO * 100}%: ${regressionRatio * 100}%",
            regressionRatio <= MAX_DURATION_REGRESSION_RATIO
        )
        assertTrue(
            "additional animation memory overhead must be <= ${MAX_ADDITIONAL_MEMORY_KB}KB: ${additionalOverheadKb}KB",
            additionalOverheadKb <= MAX_ADDITIONAL_MEMORY_KB
        )
    }

    private fun measureFrameResolutionCost(registry: EffectRegistry, activeAnimationCount: Int): PerfRecorder.FrameStats {
        val effects = (0 until activeAnimationCount).map { index ->
            EffectItem(
                id = "perf_effect_$index",
                projectId = PROJECT_ID,
                effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
                scope = EffectScope.GLOBAL,
                startMs = 0L,
                endMs = WINDOW_DURATION_MS,
                zOrder = index,
                params = animatedParams(WINDOW_DURATION_MS)
            )
        }
        val attachments = EffectPreviewPlan.build(effects, registry).attachments
        val tracker = PerfRecorder.FrameTimeTracker()
        val frameIntervalUs = 1_000_000L / SIMULATED_FPS

        repeat(SIMULATED_FRAME_COUNT) { frameIndex ->
            tracker.onFrame()
            val presentationTimeUs = frameIndex * frameIntervalUs
            attachments.forEach { attachment ->
                val clamped = presentationTimeUs.coerceIn(attachment.windowStartUs, attachment.windowEndUs)
                val values = TransformMath.resolveValues(clamped, attachment.windowStartUs, attachment.windowEndUs, attachment.provider)
                TransformMath.composeMatrix(values, ASPECT)
                TransformMath.opacityOf(values)
            }
        }
        return tracker.stats()
    }

    private fun animatedParams(durationMs: Long): Map<String, EffectParamValue> {
        val durationUs = durationMs * 1_000L
        return mapOf(
            AnimationPropertyKeys.SCALE_X to EffectParamValue.Keyframed(listOf(Keyframe(0L, 1f), Keyframe(durationUs, 1.3f))),
            AnimationPropertyKeys.SCALE_Y to EffectParamValue.Keyframed(listOf(Keyframe(0L, 1f), Keyframe(durationUs, 1.3f))),
            AnimationPropertyKeys.ROTATION to EffectParamValue.Keyframed(listOf(Keyframe(0L, 0f), Keyframe(durationUs, 8f))),
            AnimationPropertyKeys.OPACITY to EffectParamValue.Keyframed(listOf(Keyframe(0L, 1f), Keyframe(durationUs, 0.7f))),
            AnimationPropertyKeys.POSITION_X to EffectParamValue.Constant(0f),
            AnimationPropertyKeys.POSITION_Y to EffectParamValue.Constant(0f),
            AnimationPropertyKeys.ANCHOR_X to EffectParamValue.Constant(0.5f),
            AnimationPropertyKeys.ANCHOR_Y to EffectParamValue.Constant(0.5f)
        )
    }

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
                title = "C9 Perf $projectId",
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

    private fun runExport(app: ClipForgeApp, projectId: String): com.clipforge.ai.core.export.ExportManagerState {
        app.exportManager.startExport(projectId)
        val deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = app.exportManager.state.value
            if (state.projectId == projectId && state.status in TERMINAL_STATUSES) {
                if (state.status == RenderJobStatus.FAILED) {
                    Log.e(TAG, "C9_PERF_EXPORT_FAILED projectId=$projectId error=${state.errorMessage}")
                }
                return state
            }
            Thread.sleep(POLL_MS)
        }
        error("Timed out waiting for export project=$projectId state=${app.exportManager.state.value}")
    }

    private companion object {
        const val TAG = "C9_Performance"
        const val PROJECT_ID = "c9-perf-preview"
        const val EXPORT_TIMEOUT_MS = 240_000L
        const val POLL_MS = 500L
        const val ASPECT = 9f / 16f
        const val WINDOW_DURATION_MS = 1_000L
        const val SIMULATED_FPS = 30L
        const val SIMULATED_FRAME_COUNT = 150
        const val P95_BUDGET_MS = 33.0
        const val MIN_SUSTAINED_FPS = 24.0
        const val MAX_DURATION_REGRESSION_RATIO = 0.15
        const val MAX_ADDITIONAL_MEMORY_KB = 25_000L
        val CONCURRENCY_LEVELS = listOf(1, 4, 8, 12, 16)
        val TERMINAL_STATUSES = setOf(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED, RenderJobStatus.CANCELLED)
    }
}
