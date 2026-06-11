package com.clipforge.ai.core.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect

enum class FilmBurnMode(
    val noiseScale: Float,
    val edgeSoftness: Float,
    val glowWidth: Float,
    val charWidth: Float,
    val charStrength: Float,
    val grainStrength: Float,
    val warmth: Float,
    val engulfHold: Float,
    val spread: Float,
    val driftSpeed: Float
) {
    CLASSIC(3.0f, 0.12f, 0.10f, 0.035f, 0.55f, 0.06f, 0.45f, 0.12f, 0.55f, 0.25f),
    WARM(2.0f, 0.18f, 0.14f, 0.015f, 0.25f, 0.04f, 0.70f, 0.10f, 0.45f, 0.15f),
    HEAVY(4.0f, 0.08f, 0.18f, 0.060f, 0.85f, 0.10f, 0.30f, 0.20f, 0.35f, 0.40f)
}

@UnstableApi
class FilmBurnGlEffect(
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val bFrameCache: CrossfadeFrameCache,
    private val mode: FilmBurnMode
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): FilmBurnShaderProgram =
        FilmBurnShaderProgram(useHdr, startTimeUs, endTimeUs, bFrameCache, mode)
}

@UnstableApi
class FilmBurnShaderProgram(
    useHdr: Boolean,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val bFrameCache: CrossfadeFrameCache,
    private val mode: FilmBurnMode
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private var bTexId: Int = 0
    private var noiseTexId: Int = 0
    private var lastUploadedFrameIndex = -1
    private var lastBitmap: Bitmap = bFrameCache.frameInfoForOffset(0L)?.bitmap
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val noiseBitmap = buildNoiseBitmap()
    private var firstBUploadLogged = false
    private var bUploadCount = 0

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
            uniform sampler2D uBTexSampler;
            uniform sampler2D uNoiseSampler;
            uniform float uProgress;
            uniform float uNoiseScale;
            uniform float uEdgeSoftness;
            uniform float uGlowWidth;
            uniform float uCharWidth;
            uniform float uCharStrength;
            uniform float uGrainStrength;
            uniform float uWarmth;
            uniform float uEngulfHold;
            uniform float uSpread;
            uniform float uDriftSpeed;
            varying vec2 vTexSamplingCoord;

            float smoothBand(float edge0, float edge1, float x) {
                return smoothstep(edge0, edge1, x);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec4 a = texture2D(uTexSampler, uv);
                vec4 b = texture2D(uBTexSampler, uv);

                vec2 drift = vec2(uProgress * uDriftSpeed, -uProgress * uDriftSpeed * 0.45);
                float n1 = texture2D(uNoiseSampler, uv * uNoiseScale + drift).r;
                float n2 = texture2D(uNoiseSampler, uv * (uNoiseScale * 2.17) - drift.yx).g;
                float n3 = texture2D(uNoiseSampler, uv * (uNoiseScale * 5.31) + drift * 1.7).b;
                float noise = (n1 * 0.58) + (n2 * 0.30) + (n3 * 0.12);

                float holdStart = 1.0 - uEngulfHold;
                float revealProgress = smoothstep(0.0, holdStart, uProgress);
                float center = mix(-uSpread, 1.0 + uSpread, revealProgress);
                float diagonal = (uv.x * 0.72) + ((1.0 - uv.y) * 0.28);
                float front = diagonal + ((noise - 0.5) * 0.42);
                float mask = smoothBand(center - uEdgeSoftness, center + uEdgeSoftness, front);

                float edgeDistance = abs(front - center);
                float glow = (1.0 - smoothBand(0.0, uGlowWidth, edgeDistance)) * (1.0 - mask * 0.35);
                float charEdge = (1.0 - smoothBand(0.0, uCharWidth, edgeDistance)) * (1.0 - mask) * uCharStrength;
                float grain = (n3 - 0.5) * uGrainStrength;

                vec3 base = mix(a.rgb, b.rgb, mask);
                vec3 warmTint = vec3(1.0, 0.50 + (uWarmth * 0.24), 0.16);
                vec3 over = base + warmTint * glow * (1.15 + uWarmth);
                over += vec3(grain);
                over = mix(over, vec3(0.05, 0.018, 0.006), charEdge);
                over.r += uWarmth * glow * 0.28;
                over.g += uWarmth * glow * 0.08;

                gl_FragColor = vec4(clamp(over, 0.0, 1.0), 1.0);
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
        try {
            val raw = if (endTimeUs > startTimeUs) {
                ((presentationTimeUs - startTimeUs).toFloat() / (endTimeUs - startTimeUs).toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val t = raw * raw * (3f - 2f * raw)
            updateBTexture((presentationTimeUs - startTimeUs).coerceAtLeast(0L))
            ensureNoiseTexture()

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setSamplerTexIdUniform("uBTexSampler", bTexId, 1)
            program.setSamplerTexIdUniform("uNoiseSampler", noiseTexId, 2)
            program.setFloatUniform("uProgress", t)
            program.setFloatUniform("uNoiseScale", mode.noiseScale)
            program.setFloatUniform("uEdgeSoftness", mode.edgeSoftness)
            program.setFloatUniform("uGlowWidth", mode.glowWidth)
            program.setFloatUniform("uCharWidth", mode.charWidth)
            program.setFloatUniform("uCharStrength", mode.charStrength)
            program.setFloatUniform("uGrainStrength", mode.grainStrength)
            program.setFloatUniform("uWarmth", mode.warmth)
            program.setFloatUniform("uEngulfHold", mode.engulfHold)
            program.setFloatUniform("uSpread", mode.spread)
            program.setFloatUniform("uDriftSpeed", mode.driftSpeed)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun updateBTexture(intoWindowUs: Long) {
        val frame = bFrameCache.frameInfoForOffset(intoWindowUs)
        if (frame != null) lastBitmap = frame.bitmap
        if (bTexId != 0 && frame?.index == lastUploadedFrameIndex) return
        try {
            if (bTexId == 0) bTexId = GlUtil.createTexture(lastBitmap) else GlUtil.setTexture(bTexId, lastBitmap)
            lastUploadedFrameIndex = frame?.index ?: lastUploadedFrameIndex
            bUploadCount++
            if (!firstBUploadLogged) {
                firstBUploadLogged = true
                Log.d(TAG, "secondTextureUpload=true animatedBFrameUpload=true texId=$bTexId size=${lastBitmap.width}x${lastBitmap.height} mode=$mode")
            }
        } catch (e: GlUtil.GlException) {
            Log.e(TAG, "secondTextureUpload=false animatedBFrameUpload=false mode=$mode", e)
            throw e
        }
    }

    private fun ensureNoiseTexture() {
        if (noiseTexId != 0) return
        try {
            noiseTexId = GlUtil.createTexture(noiseBitmap)
            Log.d(TAG, "noiseTextureUpload=true texId=$noiseTexId size=${noiseBitmap.width}x${noiseBitmap.height} mode=$mode")
        } catch (e: GlUtil.GlException) {
            Log.e(TAG, "noiseTextureUpload=false mode=$mode", e)
            throw e
        }
    }

    override fun release() {
        super.release()
        try {
            if (bTexId != 0) GlUtil.deleteTexture(bTexId)
            if (noiseTexId != 0) GlUtil.deleteTexture(noiseTexId)
            if (!noiseBitmap.isRecycled) noiseBitmap.recycle()
            program.delete()
            Log.d(TAG, "released mode=$mode animatedUploadCount=$bUploadCount")
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private fun buildNoiseBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(NOISE_SIZE, NOISE_SIZE, Bitmap.Config.ARGB_8888)
        var state = 0x4D595DF4u
        for (y in 0 until NOISE_SIZE) {
            for (x in 0 until NOISE_SIZE) {
                state = state * 1664525u + 1013904223u
                val r = (state and 0xFFu).toInt()
                state = state * 1664525u + 1013904223u
                val g = (state and 0xFFu).toInt()
                state = state * 1664525u + 1013904223u
                val b = (state and 0xFFu).toInt()
                bitmap.setPixel(x, y, Color.argb(255, r, g, b))
            }
        }
        return bitmap
    }

    private companion object {
        const val TAG = "FILM_BURN"
        const val NOISE_SIZE = 64
    }
}
