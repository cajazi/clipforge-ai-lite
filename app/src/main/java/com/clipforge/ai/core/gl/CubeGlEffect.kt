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

enum class CubeDirection { LEFT, RIGHT, UP, DOWN }

internal val CubeDirection.isVertical: Boolean
    get() = this == CubeDirection.UP || this == CubeDirection.DOWN

/**
 * 2.5D cube approximation for the outgoing A-tail (horizontal and vertical turns).
 *
 * This intentionally avoids true perspective/depth. It narrows and shifts A along the
 * turn axis as if the face is rotating away, while [CubeBitmapOverlay] brings B in as
 * the next face.
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
            uniform float uScale;
            uniform float uOffset;
            uniform float uShear;
            uniform float uVertical;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec2 pos = aFramePosition.xy;
                vec2 horizontal = vec2((pos.x * uScale) + uOffset + (pos.y * uShear), pos.y);
                vec2 vertical = vec2(pos.x, (pos.y * uScale) + uOffset + (pos.x * uShear));
                vec2 xy = mix(horizontal, vertical, uVertical);
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
            // NDC sign of A's exit edge: LEFT exits -x, RIGHT exits +x, UP exits +y (NDC
            // y is up-positive), DOWN exits -y.
            val sign = when (direction) {
                CubeDirection.LEFT -> -1f
                CubeDirection.RIGHT -> 1f
                CubeDirection.UP -> 1f
                CubeDirection.DOWN -> -1f
            }
            val scale = 1f - (0.72f * t)
            val offset = sign * 0.86f * t
            val shear = -sign * 0.18f * t
            val shade = 1f - (0.32f * t)

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatUniform("uScale", scale)
            program.setFloatUniform("uOffset", offset)
            program.setFloatUniform("uShear", shear)
            program.setFloatUniform("uVertical", if (direction.isVertical) 1f else 0f)
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
            // Opposite of CubeShaderProgram's exit sign: B enters from the edge A exposes.
            val sign = when (direction) {
                CubeDirection.LEFT -> 1f
                CubeDirection.RIGHT -> -1f
                CubeDirection.UP -> -1f
                CubeDirection.DOWN -> 1f
            }
            val vertical = direction.isVertical
            val remaining = 1f - t
            val scaleAxis = 0.28f + (0.72f * t)
            val anchor = sign * remaining
            val bg = -sign * remaining
            val alpha = 0.82f + (0.18f * t)

            settingsCallCount++
            if (settingsCallCount <= 5 || settingsCallCount % 30 == 0) {
                Log.d(tag, "settings call=$settingsCallCount direction=$direction tRaw=$tRaw t=$t scaleAxis=$scaleAxis anchor=$anchor alpha=$alpha")
            }

            StaticOverlaySettings.Builder()
                .setAlphaScale(alpha)
                .setScale(if (vertical) 1f else scaleAxis, if (vertical) scaleAxis else 1f)
                .setOverlayFrameAnchor(if (vertical) 0f else anchor, if (vertical) anchor else 0f)
                .setBackgroundFrameAnchor(if (vertical) 0f else bg, if (vertical) bg else 0f)
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
