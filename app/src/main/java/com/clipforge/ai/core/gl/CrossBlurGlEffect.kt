package com.clipforge.ai.core.gl

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import kotlin.math.abs

/**
 * Timestamp-driven isotropic blur for the outgoing A stream.
 *
 * Blur peaks at the middle of the overlap and returns to sharp at the end, so the cutover can
 * happen without adding time or introducing directional motion.
 */
@UnstableApi
class CrossBlurGlEffect(
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val maxBlurTexels: Float = 18f,
    private val boost: Float = 0.0f
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): CrossBlurShaderProgram {
        return CrossBlurShaderProgram(useHdr, startTimeUs, endTimeUs, maxBlurTexels, boost)
    }
}

@UnstableApi
class CrossBlurShaderProgram(
    useHdr: Boolean,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val maxBlurTexels: Float,
    private val boost: Float
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private var width: Int = 1
    private var height: Int = 1

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
            uniform vec2 uStep;
            uniform float uBoost;
            varying vec2 vTexSamplingCoord;

            vec4 sampleAt(vec2 offset) {
                vec2 uv = clamp(vTexSamplingCoord + offset, 0.0, 1.0);
                return texture2D(uTexSampler, uv);
            }

            void main() {
                vec4 color = sampleAt(vec2(0.0, 0.0)) * 0.20;
                color += sampleAt(vec2( uStep.x, 0.0)) * 0.10;
                color += sampleAt(vec2(-uStep.x, 0.0)) * 0.10;
                color += sampleAt(vec2(0.0,  uStep.y)) * 0.10;
                color += sampleAt(vec2(0.0, -uStep.y)) * 0.10;
                color += sampleAt(vec2( uStep.x,  uStep.y)) * 0.08;
                color += sampleAt(vec2(-uStep.x,  uStep.y)) * 0.08;
                color += sampleAt(vec2( uStep.x, -uStep.y)) * 0.08;
                color += sampleAt(vec2(-uStep.x, -uStep.y)) * 0.08;
                color += sampleAt(vec2( uStep.x * 2.0, 0.0)) * 0.04;
                color += sampleAt(vec2(-uStep.x * 2.0, 0.0)) * 0.04;
                gl_FragColor = vec4(min(color.rgb + vec3(uBoost), vec3(1.0)), color.a);
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

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth.coerceAtLeast(1)
        height = inputHeight.coerceAtLeast(1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val t = if (endTimeUs > startTimeUs) {
                ((presentationTimeUs - startTimeUs).toFloat() / (endTimeUs - startTimeUs).toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val peak = (1f - abs((t * 2f) - 1f)).coerceIn(0f, 1f)
            val easedPeak = peak * peak * (3f - 2f * peak)
            val blurTexels = maxBlurTexels * easedPeak

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatsUniform(
                "uStep",
                floatArrayOf(
                    blurTexels / width.toFloat(),
                    blurTexels / height.toFloat()
                )
            )
            program.setFloatUniform("uBoost", boost * easedPeak)
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
