@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.validation.c9

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.effect.GlEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.RequiresGpuExport
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ExportEffectRegistry
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.core.gl.AnimationEffectFactory
import com.clipforge.ai.core.player.EffectPreviewPlan
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * C9.0 validation contract C: preview/export sampled pixel parity (SSIM >= 0.98).
 *
 * "Preview" pixels are rendered by feeding the exact [EffectPreviewPlan]-resolved provider/window
 * (already proven byte-equal to export's, contract A) through the same production
 * [AnimationEffectFactory] GL effect class, via a direct single-clip [Transformer] render that
 * bypasses [com.clipforge.ai.core.gl.CrossfadeExecutor] entirely. "Export" pixels come from the
 * real, full [com.clipforge.ai.core.export.ExportManager] pipeline. Comparing the two exercises an
 * independent rendering path at the GPU/shader level, not just the matrix math.
 */
@RunWith(AndroidJUnit4::class)
@RequiresGpuExport
class AnimationPreviewExportPixelTest {

    @Before
    fun registerTransformAnimation() {
        ExportEffectRegistry.registry.registerTransformAnimationEffect()
    }

    @Test
    fun preview_and_export_pixels_match_within_ssim_threshold() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = SeedMediaFactory.seedPath(app)
        val clipDurationMs = minOf(SeedMediaFactory.readDurationMs(mediaPath), 2_500L)
        val projectId = "c9_pixel_${System.currentTimeMillis()}"

        val windowStartMs = 300L
        val windowEndMs = clipDurationMs - 300L
        check(windowEndMs > windowStartMs + 200L) { "seed clip too short for pixel parity window: $clipDurationMs" }

        val effect = EffectItem(
            id = "${projectId}_effect",
            projectId = projectId,
            effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
            scope = EffectScope.GLOBAL,
            startMs = windowStartMs,
            endMs = windowEndMs,
            zOrder = 0,
            params = animatedParams(windowEndMs - windowStartMs)
        )

        seedSingleClipProject(app, projectId, mediaPath, clipDurationMs, effect)

        val previewAttachment = EffectPreviewPlan.build(listOf(effect), ExportEffectRegistry.registry).attachments.single()
        val previewGlEffect = AnimationEffectFactory.create(
            previewAttachment.windowStartUs, previewAttachment.windowEndUs, previewAttachment.provider
        )

        val previewOutput = File(app.filesDir, "c9_preview_equiv_$projectId.mp4")
        renderSingleEffectExport(app, mediaPath, clipDurationMs, previewGlEffect, previewOutput)

        val exportResult = runExport(app, projectId)
        assertEquals(RenderJobStatus.COMPLETED, exportResult.status)
        val exportOutput = File(exportResult.outputUrl!!)

        val sampleTimesUs = listOf(
            (windowStartMs - 200L).coerceAtLeast(0L) * 1_000L,
            (windowStartMs + 150L) * 1_000L,
            ((windowStartMs + windowEndMs) / 2L) * 1_000L,
            (windowEndMs - 150L) * 1_000L
        )

        sampleTimesUs.forEach { timeUs ->
            val previewFrame = frameAt(previewOutput.absolutePath, timeUs)
            val exportFrame = frameAt(exportOutput.absolutePath, timeUs)
            val ssim = GoldenComparator.compare(previewFrame, exportFrame)
            Log.d(TAG, "C9_PIXEL_PARITY t=${timeUs}us ssim=${ssim.score}")
            assertTrue("preview/export pixel parity at ${timeUs}us (ssim=${ssim.score})", ssim.pass)
        }
    }

    private fun animatedParams(durationMs: Long): Map<String, EffectParamValue> {
        val durationUs = durationMs * 1_000L
        return mapOf(
            AnimationPropertyKeys.SCALE_X to EffectParamValue.Keyframed(
                listOf(Keyframe(0L, 1f), Keyframe(durationUs, 1.4f))
            ),
            AnimationPropertyKeys.SCALE_Y to EffectParamValue.Keyframed(
                listOf(Keyframe(0L, 1f), Keyframe(durationUs, 1.4f))
            ),
            AnimationPropertyKeys.ROTATION to EffectParamValue.Keyframed(
                listOf(Keyframe(0L, 0f), Keyframe(durationUs, 12f))
            ),
            AnimationPropertyKeys.OPACITY to EffectParamValue.Keyframed(
                listOf(Keyframe(0L, 1f), Keyframe(durationUs, 0.6f))
            ),
            AnimationPropertyKeys.POSITION_X to EffectParamValue.Constant(0f),
            AnimationPropertyKeys.POSITION_Y to EffectParamValue.Constant(0f),
            AnimationPropertyKeys.ANCHOR_X to EffectParamValue.Constant(0.5f),
            AnimationPropertyKeys.ANCHOR_Y to EffectParamValue.Constant(0.5f)
        )
    }

    private fun renderSingleEffectExport(
        context: Context,
        mediaPath: String,
        clipDurationMs: Long,
        glEffect: GlEffect,
        outputFile: File
    ) {
        if (outputFile.exists()) outputFile.delete()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(mediaPath)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0L)
                    .setEndPositionMs(clipDurationMs)
                    .build()
            )
            .build()
        // Matches outputDimensions(RATIO_9_16, QUALITY_720P) computed independently in
        // CrossfadeExecutor (forbidden to modify) so both renders share output dimensions.
        val presentationEffect = Presentation.createForWidthAndHeight(
            PREVIEW_OUTPUT_WIDTH, PREVIEW_OUTPUT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT
        )
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), listOf(presentationEffect, glEffect)))
            .build()

        val latch = CountDownLatch(1)
        var failure: Exception? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        latch.countDown()
                    }

                    override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                        failure = exception
                        latch.countDown()
                    }
                })
                .build()
            transformer.start(editedItem, outputFile.absolutePath)
        }
        check(latch.await(RENDER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "preview-equivalent render timed out" }
        failure?.let { throw it }
        check(outputFile.exists() && outputFile.length() > 0L) { "preview-equivalent render produced no output" }
    }

    private suspend fun seedSingleClipProject(
        app: ClipForgeApp,
        projectId: String,
        mediaPath: String,
        clipDurationMs: Long,
        effect: EffectItem
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
        app.effectRepository.upsertEffect(effect)
    }

    private fun runExport(app: ClipForgeApp, projectId: String): com.clipforge.ai.core.export.ExportManagerState {
        app.exportManager.startExport(projectId)
        val deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = app.exportManager.state.value
            if (state.projectId == projectId && state.status in TERMINAL_STATUSES) {
                if (state.status == RenderJobStatus.FAILED) {
                    Log.e(TAG, "C9_PIXEL_EXPORT_FAILED projectId=$projectId error=${state.errorMessage}")
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

    private companion object {
        const val TAG = "C9_PixelParity"
        const val EXPORT_TIMEOUT_MS = 240_000L
        const val RENDER_TIMEOUT_MS = 60_000L
        const val POLL_MS = 500L
        const val PREVIEW_OUTPUT_WIDTH = 720
        const val PREVIEW_OUTPUT_HEIGHT = 1280
        val TERMINAL_STATUSES = setOf(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED, RenderJobStatus.CANCELLED)
    }
}
