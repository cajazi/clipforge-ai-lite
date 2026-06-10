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

enum class CubeDirection { LEFT, RIGHT }

/**
 * Horizontal 2.5D cube approximation for the outgoing A-tail.
 *
 * This intentionally avoids true perspective/depth. It narrows and shifts A as if the
 * face is rotating away, while [CubeBitmapOverlay] brings B in as the next face.
 */
@UnstableApi
class CubeGlEffect(
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val direction: CubeDirection
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): CubeShaderProgram {
        return CubeShaderProgram(
            useHdr = useHdr,
            startTimeUs = startTimeUs,
            endTimeUs = endTimeUs,
            direction = direction
        )
    }
}

@UnstableApi
class CubeShaderProgram(
    useHdr: Boolean,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val direction: CubeDirection
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram

    init {
        val vertexShader = """
            attribute vec4 aFramePosition;
            attribute vec4 aTexSamplingCoord;
            uniform float uScaleX;
            uniform float uOffsetX;
            uniform float uShear;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec2 xy = vec2((aFramePosition.x * uScaleX) + uOffsetX + (aFramePosition.y * uShear), aFramePosition.y);
                gl_Position = vec4(xy, aFramePosition.z, aFramePosition.w);
                vTexSamplingCoord = aTexSamplingCoord.xy;
            }
        """.trimIndent()

        val fragmentShader = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform float uShade;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                gl_FragColor = vec4(src.rgb * uShade, src.a);
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
            val sign = if (direction == CubeDirection.LEFT) -1f else 1f
            val scaleX = 1f - (0.72f * t)
            val offsetX = sign * 0.86f * t
            val shear = -sign * 0.18f * t
            val shade = 1f - (0.32f * t)

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatUniform("uScaleX", scaleX)
            program.setFloatUniform("uOffsetX", offsetX)
            program.setFloatUniform("uShear", shear)
            program.setFloatUniform("uShade", shade)
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
class CubeBitmapOverlay(
    private val cache: CrossfadeFrameCache,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long,
    private val direction: CubeDirection
) : BitmapOverlay() {

    private val tag = "CUBE_OV"
    private var lastBitmap: Bitmap = cache.frameInfoForOffset(0L)?.bitmap
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var bitmapCallCount = 0
    private var settingsCallCount = 0

    init {
        cache.logStats("CUBE_OVERLAY_CREATE_CACHE_STATS")
        Log.d(
            tag,
            "CREATE fadeStartUs=$fadeStartUs fadeEndUs=$fadeEndUs durationUs=${fadeEndUs - fadeStartUs} " +
                "direction=$direction cacheEmpty=${cache.isEmpty()} initialBitmap=${lastBitmap.width}x${lastBitmap.height}"
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
            val sign = if (direction == CubeDirection.LEFT) 1f else -1f
            val remaining = 1f - t
            val scaleX = 0.28f + (0.72f * t)
            val anchorX = sign * remaining
            val bgX = -sign * remaining
            val alpha = 0.82f + (0.18f * t)

            settingsCallCount++
            if (settingsCallCount <= 5 || settingsCallCount % 30 == 0) {
                Log.d(tag, "settings call=$settingsCallCount direction=$direction tRaw=$tRaw t=$t scaleX=$scaleX anchorX=$anchorX alpha=$alpha")
            }

            StaticOverlaySettings.Builder()
                .setAlphaScale(alpha)
                .setScale(scaleX, 1f)
                .setOverlayFrameAnchor(anchorX, 0f)
                .setBackgroundFrameAnchor(bgX, 0f)
                .build()
        } catch (t: Throwable) {
            Log.e(tag, "getOverlaySettings failed ptsUs=$presentationTimeUs direction=$direction", t)
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
        cache.logStats("CUBE_OVERLAY_RELEASE_CACHE_STATS")
        try { cache.release() } catch (t: Throwable) { Log.e(tag, "cache release failed", t) }
        Log.d(tag, "released bitmapCalls=$bitmapCallCount settingsCalls=$settingsCallCount direction=$direction")
    }
}
