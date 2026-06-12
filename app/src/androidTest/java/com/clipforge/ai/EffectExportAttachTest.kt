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
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.core.effects.EffectCategory
import com.clipforge.ai.core.effects.EffectDescriptor
import com.clipforge.ai.core.effects.EffectFactory
import com.clipforge.ai.core.effects.EffectRegistration
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ExportEffectRegistry
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.ParamProvider
import com.clipforge.ai.core.effects.ParamSpec
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.model.RenderJobStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EffectExportAttachTest {

    @Before
    fun registerTestEffect() {
        ExportEffectRegistry.registry.clear()
        ExportEffectRegistry.registry.register(
            EffectRegistration(
                descriptor = EffectDescriptor(
                    id = TEST_TINT_ID,
                    displayName = "Test Tint",
                    category = EffectCategory.TRENDY,
                    paramSpecs = listOf(ParamSpec("intensity", "Intensity", 0f, 1f, 0f))
                ),
                factory = EffectFactory { windowStartUs, windowEndUs, params ->
                    WindowedRedTintEffect(windowStartUs, windowEndUs, params)
                }
            )
        )
    }

    @After
    fun clearTestEffect() {
        ExportEffectRegistry.registry.clear()
    }

    @Test
    fun export_attaches_constant_tint_across_mapped_transition_boundary() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = seedPath(app)
        val clipDurationMs = minOf(readDurationMs(mediaPath), 2_500L)
        val projectId = "c4_constant_tint"
        seedProject(app, projectId, mediaPath, clipDurationMs)
        app.effectRepository.upsertEffect(
            EffectItem(
                id = "${projectId}_effect",
                projectId = projectId,
                effectId = TEST_TINT_ID,
                scope = EffectScope.GLOBAL,
                startMs = 2_550L,
                endMs = 2_800L,
                zOrder = 0,
                params = mapOf("intensity" to EffectParamValue.Constant(0.85f))
            )
        )

        val result = runExport(app, projectId)
        assertEquals(RenderJobStatus.COMPLETED, result.status)
        val output = File(result.outputUrl!!)
        assertTrue(output.length() > 0L)

        val before = redDominance(frameAt(output.absolutePath, 2_000_000L))
        val inside = redDominance(frameAt(output.absolutePath, 2_330_000L))
        val after = redDominance(frameAt(output.absolutePath, 3_000_000L))
        Log.d(TAG, "C4_FRAME_PROBE constant before=$before inside=$inside after=$after output=${output.absolutePath}")
        assertTrue("constant tint should be visible inside mapped window", inside > before + 35f)
        assertTrue("constant tint should end after mapped window", inside > after + 35f)
    }

    @Test
    fun export_attaches_keyframed_tint_with_shifted_times() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = seedPath(app)
        val clipDurationMs = minOf(readDurationMs(mediaPath), 2_500L)
        val projectId = "c4_keyframed_tint"
        seedProject(app, projectId, mediaPath, clipDurationMs)
        app.effectRepository.upsertEffect(
            EffectItem(
                id = "${projectId}_effect",
                projectId = projectId,
                effectId = TEST_TINT_ID,
                scope = EffectScope.GLOBAL,
                startMs = 2_550L,
                endMs = 2_800L,
                zOrder = 0,
                params = mapOf(
                    "intensity" to EffectParamValue.Keyframed(
                        listOf(Keyframe(0L, 0.05f), Keyframe(125_000L, 0.9f))
                    )
                )
            )
        )

        val result = runExport(app, projectId)
        assertEquals(RenderJobStatus.COMPLETED, result.status)
        val output = File(result.outputUrl!!)
        assertTrue(output.length() > 0L)

        val early = redDominance(frameAt(output.absolutePath, 2_285_000L))
        val late = redDominance(frameAt(output.absolutePath, 2_390_000L))
        val outside = redDominance(frameAt(output.absolutePath, 3_000_000L))
        Log.d(TAG, "C4_FRAME_PROBE keyframed early=$early late=$late outside=$outside output=${output.absolutePath}")
        assertTrue("keyframed tint should strengthen later in mapped window", late > early + 25f)
        assertTrue("keyframed tint should not leak after mapped window", late > outside + 35f)
    }

    private suspend fun seedProject(
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
                title = "C4 $projectId",
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
        db.effectItemDao().deleteForProject(projectId)
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
                    transitionType = "DISSOLVE",
                    transitionDurationMs = 500L,
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
        Log.d(TAG, "SEED_INFO source=synthetic path=${seed.absolutePath} durationMs=$durationMs bytes=${seed.length()} hasAudio=${readHasAudio(seed.absolutePath)}")
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

    private fun runExport(app: ClipForgeApp, projectId: String): com.clipforge.ai.core.export.ExportManagerState {
        app.exportManager.startExport(projectId)
        val deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = app.exportManager.state.value
            if (state.projectId == projectId && state.status in TERMINAL_STATUSES) {
                if (state.status == RenderJobStatus.FAILED) {
                    Log.e(TAG, "C4_EXPORT_FAILED projectId=$projectId error=${state.errorMessage}")
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

    private fun redDominance(bitmap: Bitmap): Float {
        val left = bitmap.width / 3
        val top = bitmap.height / 3
        val right = bitmap.width * 2 / 3
        val bottom = bitmap.height * 2 / 3
        var count = 0
        var total = 0f
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                total += Color.red(pixel) - ((Color.green(pixel) + Color.blue(pixel)) / 2f)
                count++
                x += 12
            }
            y += 12
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

    private class WindowedRedTintEffect(
        private val windowStartUs: Long,
        private val windowEndUs: Long,
        private val params: ParamProvider
    ) : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
            WindowedRedTintProgram(useHdr, windowStartUs, windowEndUs, params)
    }

    private class WindowedRedTintProgram(
        useHdr: Boolean,
        private val windowStartUs: Long,
        private val windowEndUs: Long,
        private val params: ParamProvider
    ) : BaseGlShaderProgram(useHdr, 1) {

        private val program: GlProgram

        init {
            val vertexShader = """
                attribute vec4 aFramePosition;
                attribute vec4 aTexSamplingCoord;
                varying vec2 vTexSamplingCoord;
                void main() {
                    gl_Position = aFramePosition;
                    vTexSamplingCoord = aTexSamplingCoord.xy;
                }
            """.trimIndent()

            val fragmentShader = """
                precision mediump float;
                uniform sampler2D uTexSampler;
                uniform float uIntensity;
                varying vec2 vTexSamplingCoord;
                void main() {
                    vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                    vec3 tinted = mix(src.rgb, vec3(1.0, 0.0, 0.0), uIntensity);
                    gl_FragColor = vec4(tinted, src.a);
                }
            """.trimIndent()

            try {
                program = GlProgram(vertexShader, fragmentShader)
                program.setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                )
                program.setBufferAttribute(
                    "aTexSamplingCoord",
                    GlUtil.getTextureCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                )
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            val intensity = if (presentationTimeUs in windowStartUs..windowEndUs) {
                params.valueAt("intensity", presentationTimeUs).coerceIn(0f, 1f)
            } else {
                0f
            }
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatUniform("uIntensity", intensity)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtil.checkGlError()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
    }

    private companion object {
        const val TAG = "C4_EFFECT_EXPORT"
        const val TEST_TINT_ID = "test.windowed_red_tint"
        const val EXPORT_TIMEOUT_MS = 240_000L
        const val POLL_MS = 500L
        const val MIN_SEED_DURATION_MS = 2_500L
        const val SEED_FILE_NAME = "c4_effect_seed.mp4"
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
