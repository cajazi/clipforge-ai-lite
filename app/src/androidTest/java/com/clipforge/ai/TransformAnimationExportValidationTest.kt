@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ExportEffectRegistry
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.model.RenderJobStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@RequiresGpuExport
@RequiresRealGpu
class TransformAnimationExportValidationTest {

    @Before
    fun registerTransformAnimation() {
        ExportEffectRegistry.registry.registerTransformAnimationEffect()
    }

    @Test
    fun export_applies_transform_animation_window_and_leaves_identity_outside() {
        runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = seedPath(app)
        val clipDurationMs = minOf(readDurationMs(mediaPath), 2_500L)
        val projectId = "c8_transform_export_${System.currentTimeMillis()}"

        seedSingleClipProject(app, projectId, mediaPath, clipDurationMs)
        app.effectRepository.upsertEffect(
            EffectItem(
                id = "${projectId}_effect",
                projectId = projectId,
                effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
                scope = EffectScope.GLOBAL,
                startMs = 500L,
                endMs = 1_500L,
                zOrder = 0,
                params = transformParams(scale = 0.5f)
            )
        )

        val result = runExport(app, projectId)
        assertEquals(RenderJobStatus.COMPLETED, result.status)
        val output = File(result.outputUrl!!)
        assertTrue("transform output exists", output.exists())
        assertTrue("transform output nonzero", output.length() > 0L)

        val durationMs = readDurationMs(output.absolutePath)
        assertTrue("duration unchanged enough: $durationMs", durationMs in (clipDurationMs - 250L)..(clipDurationMs + 250L))

        val before = edgeBrightness(frameAt(output.absolutePath, 250_000L))
        val inside = edgeBrightness(frameAt(output.absolutePath, 1_000_000L))
        val after = edgeBrightness(frameAt(output.absolutePath, 2_000_000L))
        Log.d(
            TAG,
            "C8_TRANSFORM_EXPORT output=${output.absolutePath} bytes=${output.length()} " +
                "durationMs=$durationMs edgeBefore=$before edgeInside=$inside edgeAfter=$after"
        )
            assertTrue("scale=0.5 should reveal dark edges inside effect window", inside < before - 10f)
            assertTrue("identity should resume after effect window", after > inside + 10f)
        }
    }

    @Test
    fun export_without_transform_animation_still_completes() {
        runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = seedPath(app)
        val clipDurationMs = minOf(readDurationMs(mediaPath), 2_500L)
        val projectId = "c8_no_transform_export_${System.currentTimeMillis()}"

        seedSingleClipProject(app, projectId, mediaPath, clipDurationMs)

        val result = runExport(app, projectId)
        assertEquals(RenderJobStatus.COMPLETED, result.status)
        val output = File(result.outputUrl!!)
        assertTrue("regression output exists", output.exists())
        assertTrue("regression output nonzero", output.length() > 0L)
        val durationMs = readDurationMs(output.absolutePath)
        assertTrue("regression duration sane: $durationMs", durationMs > 0L)
            Log.d(TAG, "C8_NO_TRANSFORM_EXPORT output=${output.absolutePath} bytes=${output.length()} durationMs=$durationMs")
        }
    }

    private suspend fun seedSingleClipProject(
        app: ClipForgeApp,
        projectId: String,
        mediaPath: String,
        clipDurationMs: Long
    ) {
        val now = System.currentTimeMillis()
        val db = app.database
        db.projectDao().upsertProject(
            ProjectEntity(
                id = projectId,
                title = "C8 $projectId",
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
                    assetId,
                    projectId,
                    "VIDEO",
                    mediaPath,
                    null,
                    clipDurationMs,
                    File(mediaPath).length(),
                    "video/mp4",
                    now
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

    private fun seedPath(context: Context): String {
        val mediaDir = File(context.filesDir, "media")
        mediaDir.mkdirs()
        val seed = File(mediaDir, SEED_FILE_NAME)
        val existingDurationMs = if (seed.isFile) readDurationMs(seed.absolutePath) else 0L
        if (existingDurationMs < MIN_SEED_DURATION_MS) {
            generateSyntheticSeedVideo(seed)
        }
        val durationMs = readDurationMs(seed.absolutePath)
        check(seed.isFile && durationMs >= MIN_SEED_DURATION_MS) {
            "synthetic seed invalid durationMs=$durationMs path=${seed.absolutePath}"
        }
        Log.d(TAG, "SEED_INFO path=${seed.absolutePath} durationMs=$durationMs bytes=${seed.length()}")
        return seed.absolutePath
    }

    private fun generateSyntheticSeedVideo(output: File) {
        if (output.exists()) output.delete()
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, SEED_WIDTH, SEED_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, SEED_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, SEED_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var muxerStarted = false
        var trackIndex = -1
        var frameIndex = 0
        var inputDone = false
        var outputDone = false

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val ptsUs = frameIndex * 1_000_000L / SEED_FPS
                        val input = codec.getInputBuffer(inputIndex) ?: error("No encoder input buffer")
                        if (frameIndex < SEED_FRAME_COUNT) {
                            val size = fillSyntheticYuvFrame(input, frameIndex)
                            codec.queueInputBuffer(inputIndex, 0, size, ptsUs, 0)
                            frameIndex++
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (outputIndex >= 0) {
                        val encoded = codec.getOutputBuffer(outputIndex) ?: error("No encoder output buffer")
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            info.size = 0
                        }
                        if (info.size > 0) {
                            check(muxerStarted) { "Muxer not started" }
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encoded, info)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun fillSyntheticYuvFrame(buffer: java.nio.ByteBuffer, frameIndex: Int): Int {
        buffer.clear()
        val ySize = SEED_WIDTH * SEED_HEIGHT
        val frameSize = ySize * 3 / 2
        for (y in 0 until SEED_HEIGHT) {
            for (x in 0 until SEED_WIDTH) {
                buffer.put((96 + ((x + frameIndex * 5 + y / 3) % 128)).toByte())
            }
        }
        val u = (96 + (frameIndex * 3 % 48)).toByte()
        val v = (160 - (frameIndex * 2 % 48)).toByte()
        repeat(ySize / 4) {
            buffer.put(u)
            buffer.put(v)
        }
        return frameSize
    }

    private fun runExport(app: ClipForgeApp, projectId: String): com.clipforge.ai.core.export.ExportManagerState {
        app.exportManager.startExport(projectId)
        val deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = app.exportManager.state.value
            if (state.projectId == projectId && state.status in TERMINAL_STATUSES) {
                if (state.status == RenderJobStatus.FAILED) {
                    Log.e(TAG, "C8_EXPORT_FAILED projectId=$projectId error=${state.errorMessage}")
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
            assertNotNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
            mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: error("No frame at $timeUs us for $path")
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun edgeBrightness(bitmap: Bitmap): Float {
        var count = 0
        var total = 0f
        var y = bitmap.height / 10
        while (y < bitmap.height * 9 / 10) {
            var x = bitmap.width / 20
            while (x < bitmap.width / 5) {
                val pixel = bitmap.getPixel(x, y)
                total += (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f
                count++
                x += 16
            }
            y += 16
        }
        return total / count
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
        const val TAG = "C8_TRANSFORM_EXPORT"
        const val EXPORT_TIMEOUT_MS = 240_000L
        const val POLL_MS = 500L
        const val MIN_SEED_DURATION_MS = 2_500L
        const val SEED_FILE_NAME = "c8_transform_seed.mp4"
        const val SEED_WIDTH = 320
        const val SEED_HEIGHT = 240
        const val SEED_FPS = 15
        const val SEED_DURATION_SECONDS = 3
        const val SEED_FRAME_COUNT = SEED_FPS * SEED_DURATION_SECONDS
        const val SEED_BIT_RATE = 600_000
        const val CODEC_TIMEOUT_US = 10_000L
        val TERMINAL_STATUSES = setOf(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED, RenderJobStatus.CANCELLED)
    }
}
