package com.clipforge.ai.validation.c9.support

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * C9.0 support harness: generates a small synthetic seed video for instrumented export tests, so
 * each test does not need its own copy of raw [MediaCodec]/[MediaMuxer] encoding boilerplate.
 * Mirrors the proven approach in `TransitionExportAbValidationTest` / `TransformAnimationExportValidationTest`
 * (must-stay-green references) - same format, same resolution, same generation strategy.
 */
object SeedMediaFactory {
    const val SEED_WIDTH = 320
    const val SEED_HEIGHT = 240
    const val MIN_SEED_DURATION_MS = 1500L

    private const val TAG = "C9_SeedMediaFactory"
    private const val SEED_FILE_NAME = "c9_validation_seed.mp4"
    private const val SEED_FPS = 15
    private const val SEED_DURATION_SECONDS = 3
    private const val SEED_FRAME_COUNT = SEED_FPS * SEED_DURATION_SECONDS
    private const val SEED_BIT_RATE = 600_000
    private const val CODEC_TIMEOUT_US = 10_000L

    fun seedPath(context: Context): String {
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

    fun readDurationMs(path: String): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { mmr.release() }
        }
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

    private fun fillSyntheticYuvFrame(buffer: ByteBuffer, frameIndex: Int): Int {
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
}
