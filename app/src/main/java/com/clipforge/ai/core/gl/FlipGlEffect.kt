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

/**
 * Center-pivot card flip for the outgoing A-tail.
 *
 * Incoming B is handled by [FlipBitmapOverlay], which appears at the midpoint swap and
 * expands from edge-on to full-frame without slide/cube translation.
 */
enum class FlipDirection { LEFT, RIGHT, UP, DOWN }

@UnstableApi
class FlipGlEffect(
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val direction: FlipDirection,
    private val perspective: Float = 0.42f,
    private val cullBackFace: Boolean = true
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): FlipShaderProgram {
        return FlipShaderProgram(
            useHdr = useHdr,
            startTimeUs = startTimeUs,
            endTimeUs = endTimeUs,
            direction = direction,
            perspective = perspective,
            cullBackFace = cullBackFace
        )
    }
}

@UnstableApi
class FlipShaderProgram(
    useHdr: Boolean,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val direction: FlipDirection,
    private val perspective: Float,
    private val cullBackFace: Boolean
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram

    init {
        val vertexShader = """
            attribute vec4 aFramePosition;
            attribute vec4 aTexSamplingCoord;
            uniform float uCosTheta;
            uniform float uSinTheta;
            uniform float uPerspective;
            uniform float uVertical;
            varying vec2 vTexSamplingCoord;
            varying float vFacing;

            void main() {
                vec2 pos = aFramePosition.xy;
                float z = mix(-pos.x * uSinTheta, pos.y * uSinTheta, uVertical);
                float perspectiveScale = 1.0 / max(0.55, 1.0 + (z * uPerspective));
                float x = mix(pos.x * uCosTheta, pos.x, uVertical) * perspectiveScale;
                float y = mix(pos.y, pos.y * uCosTheta, uVertical) * perspectiveScale;
                gl_Position = vec4(x, y, aFramePosition.z, aFramePosition.w);
                vTexSamplingCoord = aTexSamplingCoord.xy;
                vFacing = uCosTheta;
            }
        """.trimIndent()

        val fragmentShader = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform float uCullBackFace;
            varying vec2 vTexSamplingCoord;
            varying float vFacing;

            void main() {
                if (uCullBackFace > 0.5 && vFacing < 0.0) {
                    discard;
                }
                float edgeNarrowing = clamp(abs(vFacing), 0.0, 1.0);
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                float shade = 0.65 + (0.35 * edgeNarrowing);
                gl_FragColor = vec4(src.rgb * shade, src.a);
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
            val degrees = when (direction) {
                FlipDirection.LEFT,
                FlipDirection.UP -> -180f
                FlipDirection.RIGHT,
                FlipDirection.DOWN -> 180f
            }
            val radians = Math.toRadians((degrees * t).toDouble()).toFloat()

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatUniform("uCosTheta", cos(radians))
            program.setFloatUniform("uSinTheta", sin(radians))
            program.setFloatUniform("uPerspective", perspective.coerceIn(0f, 1f))
            program.setFloatUniform(
                "uVertical",
                if (direction == FlipDirection.UP || direction == FlipDirection.DOWN) 1f else 0f
            )
            program.setFloatUniform("uCullBackFace", if (cullBackFace) 1f else 0f)
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
class FlipBitmapOverlay(
    private val cache: CrossfadeFrameCache,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long,
    private val direction: FlipDirection
) : BitmapOverlay() {

    private val tag = "FLIP_OV"
    private var lastBitmap: Bitmap = cache.frameInfoForOffset(0L)?.bitmap
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var bitmapCallCount = 0
    private var settingsCallCount = 0

    init {
        cache.logStats("FLIP_OVERLAY_CREATE_CACHE_STATS")
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
            val raw = ((presentationTimeUs - fadeStartUs).toFloat() / span).coerceIn(0f, 1f)
            val p = raw * raw * (3f - 2f * raw)
            val revealRaw = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
            val reveal = revealRaw * revealRaw * (3f - 2f * revealRaw)
            val edgeScale = 0.04f + (0.96f * reveal)
            val horizontal = direction == FlipDirection.LEFT || direction == FlipDirection.RIGHT

            settingsCallCount++
            if (settingsCallCount <= 5 || settingsCallCount % 30 == 0) {
                Log.d(tag, "settings call=$settingsCallCount direction=$direction raw=$raw p=$p reveal=$reveal edgeScale=$edgeScale")
            }

            StaticOverlaySettings.Builder()
                .setAlphaScale(if (p >= 0.5f) 1f else 0f)
                .setScale(if (horizontal) edgeScale else 1f, if (horizontal) 1f else edgeScale)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
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
        cache.logStats("FLIP_OVERLAY_RELEASE_CACHE_STATS")
        try { cache.release() } catch (t: Throwable) { Log.e(tag, "cache release failed", t) }
        Log.d(tag, "released bitmapCalls=$bitmapCallCount settingsCalls=$settingsCallCount direction=$direction")
    }
}
