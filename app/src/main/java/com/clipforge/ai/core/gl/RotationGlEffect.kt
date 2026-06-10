package com.clipforge.ai.core.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.GlEffect
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.cos
import kotlin.math.sin

enum class RotationMode { SPIN, ROTATE, CAMERA_ROLL }

/**
 * Rotates/scales the outgoing A-tail. Incoming B is handled by [RotationBitmapOverlay].
 */
@UnstableApi
class RotationGlEffect(
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val mode: RotationMode
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): RotationShaderProgram {
        return RotationShaderProgram(
            useHdr = useHdr,
            startTimeUs = startTimeUs,
            endTimeUs = endTimeUs,
            mode = mode
        )
    }
}

@UnstableApi
class RotationShaderProgram(
    useHdr: Boolean,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val mode: RotationMode
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private val startAngle = when (mode) {
        RotationMode.SPIN -> 0f
        RotationMode.ROTATE -> 0f
        RotationMode.CAMERA_ROLL -> 0f
    }
    private val endAngle = when (mode) {
        RotationMode.SPIN -> -45f
        RotationMode.ROTATE -> -24f
        RotationMode.CAMERA_ROLL -> 10f
    }
    private val startScale = when (mode) {
        RotationMode.SPIN -> 1.08f
        RotationMode.ROTATE -> 1.04f
        RotationMode.CAMERA_ROLL -> 1.12f
    }
    private val endScale = when (mode) {
        RotationMode.SPIN -> 0.94f
        RotationMode.ROTATE -> 0.98f
        RotationMode.CAMERA_ROLL -> 1.12f
    }

    init {
        val vertexShader = """
            attribute vec4 aFramePosition;
            attribute vec4 aTexSamplingCoord;
            uniform vec4 uRotation;
            uniform float uScale;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec2 pos = aFramePosition.xy * uScale;
                vec2 xy = vec2(
                    (uRotation.x * pos.x) + (uRotation.z * pos.y),
                    (uRotation.y * pos.x) + (uRotation.w * pos.y)
                );
                gl_Position = vec4(xy, aFramePosition.z, aFramePosition.w);
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
        try {
            val tRaw = if (endTimeUs > startTimeUs) {
                ((presentationTimeUs - startTimeUs).toFloat() / (endTimeUs - startTimeUs).toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val t = tRaw * tRaw * (3f - 2f * tRaw)
            val angleDegrees = startAngle + ((endAngle - startAngle) * t)
            val radians = Math.toRadians(angleDegrees.toDouble()).toFloat()
            val c = cos(radians)
            val s = sin(radians)
            val scale = startScale + ((endScale - startScale) * t)

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatsUniform("uRotation", floatArrayOf(c, s, -s, c))
            program.setFloatUniform("uScale", scale)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
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

@UnstableApi
class RotationBitmapOverlay(
    private val cache: CrossfadeFrameCache,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long,
    private val mode: RotationMode
) : BitmapOverlay() {

    private val tag = "ROTATION_OV"
    private var lastBitmap: Bitmap = cache.frameInfoForOffset(0L)?.bitmap
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var bitmapCallCount = 0
    private var settingsCallCount = 0

    init {
        cache.logStats("ROTATION_OVERLAY_CREATE_CACHE_STATS")
        Log.d(
            tag,
            "CREATE fadeStartUs=$fadeStartUs fadeEndUs=$fadeEndUs durationUs=${fadeEndUs - fadeStartUs} " +
                "mode=$mode cacheEmpty=${cache.isEmpty()} initialBitmap=${lastBitmap.width}x${lastBitmap.height}"
        )
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val intoWindowUs = (presentationTimeUs - fadeStartUs).coerceAtLeast(0L)
        val frame = cache.frameInfoForOffset(intoWindowUs)
        if (frame != null) {
            lastBitmap = frame.bitmap
        }
        bitmapCallCount++
        return lastBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return try {
            val span = (fadeEndUs - fadeStartUs).toFloat().coerceAtLeast(1f)
            val tRaw = ((presentationTimeUs - fadeStartUs).toFloat() / span).coerceIn(0f, 1f)
            val t = tRaw * tRaw * (3f - 2f * tRaw)
            val rotationStart = when (mode) {
                RotationMode.SPIN -> 60f
                RotationMode.ROTATE -> 24f
                RotationMode.CAMERA_ROLL -> -10f
            }
            val scaleStart = when (mode) {
                RotationMode.SPIN -> 0.84f
                RotationMode.ROTATE -> 0.96f
                RotationMode.CAMERA_ROLL -> 1.12f
            }
            val scaleEnd = when (mode) {
                RotationMode.SPIN -> 1.0f
                RotationMode.ROTATE -> 1.0f
                RotationMode.CAMERA_ROLL -> 1.08f
            }
            val alphaStart = when (mode) {
                RotationMode.SPIN -> 0.15f
                RotationMode.ROTATE -> 0.2f
                RotationMode.CAMERA_ROLL -> 0.25f
            }
            val rotation = rotationStart * (1f - t)
            val scale = scaleStart + ((scaleEnd - scaleStart) * t)
            val alpha = alphaStart + ((1f - alphaStart) * t)

            settingsCallCount++
            if (settingsCallCount <= 5 || settingsCallCount % 30 == 0) {
                Log.d(tag, "settings call=$settingsCallCount mode=$mode tRaw=$tRaw t=$t rotation=$rotation scale=$scale alpha=$alpha")
            }

            StaticOverlaySettings.Builder()
                .setAlphaScale(alpha)
                .setScale(scale, scale)
                .setRotationDegrees(rotation)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
                .build()
        } catch (t: Throwable) {
            Log.e(tag, "getOverlaySettings failed ptsUs=$presentationTimeUs mode=$mode", t)
            StaticOverlaySettings.Builder()
                .setAlphaScale(1f)
                .setScale(1f, 1f)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
                .build()
        }
    }

    override fun release() {
        super.release()
        cache.logStats("ROTATION_OVERLAY_RELEASE_CACHE_STATS")
        try { cache.release() } catch (t: Throwable) { Log.e(tag, "cache release failed", t) }
        Log.d(tag, "released bitmapCalls=$bitmapCallCount settingsCalls=$settingsCallCount mode=$mode")
    }
}
