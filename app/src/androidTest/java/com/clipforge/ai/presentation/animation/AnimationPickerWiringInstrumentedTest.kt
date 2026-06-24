@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.presentation.animation

import android.content.Context
import androidx.media3.effect.GlEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clipforge.ai.RequiresRealGpu
import com.clipforge.ai.core.animation.AnimationPresetIds
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ParamProvider
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.core.gl.TransformAnimationGlEffect
import com.clipforge.ai.core.player.EffectPreviewController
import com.clipforge.ai.domain.history.HistoryRegistry
import com.clipforge.ai.domain.model.EffectItem
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
class AnimationPickerWiringInstrumentedTest {
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
    fun pickerApplyReplaceRemoveRefreshesPreviewEffects() = runBlocking {
        val repository = FakeRepository()
        val historyRegistry = HistoryRegistry()
        val picker = AnimationPickerViewModel(PROJECT_ID, repository, historyRegistry)
        val applied = mutableListOf<List<GlEffect>>()
        val controller = EffectPreviewController(
            player = player,
            repository = repository,
            scope = scope,
            registry = EffectRegistry().apply { registerTransformAnimationEffect() },
            logger = {},
            videoEffectsApplier = { effects -> applied += effects }
        )

        controller.bind(PROJECT_ID)
        picker.applyPreset(AnimationPresetIds.ZOOM_IN, totalDurationMs = 2_000L)
        delay(350L)

        assertEquals(listOf("anim-global-$PROJECT_ID"), transformRows(repository).map { it.id })
        val zoomEffect = applied.last().single()
        assertTrue(zoomEffect is TransformAnimationGlEffect)
        assertEquals(0.8f, providerFrom(zoomEffect as TransformAnimationGlEffect).valueAt(AnimationPropertyKeys.SCALE_X, 0L), 0f)

        picker.applyPreset(AnimationPresetIds.SLOW_ZOOM, totalDurationMs = 4_000L)
        delay(350L)

        assertEquals("Replace must keep exactly one transform_animation row", 1, transformRows(repository).size)
        val slowZoomEffect = applied.last().single()
        assertTrue(slowZoomEffect is TransformAnimationGlEffect)
        assertEquals(1.12f, providerFrom(slowZoomEffect as TransformAnimationGlEffect).valueAt(AnimationPropertyKeys.SCALE_X, 4_000_000L), 0f)

        picker.removeAnimation()
        delay(350L)

        assertTrue(transformRows(repository).isEmpty())
        assertTrue(applied.last().isEmpty())
        controller.release()
    }

    private suspend fun transformRows(repository: EffectRepository): List<EffectItem> =
        repository.getEffectsForProject(PROJECT_ID)
            .filter { it.effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION && it.scope == EffectScope.GLOBAL }

    private fun providerFrom(effect: TransformAnimationGlEffect): ParamProvider {
        val field = TransformAnimationGlEffect::class.java.getDeclaredField("params")
        field.isAccessible = true
        return field.get(effect) as ParamProvider
    }

    private class FakeRepository(initial: List<EffectItem> = emptyList()) : EffectRepository {
        private val flow = MutableStateFlow(initial)

        override suspend fun getEffectsForProject(projectId: String): List<EffectItem> =
            flow.value.filter { it.projectId == projectId }

        override fun observeEffectsForProject(projectId: String): Flow<List<EffectItem>> = flow

        override suspend fun upsertEffect(effect: EffectItem) {
            flow.value = flow.value.filterNot { it.id == effect.id } + effect
        }

        override suspend fun deleteEffect(id: String) {
            flow.value = flow.value.filterNot { it.id == id }
        }

        override suspend fun deleteEffectsForProject(projectId: String) {
            flow.value = flow.value.filterNot { it.projectId == projectId }
        }
    }

    private companion object {
        const val PROJECT_ID = "project"
    }
}
