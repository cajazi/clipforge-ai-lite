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

enum class GlitchMode(
    val sliceWeight: Float,
    val blockWeight: Float,
    val rgbWeight: Float,
    val scanWeight: Float,
    val burstRate: Float,
    val burstDensity: Float,
    val sliceHeight: Float,
    val blockSize: Float,
    val posterize: Float
) {
    PRO(0.80f, 0.40f, 0.60f, 0.20f, 12.0f, 0.65f, 1f / 14f, 1f / 16f, 0.0f),
    DIGITAL(0.30f, 1.00f, 0.30f, 0.00f, 9.0f, 0.55f, 1f / 8f, 1f / 10f, 6.0f),
    RGB(0.20f, 0.00f, 1.00f, 0.00f, 10.0f, 0.60f, 1f / 20f, 1f / 16f, 0.0f),
    SCANLINE(0.30f, 0.00f, 0.25f, 1.00f, 14.0f, 0.75f, 1f / 96f, 1f / 16f, 0.0f)
}

@UnstableApi
class GlitchProGlEffect(
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val bFrameCache: CrossfadeFrameCache,
    private val mode: GlitchMode
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlitchProShaderProgram =
        GlitchProShaderProgram(useHdr, startTimeUs, endTimeUs, bFrameCache, mode)
}

