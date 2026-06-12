package com.clipforge.ai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import com.clipforge.ai.domain.model.RenderJobStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-only A/B export validator for the CrossfadeExecutor registry-dispatch flip.
 *
 * Run once with USE_REGISTRY_DISPATCH=false, then temporarily flip it true and run the
 * same test again. Restore the switch to false before shipping.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TransitionExportAbValidationTest {

    private data class Case(val label: String, val type: String, val durationMs: Long = 500L)

    private val cases = listOf(
        Case("Dissolve", "DISSOLVE"),
        Case("Fade Black", "FADE_BLACK"),
        Case("Fade White", "FADE_WHITE"),
        Case("Flash", "FLASH"),
        Case("Flash Black", "FLASH_BLACK"),
        Case("Flash Warm", "FLASH_WARM"),
        Case("Flash Blue", "FLASH_BLUE"),
        Case("Film Burn", "FILM_BURN"),
        Case("Film Burn Warm", "FILM_BURN_WARM"),
        Case("Film Burn Heavy", "FILM_BURN_HEAVY"),
        Case("Slide Left", "SLIDE_LEFT"),
        Case("Slide Right", "SLIDE_RIGHT"),
        Case("Push Left", "PUSH_LEFT"),
        Case("Push Right", "PUSH_RIGHT"),
        Case("Push Up", "PUSH_UP"),
        Case("Push Down", "PUSH_DOWN"),
        Case("Zoom In", "ZOOM_IN"),
        Case("Zoom Out", "ZOOM_OUT"),
        Case("Spin", "SPIN"),
        Case("Rotate", "ROTATE"),
        Case("Camera Roll", "CAMERA_ROLL"),
        Case("Cube Left", "CUBE_LEFT"),
        Case("Cube Right", "CUBE_RIGHT"),
        Case("Cube Up", "CUBE_UP"),
        Case("Cube Down", "CUBE_DOWN"),
        Case("Flip Left", "FLIP_LEFT"),
        Case("Flip Right", "FLIP_RIGHT"),
        Case("Flip Up", "FLIP_UP"),
        Case("Flip Down", "FLIP_DOWN"),
        Case("Page Turn Left", "PAGE_TURN_LEFT"),
        Case("Page Turn Right", "PAGE_TURN_RIGHT"),
        Case("Page Turn Up", "PAGE_TURN_UP"),
        Case("Page Turn Down", "PAGE_TURN_DOWN"),
        Case("Whip Pan Left", "WHIP_PAN_LEFT"),
        Case("Whip Pan Right", "WHIP_PAN_RIGHT"),
        Case("Whip Pan Up", "WHIP_PAN_UP"),
        Case("Whip Pan Down", "WHIP_PAN_DOWN"),
        Case("Motion Blur Left", "MOTION_BLUR_LEFT"),
        Case("Motion Blur Right", "MOTION_BLUR_RIGHT"),
        Case("Motion Blur Up", "MOTION_BLUR_UP"),
        Case("Motion Blur Down", "MOTION_BLUR_DOWN"),
        Case("Wipe", "WIPE"),
        Case("Wipe Up", "WIPE_UP"),
        Case("Wipe Down", "WIPE_DOWN"),
        Case("Wipe Right", "WIPE_RIGHT"),
        Case("Glitch Pro", "GLITCH_PRO"),
        Case("Glitch Digital", "GLITCH_DIGITAL"),
        Case("Glitch RGB", "GLITCH_RGB"),
        Case("Glitch Scanline", "GLITCH_SCANLINE")
    )

    @Test
    fun export_matrix_completes_and_logs_metrics() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = seedPath(app)
        val sourceDurationMs = readDurationMs(mediaPath)
        assertTrue("seed media too short: ${sourceDurationMs}ms", sourceDurationMs >= 1500L)
        val clipDurationMs = minOf(sourceDurationMs, 2500L)

        cases.forEach { case ->
            val projectId = "ab_validation_${case.type.lowercase()}"
            seedProject(app, projectId, mediaPath, clipDurationMs, case.type, case.durationMs)
            val result = runExport(app, projectId)
            assertEquals("${case.label} export status", RenderJobStatus.COMPLETED, result.status)
            assertTrue("${case.label} gallery visible", result.isGalleryVisible)
            assertNotNull("${case.label} public uri", result.publicUri)
            assertNotNull("${case.label} output path", result.outputUrl)
            val output = File(result.outputUrl!!)
            assertTrue("${case.label} output exists", output.exists())
            assertTrue("${case.label} output nonzero", output.length() > 0L)
            val exportedDurationMs = readDurationMs(output.absolutePath)
            assertTrue("${case.label} exported duration sane: $exportedDurationMs", exportedDurationMs > 0L)
            Log.d(
                TAG,
                "AB_RESULT transition=${case.type} label=${case.label} " +
                    "durationMs=$exportedDurationMs bytes=${output.length()} " +
                    "gallery=${result.isGalleryVisible} uri=${result.publicUri} " +
                    "output=${output.absolutePath} statusMessage=${result.statusMessage}"
            )
        }
    }

    private fun seedPath(context: Context): String {
        val mediaDir = File(context.filesDir, "media")
        mediaDir.mkdirs()
        val seed = File(mediaDir, SEED_FILE_NAME)

        val synthetic = runCatching {
            val existingDurationMs = if (seed.isFile) readDurationMs(seed.absolutePath) else 0L
            if (existingDurationMs < MIN_SEED_DURATION_MS) {
                generateSyntheticSeedVideo(seed)
            }
            val durationMs = readDurationMs(seed.absolutePath)
            check(seed.isFile && durationMs >= MIN_SEED_DURATION_MS) {
                "synthetic seed invalid durationMs=$durationMs path=${seed.absolutePath}"
            }
            seed
        }

        val chosen = synthetic.getOrElse { error ->
            Log.w(TAG, "SEED_SYNTHETIC_UNAVAILABLE path=${seed.absolutePath} reason=${error.message}", error)
            mediaDir
                .listFiles { file -> file.isFile && file.extension.equals("mp4", ignoreCase = true) }
                .orEmpty()
                .filter { it.name != SEED_FILE_NAME }
                .sortedBy { it.name }
                .firstOrNull { readDurationMs(it.absolutePath) >= MIN_SEED_DURATION_MS }
                ?: throw AssertionError("synthetic seed unavailable and no imported fallback media found", error)
        }

        logSeedInfo(chosen, if (chosen.name == SEED_FILE_NAME) "synthetic" else "imported_fallback")
        return chosen.absolutePath
    }

    private fun logSeedInfo(seed: File, source: String) {
        Log.d(
            TAG,
            "SEED_INFO source=$source path=${seed.absolutePath} durationMs=${readDurationMs(seed.absolutePath)} " +
                "bytes=${seed.length()} hasAudio=${readHasAudio(seed.absolutePath)}"
        )
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
                buffer.put((32 + ((x + frameIndex * 5 + y / 3) % 192)).toByte())
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

    private suspend fun seedProject(
        app: ClipForgeApp,
        projectId: String,
        mediaPath: String,
        clipDurationMs: Long,
        transitionType: String,
        transitionDurationMs: Long
    ) {
        val now = System.currentTimeMillis()
        val db = app.database
        db.projectDao().upsertProject(
            ProjectEntity(
                id = projectId,
                title = "AB $transitionType",
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
                    transitionDurationMs = transitionDurationMs,
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
    }

    private fun runExport(app: ClipForgeApp, projectId: String): com.clipforge.ai.core.export.ExportManagerState {
        app.exportManager.startExport(projectId)
        val deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = app.exportManager.state.value
            if (state.projectId == projectId && state.status in TERMINAL_STATUSES) {
                if (state.status == RenderJobStatus.FAILED) {
                    Log.e(TAG, "AB_FAILED transitionProject=$projectId error=${state.errorMessage}")
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

    private fun readHasAudio(path: String): Boolean {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (_: Exception) {
            false
        } finally {
            runCatching { mmr.release() }
        }
    }

    private companion object {
        const val TAG = "AB_EXPORT_VALIDATION"
        const val EXPORT_TIMEOUT_MS = 240_000L
        const val POLL_MS = 500L
        const val MIN_SEED_DURATION_MS = 1500L
        const val SEED_FILE_NAME = "ab_validation_seed.mp4"
        const val SEED_WIDTH = 320
        const val SEED_HEIGHT = 240
        const val SEED_FPS = 15
        const val SEED_DURATION_SECONDS = 3
        const val SEED_FRAME_COUNT = SEED_FPS * SEED_DURATION_SECONDS
        const val SEED_BIT_RATE = 600_000
        const val CODEC_TIMEOUT_US = 10_000L
        val TERMINAL_STATUSES = setOf(
            RenderJobStatus.COMPLETED,
            RenderJobStatus.FAILED,
            RenderJobStatus.CANCELLED
        )
    }
}
