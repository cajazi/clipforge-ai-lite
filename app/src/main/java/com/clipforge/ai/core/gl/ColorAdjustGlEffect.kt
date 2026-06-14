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
import com.clipforge.ai.core.effects.EffectFactory
import com.clipforge.ai.core.effects.ParamProvider

@UnstableApi
class ColorAdjustGlEffect(
    private val windowStartUs: Long,
    private val windowEndUs: Long,
    private val params: ParamProvider
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): ColorAdjustShaderProgram {
        return ColorAdjustShaderProgram(useHdr, windowStartUs, windowEndUs, params)
    }
}

@UnstableApi
class ColorAdjustShaderProgram(
    useHdr: Boolean,
    private val windowStartUs: Long,
    private val windowEndUs: Long,
    private val params: ParamProvider
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

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
            uniform float uBrightness;
            uniform float uContrast;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 contrasted = ((src.rgb - vec3(0.5)) * uContrast) + vec3(0.5);
                vec3 adjusted = clamp(contrasted * uBrightness, 0.0, 1.0);
                gl_FragColor = vec4(adjusted, src.a);
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
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val values = colorAdjustValuesAt(
                presentationTimeUs = presentationTimeUs,
                windowStartUs = windowStartUs,
                windowEndUs = windowEndUs,
                provider = params
            )

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatUniform("uBrightness", values.brightness)
            program.setFloatUniform("uContrast", values.contrast)
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
object ColorAdjustEffectFactory : EffectFactory {
    override fun create(windowStartUs: Long, windowEndUs: Long, params: ParamProvider): GlEffect {
        return ColorAdjustGlEffect(windowStartUs, windowEndUs, params)
    }
}
