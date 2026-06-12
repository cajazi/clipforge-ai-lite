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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
class EffectPreviewControllerInstrumentedTest {
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
    fun empty_fast_path_does_not_apply_effects() = runBlocking {
        val calls = mutableListOf<Int>()
        val controller = controller(FakeRepository(), calls = calls)

        controller.bind("project")
        kotlinx.coroutines.delay(350L)

        assertTrue(calls.isEmpty())
        controller.release()
    }

    @Test
    fun suspend_resume_is_depth_counted_and_resume_applies_immediately() = runBlocking {
        val repository = FakeRepository(listOf(item()))
        val calls = mutableListOf<Int>()
        val controller = controller(repository, calls = calls)

        controller.bind("project")
        kotlinx.coroutines.delay(350L)
        controller.suspendEffects()
        controller.suspendEffects()
        controller.resumeEffects()
        controller.resumeEffects()

        assertEquals(listOf(1, 0, 1), calls)
        controller.release()
    }

    @Test
    fun debounce_coalesces_structural_changes() = runBlocking {
        val repository = FakeRepository()
        val calls = mutableListOf<Int>()
        val controller = controller(repository, calls = calls)

        controller.bind("project")
        repository.emit(listOf(item(id = "a")))
        kotlinx.coroutines.delay(100L)
        repository.emit(listOf(item(id = "b")))
        kotlinx.coroutines.delay(350L)

        assertEquals(listOf(1), calls)
        controller.release()
    }

    @Test
    fun slider_update_is_visible_without_reapplying_effect_stack() = runBlocking {
        val repository = FakeRepository(listOf(item(params = mapOf("intensity" to EffectParamValue.Constant(0.2f)))))
        val calls = mutableListOf<Int>()
        var capturedProvider: ParamProvider? = null
        val registry = registry { provider -> capturedProvider = provider }
        val controller = controller(repository, registry, calls)

        controller.bind("project")
        kotlinx.coroutines.delay(350L)
        controller.setParam("effect-item", "intensity", 0.9f)

        assertEquals(listOf(1), calls)
        assertEquals(0.9f, capturedProvider!!.valueAt("intensity", 0L), 0f)
        controller.release()
    }

    private fun controller(
        repository: FakeRepository,
        registry: EffectRegistry = registry(),
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
