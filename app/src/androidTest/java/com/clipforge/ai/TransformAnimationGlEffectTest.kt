package com.clipforge.ai

import android.opengl.EGL14
import android.opengl.EGLConfig
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.animation.TransformMath
import com.clipforge.ai.core.effects.ConstantParams
import com.clipforge.ai.core.gl.AnimationEffectFactory
import com.clipforge.ai.core.gl.TransformAnimationGlEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device adapter test for C8.2 (SM-A165F). Stands up a minimal EGL14 GLES2 context so the
 * TransformAnimationGlEffect's GLSL actually compiles + links on the device GPU (the real
 * "builds on device" check, incl. the uMatrix / uOpacity uniform path), and runs the
 * TransformMath single-source-of-truth on the device's ART.
 *
 * Does NOT wire preview/export — it constructs the effect in a standalone EGL context only.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TransformAnimationGlEffectTest {

    private fun params() = ConstantParams(
        mapOf(
            AnimationPropertyKeys.POSITION_X to 0f,
            AnimationPropertyKeys.POSITION_Y to 0f,
            AnimationPropertyKeys.SCALE_X to 1f,
            AnimationPropertyKeys.SCALE_Y to 1f,
            AnimationPropertyKeys.ROTATION to 0f,
            AnimationPropertyKeys.OPACITY to 1f,
            AnimationPropertyKeys.ANCHOR_X to 0.5f,
            AnimationPropertyKeys.ANCHOR_Y to 0.5f
        )
    )

    @Test
    fun factory_constructs_effect_on_device() {
        val effect = AnimationEffectFactory.create(0L, 1_000_000L, params())
        assertTrue(effect is TransformAnimationGlEffect)
    }

    @Test
    fun transform_math_runs_on_device() {
        // Outside-window identity path.
        val outside = TransformMath.resolveValues(2_000_000L, 0L, 1_000_000L, params())
        assertEquals(TransformMath.TransformValues.IDENTITY, outside)
        // Matrix upload payload is a valid 16-float column-major mat4.
        val m = TransformMath.composeMatrix(TransformMath.TransformValues(scaleX = 2f), aspect = 9f / 16f)
        assertEquals(16, m.toColumnMajor4x4().size)
    }

    @Test
    fun shader_program_compiles_on_device_gpu() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue("no EGL display", display != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        assertTrue("eglInitialize failed", EGL14.eglInitialize(display, version, 0, version, 1))

        val cfgAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfig = IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display, cfgAttribs, 0, configs, 0, 1, numConfig, 0))
        assertTrue("no EGL config", numConfig[0] > 0)

        val context = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        assertTrue("no EGL context", context != EGL14.EGL_NO_CONTEXT)
        val surface = EGL14.eglCreatePbufferSurface(
            display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0
        )
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))

        try {
            val effect = TransformAnimationGlEffect(0L, 1_000_000L, params())
            // Compiles + links the vertex/fragment shaders on the device GPU and sets up the
            // uMatrix / uOpacity uniforms; throws VideoFrameProcessingException on GL failure.
            val program = effect.toGlShaderProgram(ApplicationProvider.getApplicationContext(), false)
            assertNotNull(program)
            val size = program.configure(720, 1280)
            assertEquals(720, size.width)
            assertEquals(1280, size.height)
            program.release()
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
