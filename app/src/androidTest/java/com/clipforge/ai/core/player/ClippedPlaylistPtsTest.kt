@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.GLES20
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clipforge.ai.RequiresGpuExport
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@RequiresGpuExport
class ClippedPlaylistPtsTest {

    @Test
    fun clipped_playlist_preserves_continuous_effect_presentation_time() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val seed = seedPath(context)
        val observedPts = Collections.synchronizedList(mutableListOf<Long>())
        var player: ExoPlayer? = null
        val surfaceTexture = SurfaceTexture(0)
        val surface = Surface(surfaceTexture)
        val ended = CountDownLatch(1)

        try {
            instrumentation.runOnMainSync {
                val createdPlayer = ExoPlayer.Builder(context).build()
                player = createdPlayer
                createdPlayer.setVideoSurface(surface)
                createdPlayer.setVideoEffects(listOf(PtsProbeEffect(observedPts)))
                createdPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) ended.countDown()
                    }
                })
                createdPlayer.setMediaItems(
                    listOf(
                        clippedItem(seed, 0L, 500L),
                        clippedItem(seed, 500L, 1_000L),
                        clippedItem(seed, 1_000L, 1_500L)
                    )
                )
                createdPlayer.prepare()
                createdPlayer.play()
            }

            assertTrue("playlist did not end", ended.await(20, TimeUnit.SECONDS))
            val pts = observedPts.toList()
            assertTrue("no GL frames observed", pts.isNotEmpty())
            assertTrue("PTS never reached second clipped item: max=${pts.maxOrNull()}", pts.maxOrNull()!! >= 700_000L)
            assertTrue("PTS never reached third clipped item: max=${pts.maxOrNull()}", pts.maxOrNull()!! >= 1_200_000L)
            assertTrue("timeline-gated window did not activate", pts.any { it in 700_000L..900_000L })
            assertTrue("PTS was not monotonic", pts.zipWithNext().all { (a, b) -> b >= a })
        } finally {
            player?.let { createdPlayer ->
                instrumentation.runOnMainSync { createdPlayer.release() }
            }
            surface.release()
            surfaceTexture.release()
        }
    }

    private fun clippedItem(path: String, startMs: Long, endMs: Long): MediaItem =
        MediaItem.Builder()
            .setUri(path)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

    private class PtsProbeEffect(
        private val observedPts: MutableList<Long>
    ) : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
            PtsProbeProgram(useHdr, observedPts)
    }

    private class PtsProbeProgram(
        useHdr: Boolean,
        private val observedPts: MutableList<Long>
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
                varying vec2 vTexSamplingCoord;
                void main() {
                    gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord);
                }
            """.trimIndent()
            try {
                program = GlProgram(vertexShader, fragmentShader)
                program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
                program.setBufferAttribute("aTexSamplingCoord", GlUtil.getTextureCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            observedPts += presentationTimeUs
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
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

    private fun seedPath(context: Context): String {
        val mediaDir = File(context.filesDir, "media")
        mediaDir.mkdirs()
        val seed = File(mediaDir, "c5_pts_seed.mp4")
        if (!seed.isFile || seed.length() == 0L) {
            generateSyntheticSeedVideo(seed)
        }
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
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
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
        repeat(ySize) { index -> buffer.put((32 + ((index + frameIndex * 5) % 192)).toByte()) }
        val u = (96 + (frameIndex * 3 % 48)).toByte()
        val v = (160 - (frameIndex * 2 % 48)).toByte()
        repeat(ySize / 4) {
            buffer.put(u)
            buffer.put(v)
        }
        return frameSize
    }

    private companion object {
        const val SEED_WIDTH = 320
        const val SEED_HEIGHT = 240
        const val SEED_FPS = 15
        const val SEED_FRAME_COUNT = SEED_FPS * 2
        const val SEED_BIT_RATE = 600_000
        const val CODEC_TIMEOUT_US = 10_000L
    }
}
