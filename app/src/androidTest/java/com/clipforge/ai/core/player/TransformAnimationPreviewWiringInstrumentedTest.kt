@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.player

import android.content.Context
import androidx.media3.effect.GlEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clipforge.ai.RequiresRealGpu
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ParamProvider
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.core.gl.TransformAnimationGlEffect
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresRealGpu
class TransformAnimationPreviewWiringInstrumentedTest {
    private lateinit var player: ExoPlayer
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player = ExoPlayer.Builder(context).build()
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        scope.cancel()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player.release()
        }
    }

    @Test
    fun registeredGlobalTransformAnimationEmitsGlEffectAndKeepsLiveParamsConnected() = runBlocking {
        val repository = FakeRepository()
        val applied = mutableListOf<List<GlEffect>>()
        val controller = controller(repository, registeredRegistry(), applied)

        controller.bind("project")
        repository.emit(listOf(transformItem()))
        delay(350L)

        assertEquals(1, applied.size)
        val effect = applied.single().single()
        assertTrue(effect is TransformAnimationGlEffect)

        val provider = providerFrom(effect as TransformAnimationGlEffect)
        assertEquals(1f, provider.valueAt(AnimationPropertyKeys.OPACITY, 0L), 0f)

        controller.setParam("transform-item", AnimationPropertyKeys.OPACITY, 0.25f)

        assertEquals("Live param update must not rebuild the effect stack", 1, applied.size)
        assertEquals(0.25f, provider.valueAt(AnimationPropertyKeys.OPACITY, 0L), 0f)
        controller.release()
    }

    @Test
    fun unregisteredTransformAnimationIsSkipped() = runBlocking {
        val repository = FakeRepository()
        val applied = mutableListOf<List<GlEffect>>()
        val controller = controller(repository, EffectRegistry(), applied)

        controller.bind("project")
        repository.emit(listOf(transformItem()))
        delay(350L)

        assertTrue(applied.isEmpty())
        controller.release()
    }

    @Test
    fun clipScopedTransformAnimationIsApplied() = runBlocking {
        val repository = FakeRepository()
        val applied = mutableListOf<List<GlEffect>>()
        val controller = controller(repository, registeredRegistry(), applied)

        controller.bind("project")
        repository.emit(listOf(transformItem(scope = EffectScope.CLIP)))
        delay(350L)

        assertEquals(1, applied.size)
        assertTrue(applied.single().single() is TransformAnimationGlEffect)
        controller.release()
    }

    private fun controller(
        repository: FakeRepository,
        registry: EffectRegistry,
        applied: MutableList<List<GlEffect>>
    ) = EffectPreviewController(
        player = player,
        repository = repository,
        scope = scope,
        registry = registry,
        logger = {},
        videoEffectsApplier = { effects -> applied += effects }
    )

    private fun registeredRegistry() = EffectRegistry().apply {
        registerTransformAnimationEffect()
        registerTransformAnimationEffect()
    }

    private fun transformItem(scope: EffectScope = EffectScope.GLOBAL) = EffectItem(
        id = "transform-item",
        projectId = "project",
        effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
        scope = scope,
        startMs = 0L,
        endMs = 1_000L,
        zOrder = 0,
        params = mapOf(
            AnimationPropertyKeys.POSITION_X to EffectParamValue.Constant(0.1f),
            AnimationPropertyKeys.POSITION_Y to EffectParamValue.Constant(-0.1f),
            AnimationPropertyKeys.SCALE_X to EffectParamValue.Constant(0.5f),
            AnimationPropertyKeys.SCALE_Y to EffectParamValue.Constant(1f),
            AnimationPropertyKeys.ROTATION to EffectParamValue.Constant(15f),
            AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(1f),
            AnimationPropertyKeys.ANCHOR_X to EffectParamValue.Constant(0.5f),
            AnimationPropertyKeys.ANCHOR_Y to EffectParamValue.Constant(0.5f)
        )
    )

    private fun providerFrom(effect: TransformAnimationGlEffect): ParamProvider {
        val field = TransformAnimationGlEffect::class.java.getDeclaredField("params")
        field.isAccessible = true
        return field.get(effect) as ParamProvider
    }

    private class FakeRepository(initial: List<EffectItem> = emptyList()) : EffectRepository {
        private val flow = MutableStateFlow(initial)

        fun emit(effects: List<EffectItem>) {
            flow.value = effects
        }

        override suspend fun getEffectsForProject(projectId: String): List<EffectItem> = flow.value
        override fun observeEffectsForProject(projectId: String): Flow<List<EffectItem>> = flow
        override suspend fun upsertEffect(effect: EffectItem) {
            flow.value = flow.value.filterNot { it.id == effect.id } + effect
        }
        override suspend fun deleteEffect(id: String) {
            flow.value = flow.value.filterNot { it.id == id }
        }
        override suspend fun deleteEffectsForProject(projectId: String) {
            flow.value = emptyList()
        }
    }
}
