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
 * Phase 0 whip-pan spike: a timestamp-driven directional blur on a single input stream.
 *
 * This deliberately avoids temporal frame buffering. It proves Media3's GPU/GL export path can run
 * a transition shader while the existing overlay pipeline supplies clip B.
 */
@UnstableApi
class DirectionalBlurGlEffect(
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val directionX: Float,
    private val directionY: Float,
    private val maxBlurTexels: Float = 22f
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): DirectionalBlurShaderProgram {
        return DirectionalBlurShaderProgram(
            useHdr = useHdr,
            startTimeUs = startTimeUs,
            endTimeUs = endTimeUs,
            directionX = directionX,
            directionY = directionY,
            maxBlurTexels = maxBlurTexels
        )
    }
}

@UnstableApi
class DirectionalBlurShaderProgram(
    useHdr: Boolean,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val directionX: Float,
    private val directionY: Float,
    private val maxBlurTexels: Float
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
            uniform vec2 uBlurStep;
            uniform float uBoost;
            varying vec2 vTexSamplingCoord;

            vec4 sampleAt(float offset) {
                vec2 uv = clamp(vTexSamplingCoord + (uBlurStep * offset), 0.0, 1.0);
                return texture2D(uTexSampler, uv);
            }

            void main() {
                vec4 color = sampleAt(0.0) * 0.18;
                color += sampleAt(-4.0) * 0.06;
                color += sampleAt(-3.0) * 0.09;
                color += sampleAt(-2.0) * 0.12;
                color += sampleAt(-1.0) * 0.15;
                color += sampleAt(1.0) * 0.15;
                color += sampleAt(2.0) * 0.12;
                color += sampleAt(3.0) * 0.09;
                color += sampleAt(4.0) * 0.06;
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
                "uBlurStep",
                floatArrayOf(
                    directionX * blurTexels / width.toFloat(),
                    directionY * blurTexels / height.toFloat()
                )
            )
            program.setFloatUniform("uBoost", 0.06f * easedPeak)
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
