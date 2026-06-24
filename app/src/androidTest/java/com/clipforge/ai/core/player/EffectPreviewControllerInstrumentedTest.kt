@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.player

import android.content.Context
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clipforge.ai.core.effects.EffectCategory
import com.clipforge.ai.core.effects.EffectDescriptor
import com.clipforge.ai.core.effects.EffectFactory
import com.clipforge.ai.core.effects.EffectRegistration
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ParamProvider
import com.clipforge.ai.core.effects.ParamSpec
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives [EffectPreviewController] under a [kotlinx.coroutines.test.TestScope]'s virtual clock, so
 * the controller's `STRUCTURAL_DEBOUNCE_MS` debounce advances deterministically (`advanceUntilIdle`
 * / `advanceTimeBy`) instead of racing a real wall-clock delay across dispatchers — the source of
 * this test's historic emulator flakiness. It also exercises the superseded-apply generation guard:
 * a debounced structural apply that has been overtaken by a newer apply must never land.
 *
 * It remains an instrumented test only because the controller's constructor takes a real [ExoPlayer];
 * that player is never invoked here since a fake [videoEffectsApplier] replaces the default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EffectPreviewControllerInstrumentedTest {
    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player = ExoPlayer.Builder(context).build()
        }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player.release()
        }
    }

    @Test
    fun empty_fast_path_does_not_apply_effects() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val calls = mutableListOf<Int>()
        val controller = controller(FakeRepository(), scope = scope, calls = calls)

        controller.bind("project")
        advanceUntilIdle()

        assertTrue(calls.isEmpty())
        controller.release()
        scope.cancel()
    }

    @Test
    fun suspend_resume_is_depth_counted_and_resume_applies_immediately() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val repository = FakeRepository(listOf(item()))
        val calls = mutableListOf<Int>()
        val controller = controller(repository, scope = scope, calls = calls)

        controller.bind("project")
        advanceUntilIdle()
        controller.suspendEffects()
        controller.suspendEffects()
        controller.resumeEffects()
        controller.resumeEffects()

        assertEquals(listOf(1, 0, 1), calls)
        controller.release()
        scope.cancel()
    }

    @Test
    fun debounce_coalesces_structural_changes() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val repository = FakeRepository()
        val calls = mutableListOf<Int>()
        val controller = controller(repository, scope = scope, calls = calls)

        controller.bind("project")
        repository.emit(listOf(item(id = "a")))
        runCurrent()
        advanceTimeBy(100L)
        repository.emit(listOf(item(id = "b")))
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf(1), calls)
        controller.release()
        scope.cancel()
    }

    @Test
    fun immediate_apply_supersedes_pending_structural_debounce() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val repository = FakeRepository()
        val calls = mutableListOf<Int>()
        val controller = controller(repository, scope = scope, calls = calls)

        controller.bind("project")
        repository.emit(listOf(item(id = "a")))
        runCurrent()
        // A structural debounce for [a] is now pending. An immediate apply overtakes it before the
        // debounce delay elapses; the pending debounce must then drop instead of applying again.
        controller.beginAnimationDraft("clip-x")
        val afterImmediateApply = calls.toList()
        advanceUntilIdle()

        assertEquals(afterImmediateApply, calls)
        assertEquals(listOf(1), calls)
        controller.release()
        scope.cancel()
    }

    @Test
    fun slider_update_is_visible_without_reapplying_effect_stack() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val repository = FakeRepository(listOf(item(params = mapOf("intensity" to EffectParamValue.Constant(0.2f)))))
        val calls = mutableListOf<Int>()
        var capturedProvider: ParamProvider? = null
        val registry = registry { provider -> capturedProvider = provider }
        val controller = controller(repository, registry, scope = scope, calls = calls)

        controller.bind("project")
        advanceUntilIdle()
        controller.setParam("effect-item", "intensity", 0.9f)

        assertEquals(listOf(1), calls)
        assertEquals(0.9f, capturedProvider!!.valueAt("intensity", 0L), 0f)
        controller.release()
        scope.cancel()
    }

    private fun controller(
        repository: FakeRepository,
        registry: EffectRegistry = registry(),
        scope: CoroutineScope,
        calls: MutableList<Int>
    ) = EffectPreviewController(
        player = player,
        repository = repository,
        scope = scope,
        registry = registry,
        logger = {},
        videoEffectsApplier = { effects -> calls += effects.size }
    )

    private fun registry(onProvider: (ParamProvider) -> Unit = {}): EffectRegistry {
        val registry = EffectRegistry()
        registry.register(
            EffectRegistration(
                descriptor = EffectDescriptor(
                    id = "effect",
                    displayName = "Effect",
                    category = EffectCategory.TRENDY,
                    paramSpecs = listOf(ParamSpec("intensity", "Intensity", 0f, 1f, 0.5f))
                ),
                factory = EffectFactory { _, _, provider ->
                    onProvider(provider)
                    FakeGlEffect
                }
            )
        )
        return registry
    }

    private fun item(
        id: String = "effect-item",
        params: Map<String, EffectParamValue> = emptyMap()
    ) = EffectItem(
        id = id,
        projectId = "project",
        effectId = "effect",
        scope = EffectScope.GLOBAL,
        startMs = 0L,
        endMs = 1_000L,
        zOrder = 0,
        params = params
    )

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

    private object FakeGlEffect : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram {
            error("FakeGlEffect is never rendered")
        }
    }
}
