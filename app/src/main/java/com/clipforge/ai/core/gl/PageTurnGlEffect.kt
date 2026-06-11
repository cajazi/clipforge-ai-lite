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

/**
 * Single A-tail item page-turn effect.
 *
 * The input texture is the outgoing A frame. [bFrameCache] supplies clip B's head frames,
 * which are uploaded as a second GL texture and sampled underneath the curl.
 */
enum class PageTurnDirection { LEFT, RIGHT, UP, DOWN }

@UnstableApi
class PageTurnGlEffect(
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val bFrameCache: CrossfadeFrameCache,
    private val direction: PageTurnDirection
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): PageTurnShaderProgram {
        return PageTurnShaderProgram(
            useHdr = useHdr,
            startTimeUs = startTimeUs,
            endTimeUs = endTimeUs,
            bFrameCache = bFrameCache,
            direction = direction
        )
    }
}

@UnstableApi
class PageTurnShaderProgram(
    useHdr: Boolean,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
    private val bFrameCache: CrossfadeFrameCache,
    private val direction: PageTurnDirection
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private var bTexId: Int = 0
    private var lastUploadedFrameIndex = -1
    private var lastBitmap: Bitmap = bFrameCache.frameInfoForOffset(0L)?.bitmap
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var firstUploadLogged = false
    private var uploadCount = 0

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
            uniform float uDirectionSign;
            uniform float uAxisVertical;
            varying vec2 vTexSamplingCoord;

            float smoothBand(float edge0, float edge1, float x) {
                return smoothstep(edge0, edge1, x);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec4 a = texture2D(uTexSampler, uv);
                vec4 b = texture2D(uBTexSampler, uv);

                float curlWidth = 0.30;
                float primaryRaw = mix(uv.x, uv.y, uAxisVertical);
                float secondaryRaw = mix(uv.y, uv.x, uAxisVertical);
                float x = mix(primaryRaw, 1.0 - primaryRaw, step(0.0, uDirectionSign));
                float curlLead = 1.08 - (1.22 * uProgress);
                float curlStart = curlLead - curlWidth;
                float curlEnd = curlLead;

                float revealedMask = smoothBand(curlStart, curlStart + 0.025, x);
                vec4 base = mix(a, b, revealedMask);

                float inCurl = smoothBand(curlStart, curlStart + 0.018, x) *
                    (1.0 - smoothBand(curlEnd - 0.018, curlEnd, x));

                float local = clamp((x - curlStart) / curlWidth, 0.0, 1.0);
                float curve = sin(local * 3.14159265);
                float secondaryBow = (secondaryRaw - 0.5) * 0.10 * curve;
                float backPrimary = clamp(curlStart - (local * curlWidth * 0.72), 0.0, 1.0);
                backPrimary = mix(backPrimary, 1.0 - backPrimary, step(0.0, uDirectionSign));
                float backSecondary = clamp(secondaryRaw + secondaryBow, 0.0, 1.0);
                vec2 horizontalBackUv = vec2(backPrimary, backSecondary);
                vec2 verticalBackUv = vec2(backSecondary, backPrimary);
                vec2 backUv = mix(horizontalBackUv, verticalBackUv, uAxisVertical);
                vec4 backsideSrc = texture2D(uTexSampler, backUv);
                float paperShade = 0.45 + (0.28 * curve) + (0.10 * (1.0 - local));
                float sheen = smoothBand(0.40, 0.56, local) * (1.0 - smoothBand(0.56, 0.72, local));
                vec3 backside = (backsideSrc.rgb * paperShade) + vec3(0.18, 0.16, 0.13) + (vec3(1.0, 0.94, 0.78) * sheen * 0.18);

                float shadow = exp(-abs(x - curlStart) * 24.0) * (0.36 + (0.20 * curve)) * uProgress;
                vec3 shadowedBase = base.rgb * (1.0 - (shadow * revealedMask));

                vec3 color = mix(shadowedBase, backside, inCurl);
                float edgeHighlight = exp(-abs(x - curlEnd) * 55.0) * 0.16 * inCurl;
                color += vec3(edgeHighlight);
                gl_FragColor = vec4(color, 1.0);
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
            updateBTexture((presentationTimeUs - startTimeUs).coerceAtLeast(0L))

            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setSamplerTexIdUniform("uBTexSampler", bTexId, /* texUnitIndex= */ 1)
            program.setFloatUniform("uProgress", t)
            program.setFloatUniform("uDirectionSign", directionSign(direction))
            program.setFloatUniform("uAxisVertical", if (direction == PageTurnDirection.UP || direction == PageTurnDirection.DOWN) 1f else 0f)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun directionSign(direction: PageTurnDirection): Float = when (direction) {
        PageTurnDirection.RIGHT,
        PageTurnDirection.DOWN -> 1f
        PageTurnDirection.LEFT,
        PageTurnDirection.UP -> -1f
    }

    private fun updateBTexture(intoWindowUs: Long) {
        val frame = bFrameCache.frameInfoForOffset(intoWindowUs)
        if (frame != null) {
            lastBitmap = frame.bitmap
        }
        if (bTexId != 0 && frame?.index == lastUploadedFrameIndex) return

        try {
            if (bTexId == 0) {
                bTexId = GlUtil.createTexture(lastBitmap)
            } else {
                GlUtil.setTexture(bTexId, lastBitmap)
            }
            lastUploadedFrameIndex = frame?.index ?: lastUploadedFrameIndex
            uploadCount++
            if (!firstUploadLogged) {
                firstUploadLogged = true
                Log.d(
                    TAG,
                    "secondTextureUpload=true animatedBFrameUpload=true texId=$bTexId " +
                        "size=${lastBitmap.width}x${lastBitmap.height} direction=$direction"
                )
            } else if (uploadCount <= 5 || uploadCount % 15 == 0) {
                Log.d(TAG, "animated B frame upload count=$uploadCount frameIndex=$lastUploadedFrameIndex direction=$direction")
            }
        } catch (e: GlUtil.GlException) {
            Log.e(TAG, "secondTextureUpload=false animatedBFrameUpload=false direction=$direction", e)
            throw e
        }
    }

    override fun release() {
        super.release()
        try {
            if (bTexId != 0) {
                GlUtil.deleteTexture(bTexId)
                bTexId = 0
            }
            program.delete()
            Log.d(TAG, "released direction=$direction animatedUploadCount=$uploadCount")
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val TAG = "PAGE_TURN"
    }
}
