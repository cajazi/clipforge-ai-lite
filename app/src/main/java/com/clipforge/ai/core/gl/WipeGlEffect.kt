package com.clipforge.ai.core.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect

enum class WipeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN
}

@UnstableApi
class WipeGlEffect(
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val bFrameCache: CrossfadeFrameCache,
    private val direction: WipeDirection
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): WipeShaderProgram =
        WipeShaderProgram(useHdr, startTimeUs, endTimeUs, bFrameCache, direction)
}

@UnstableApi
class WipeShaderProgram(
    useHdr: Boolean,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val bFrameCache: CrossfadeFrameCache,
    private val direction: WipeDirection
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private var bTexId: Int = 0
    private var lastUploadedFrameIndex = -1
    private var lastBitmap: Bitmap = bFrameCache.frameInfoForOffset(0L)?.bitmap
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
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
            uniform float uProgress;
            uniform float uDirectionX;
            uniform float uDirectionY;
            varying vec2 vTexSamplingCoord;

            float smoothProgress(float t) {
                return t * t * (3.0 - 2.0 * t);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                float p = smoothProgress(clamp(uProgress, 0.0, 1.0));
                float horizontal = step(0.5, abs(uDirectionX));
                float axis = mix(uv.y, uv.x, horizontal);
                float reverse = step(0.0, -(uDirectionX + uDirectionY));
                axis = mix(axis, 1.0 - axis, reverse);
                float softness = 0.035;
                float reveal = 1.0 - smoothstep(p - softness, p + softness, axis);
                vec4 a = texture2D(uTexSampler, uv);
                vec4 b = texture2D(uBTexSampler, uv);
                gl_FragColor = mix(a, b, reveal);
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

            val directionX = when (direction) {
                WipeDirection.LEFT -> -1f
                WipeDirection.RIGHT -> 1f
                WipeDirection.UP,
                WipeDirection.DOWN -> 0f
            }
            val directionY = when (direction) {
                WipeDirection.UP -> -1f
                WipeDirection.DOWN -> 1f
                WipeDirection.LEFT,
                WipeDirection.RIGHT -> 0f
            }

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setSamplerTexIdUniform("uBTexSampler", bTexId, 1)
            program.setFloatUniform("uProgress", progress)
            program.setFloatUniform("uDirectionX", directionX)
            program.setFloatUniform("uDirectionY", directionY)
            program.bindAttributesAndUniforms()
            if (!sampledSettingsLogged) {
                sampledSettingsLogged = true
                Log.d(TAG, "sampledSettings direction=$direction softEdge=0.035")
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
                Log.d(TAG, "secondTextureUpload=true animatedBFrameUpload=true texId=$bTexId size=${lastBitmap.width}x${lastBitmap.height} direction=$direction")
            }
        } catch (e: GlUtil.GlException) {
            Log.e(TAG, "secondTextureUpload=false animatedBFrameUpload=false direction=$direction", e)
            throw e
        }
    }

    override fun release() {
        super.release()
        try {
            if (bTexId != 0) GlUtil.deleteTexture(bTexId)
            program.delete()
            Log.d(TAG, "released direction=$direction animatedUploadCount=$bUploadCount")
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val TAG = "WIPE_GL"
    }
}