@UnstableApi
class GlitchProShaderProgram(
    useHdr: Boolean,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val bFrameCache: CrossfadeFrameCache,
    private val mode: GlitchMode
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private var bTexId: Int = 0
    private var noiseTexId: Int = 0
    private var lastUploadedFrameIndex = -1
    private var lastBitmap: Bitmap = bFrameCache.frameInfoForOffset(0L)?.bitmap
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val noiseBitmap = buildNoiseBitmap()
    private var firstBUploadLogged = false
    private var sampledSettingsLogged = false
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
            uniform float uSliceWeight;
            uniform float uBlockWeight;
            uniform float uRgbWeight;
            uniform float uScanWeight;
            uniform float uBurstRate;
            uniform float uBurstDensity;
            uniform float uSliceHeight;
            uniform float uBlockSize;
            uniform float uPosterize;
            varying vec2 vTexSamplingCoord;

            highp float lutNoise(vec2 uv) {
                return texture2D(uNoiseSampler, fract(uv)).r;
            }

            vec3 posterizeColor(vec3 color, float levels, float amount) {
                if (levels <= 0.0 || amount <= 0.0) {
                    return color;
                }
                vec3 stepped = floor(color * levels) / max(levels - 1.0, 1.0);
                return mix(color, stepped, amount);
            }

            float burstGate(float progress, float tBurst, float envelope) {
                float forced = step(0.46, progress) * step(progress, 0.54);
                float noise = lutNoise(vec2(tBurst * 0.731 + 0.17, 0.37));
                float gated = step(1.0 - (envelope * uBurstDensity), noise);
                return max(gated, forced);
            }

            vec2 corruptedUv(vec2 uv, float amp, float tBurst) {
                highp float sliceIndex = floor(uv.y / max(uSliceHeight, 0.001));
                float sliceSeed = lutNoise(vec2(tBurst + sliceIndex * 0.017, 0.11));
                float sliceOffset = (sliceSeed - 0.5) * 0.24 * uSliceWeight * amp;

                highp vec2 blockCell = floor(uv / max(vec2(uBlockSize), vec2(0.001)));
                float blockSeed = lutNoise(vec2(tBurst + blockCell.x * 0.037, blockCell.y * 0.043));
                float blockActive = step(1.0 - (0.42 * uBlockWeight * amp), blockSeed);
                vec2 blockShift = vec2(
                    lutNoise(vec2(blockCell.x * 0.029 + tBurst, blockCell.y * 0.031)) - 0.5,
                    lutNoise(vec2(blockCell.y * 0.041 - tBurst, blockCell.x * 0.023)) - 0.5
                ) * 0.10 * uBlockWeight * amp * blockActive;

                float scanRow = floor(uv.y / max(uSliceHeight, 0.001));
                float scanSeed = lutNoise(vec2(tBurst * 0.41 + scanRow * 0.013, 0.83));
                float scanJitter = (scanSeed - 0.5) * 0.055 * uScanWeight * amp;
                float bandCenter = fract(1.0 - uProgress + tBurst * 0.23);
                float rollingBand = 1.0 - smoothstep(0.0, 0.18, abs(uv.y - bandCenter));
                scanJitter += rollingBand * 0.045 * uScanWeight * amp;

                return clamp(uv + vec2(sliceOffset + scanJitter, 0.0) + blockShift, 0.0, 1.0);
            }

            vec4 sampleSplit(sampler2D sampler, vec2 uv, float amp, float tBurst) {
                float rgbAmp = 0.025 * uRgbWeight * amp;
                float dirSeed = lutNoise(vec2(tBurst * 0.53 + 0.29, 0.61));
                vec2 rgbOff = vec2(rgbAmp * mix(-1.0, 1.0, step(0.5, dirSeed)), rgbAmp * 0.38);
                vec4 base = texture2D(sampler, uv);
                float r = texture2D(sampler, clamp(uv - rgbOff, 0.0, 1.0)).r;
                float b = texture2D(sampler, clamp(uv + rgbOff, 0.0, 1.0)).b;
                return vec4(r, base.g, b, base.a);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                float envelope = 1.0 - abs((uProgress * 2.0) - 1.0);
                float tBurst = floor(uProgress * uBurstRate) / uBurstRate;
                float gate = burstGate(uProgress, tBurst, envelope);
                float amp = envelope * gate;

                vec2 uvG = corruptedUv(uv, amp, tBurst);
                vec4 color = (uProgress < 0.5)
                    ? sampleSplit(uTexSampler, uvG, amp, tBurst)
                    : sampleSplit(uBTexSampler, uvG, amp, tBurst);

                if (uBlockWeight > 0.0) {
                    highp vec2 cell = floor(uv / max(vec2(uBlockSize), vec2(0.001)));
                    float blockSeed = lutNoise(vec2(cell.x * 0.047 + tBurst, cell.y * 0.059));
                    float blockActive = step(1.0 - (0.34 * uBlockWeight * amp), blockSeed);
                    vec3 damage = vec3(
                        lutNoise(vec2(cell.x * 0.071 + tBurst, 0.21)),
                        lutNoise(vec2(cell.y * 0.067 - tBurst, 0.49)),
                        lutNoise(vec2(cell.x * 0.031 + cell.y * 0.029, 0.77))
                    );
                    color.rgb = mix(color.rgb, mix(color.rgb, damage, 0.42), blockActive * uBlockWeight * amp);
                    color.rgb = posterizeColor(color.rgb, uPosterize, blockActive * amp);
                }

                if (uScanWeight > 0.0) {
                    float line = step(0.78, fract(uv.y / max(uSliceHeight * 0.35, 0.001)));
                    float bandCenter = fract(1.0 - uProgress + tBurst * 0.23);
                    float rollingBand = 1.0 - smoothstep(0.0, 0.15, abs(uv.y - bandCenter));
                    color.rgb *= 1.0 - (line * 0.16 * uScanWeight * amp);
                    color.rgb = mix(color.rgb, color.rgb * vec3(0.82, 0.96, 1.0), rollingBand * 0.18 * uScanWeight * amp);
                }

                gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), 1.0);
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
            val progress = if (endTimeUs > startTimeUs) {
                ((presentationTimeUs - startTimeUs).toFloat() / (endTimeUs - startTimeUs).toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            updateBTexture((presentationTimeUs - startTimeUs).coerceAtLeast(0L))
            ensureNoiseTexture()

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setSamplerTexIdUniform("uBTexSampler", bTexId, 1)
            program.setSamplerTexIdUniform("uNoiseSampler", noiseTexId, 2)
            program.setFloatUniform("uProgress", progress)
            program.setFloatUniform("uSliceWeight", mode.sliceWeight)
            program.setFloatUniform("uBlockWeight", mode.blockWeight)
            program.setFloatUniform("uRgbWeight", mode.rgbWeight)
            program.setFloatUniform("uScanWeight", mode.scanWeight)
            program.setFloatUniform("uBurstRate", mode.burstRate)
            program.setFloatUniform("uBurstDensity", mode.burstDensity)
            program.setFloatUniform("uSliceHeight", mode.sliceHeight)
            program.setFloatUniform("uBlockSize", mode.blockSize)
            program.setFloatUniform("uPosterize", mode.posterize)
            program.bindAttributesAndUniforms()
            if (!sampledSettingsLogged) {
                sampledSettingsLogged = true
                Log.d(
                    TAG,
                    "sampledSettings mode=$mode burstRate=${mode.burstRate} burstDensity=${mode.burstDensity} " +
                        "slice=${mode.sliceWeight} block=${mode.blockWeight} rgb=${mode.rgbWeight} scan=${mode.scanWeight}"
                )
            }
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
        var state = 0x4750524Fu
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
        const val TAG = "GLITCH_PRO"
        const val NOISE_SIZE = 64
    }
}
