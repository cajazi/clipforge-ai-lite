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
import com.clipforge.ai.core.animation.TransformMath
import com.clipforge.ai.core.effects.EffectFactory
import com.clipforge.ai.core.effects.ParamProvider

/**
 * Transform animation effect (C8.2). A thin GL consumer of [TransformMath]: the shader contains
 * NO transform equations — the vertex stage only multiplies the precomputed matrix uploaded as a
 * uniform, and the fragment stage only multiplies the uploaded opacity. All position/scale/
 * rotation/pivot/aspect math lives in [TransformMath] (single source of truth).
 *
 * Code-only foundation: unregistered, not wired to preview/export/UI.
 */
@UnstableApi
class TransformAnimationGlEffect(
    private val windowStartUs: Long,
    private val windowEndUs: Long,
    private val params: ParamProvider
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): TransformAnimationShaderProgram {
        return TransformAnimationShaderProgram(useHdr, windowStartUs, windowEndUs, params)
    }
}

@UnstableApi
class TransformAnimationShaderProgram(
    useHdr: Boolean,
    private val windowStartUs: Long,
    private val windowEndUs: Long,
    private val params: ParamProvider
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private var aspect: Float = 1f

    init {
        val vertexShader = """
            attribute vec4 aFramePosition;
            attribute vec4 aTexSamplingCoord;
            uniform mat4 uMatrix;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = uMatrix * aFramePosition;
                vTexSamplingCoord = aTexSamplingCoord.xy;
            }
        """.trimIndent()

        val fragmentShader = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform float uOpacity;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                gl_FragColor = vec4(src.rgb, src.a * uOpacity);
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
        aspect = if (inputHeight > 0) inputWidth.toFloat() / inputHeight.toFloat() else 1f
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            // SINGLE SOURCE OF TRUTH: all transform math comes from TransformMath; this code
            // only applies the resulting matrix + opacity.
            val values = TransformMath.resolveValues(presentationTimeUs, windowStartUs, windowEndUs, params)
            val matrix = TransformMath.composeMatrix(values, aspect).toColumnMajor4x4()

            // Clear to transparent so regions outside the transformed quad reveal lower layers.
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatsUniform("uMatrix", matrix)
            program.setFloatUniform("uOpacity", TransformMath.opacityOf(values))
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

/**
 * Factory for the transform animation effect. Defined but NOT registered/released (C8.2 is a
 * code-only foundation; preview/export wiring is C8.3/C8.4).
 */
@UnstableApi
object AnimationEffectFactory : EffectFactory {
    override fun create(windowStartUs: Long, windowEndUs: Long, params: ParamProvider): GlEffect {
        return TransformAnimationGlEffect(windowStartUs, windowEndUs, params)
    }
}
