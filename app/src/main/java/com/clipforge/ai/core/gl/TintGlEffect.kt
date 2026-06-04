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

/**
 * STEP 1b - trivial visible GLSL shader, proven end to end through the export path.
 *
 * This is intentionally ugly: it samples each input frame and blends it heavily
 * toward magenta so there is ZERO ambiguity about whether the custom shader ran.
 * The point is not the effect - it's proving that a custom GlEffect / GlShaderProgram
 * compiles and executes inside Media3 Transformer on the real device.
 *
 * Once this is confirmed on-device, the same plumbing carries the real two-clip
 * crossfade shader (Step 2) and everything after it.
 */
@UnstableApi
class TintGlEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): TintShaderProgram {
        return TintShaderProgram(useHdr)
    }
}

@UnstableApi
class TintShaderProgram(useHdr: Boolean) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram

    private var width: Int = 0
    private var height: Int = 0

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
            varying vec2 vTexSamplingCoord;
            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                // Heavy magenta tint so it is unmistakable that the shader ran.
                vec3 tinted = mix(src.rgb, vec3(1.0, 0.0, 1.0), 0.6);
                gl_FragColor = vec4(tinted, src.a);
            }
        """.trimIndent()

        try {
            program = GlProgram(vertexShader, fragmentShader)
            // Full-screen quad: positions + matching texture coordinates.
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
        width = inputWidth
        height = inputHeight
        // Output size == input size (no scaling).
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.bindAttributesAndUniforms()
            // The base class has already bound the output framebuffer/viewport for us.
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
