@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.presentation.effects

import android.content.Context
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import com.clipforge.ai.core.effects.EffectCategory
import com.clipforge.ai.core.effects.EffectDescriptor
import com.clipforge.ai.core.effects.EffectFactory
import com.clipforge.ai.core.effects.EffectRegistration
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ParamSpec
import com.clipforge.ai.domain.history.DeleteEffectCommand
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.repository.EffectRepository
import com.clipforge.ai.domain.selection.SelectionController
import com.clipforge.ai.domain.selection.SelectionTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectActionBarModelTest {
    @Test
    fun `effect action bar is hidden for none selection`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1")),
            selectionTarget = SelectionTarget.None
        )

        assertFalse(state.visible)
        assertNull(state.selectedEffectId)
        assertEquals(emptyList<EffectAction>(), state.actions)
    }

    @Test
    fun `effect action bar is hidden for clip selection`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1")),
            selectionTarget = SelectionTarget.Clip("clip-1")
        )

        assertFalse(state.visible)
    }

    @Test
    fun `effect action bar is visible for selected persisted effect`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1", effectId = "vhs")),
            selectionTarget = SelectionTarget.Effect("effect-1")
        )

        assertTrue(state.visible)
        assertEquals("effect-1", state.selectedEffectId)
        assertEquals("Vhs", state.label)
        assertEquals(listOf(EffectAction.Delete), state.actions)
        assertEquals(emptyList<EffectParamSliderState>(), state.sliders)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun `effect action bar exposes undo redo availability`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1", effectId = "vhs")),
            selectionTarget = SelectionTarget.Effect("effect-1"),
            canUndo = true,
            canRedo = true
        )

        assertTrue(state.visible)
        assertTrue(state.canUndo)
        assertTrue(state.canRedo)
    }

    @Test
    fun `effect action bar is hidden when selected effect no longer exists`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1")),
            selectionTarget = SelectionTarget.Effect("missing")
        )

        assertFalse(state.visible)
    }

    @Test
    fun `effect action bar uses descriptor label when registry is provided`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1", effectId = "brightness")),
            selectionTarget = SelectionTarget.Effect("effect-1"),
            registry = registryWith(descriptor("brightness", "Brightness"))
        )

        assertTrue(state.visible)
        assertEquals("Brightness", state.label)
    }

    @Test
    fun `effect action bar sliders are derived from param specs`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1", effectId = "brightness")),
            selectionTarget = SelectionTarget.Effect("effect-1"),
            registry = registryWith(
                descriptor(
                    id = "brightness",
                    displayName = "Brightness",
                    specs = listOf(
                        ParamSpec("amount", "Amount", -1f, 1f, 0.25f),
                        ParamSpec("mix", "Mix", 0f, 1f, 0.75f)
                    )
                )
            )
        )

        assertEquals(
            listOf(
                EffectParamSliderState("amount", "Amount", -1f, 1f, 0.25f),
                EffectParamSliderState("mix", "Mix", 0f, 1f, 0.75f)
            ),
            state.sliders
        )
    }

    @Test
    fun `effect action bar persisted param values override defaults`() {
        val state = buildEffectActionBarState(
            effects = listOf(
                effect(
                    id = "effect-1",
                    effectId = "brightness",
                    params = mapOf("amount" to EffectParamValue.Constant(0.6f))
                )
            ),
            selectionTarget = SelectionTarget.Effect("effect-1"),
            registry = registryWith(
                descriptor(
                    id = "brightness",
                    displayName = "Brightness",
                    specs = listOf(ParamSpec("amount", "Amount", -1f, 1f, 0.25f))
                )
            )
        )

        assertEquals(0.6f, state.sliders.single().value, 0f)
    }

    @Test
    fun `effect action bar missing param values use defaults`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1", effectId = "brightness")),
            selectionTarget = SelectionTarget.Effect("effect-1"),
            registry = registryWith(
                descriptor(
                    id = "brightness",
                    displayName = "Brightness",
                    specs = listOf(ParamSpec("amount", "Amount", -1f, 1f, 0.25f))
                )
            )
        )

        assertEquals(0.25f, state.sliders.single().value, 0f)
    }

    @Test
    fun `effect and clip action visibility remains mutually exclusive`() {
        val effects = listOf(effect("effect-1"))
        val controller = SelectionController()

        controller.selectEffect("effect-1")
        assertTrue(buildEffectActionBarState(effects, controller.current).visible)

        controller.selectClip("clip-1")
        assertEquals(SelectionTarget.Clip("clip-1"), controller.current)
        assertFalse(buildEffectActionBarState(effects, controller.current).visible)

        controller.selectEffect("effect-1")
        assertEquals(SelectionTarget.Effect("effect-1"), controller.current)
        assertTrue(buildEffectActionBarState(effects, controller.current).visible)
    }

    @Test
    fun `delete command invokes delete and clears selection`() = runBlocking {
        val repository = FakeEffectRepository(listOf(effect("effect-1")))
        val controller = SelectionController(SelectionTarget.Effect("effect-1"))

        DeleteEffectCommand(
            repository = repository,
            effect = effect("effect-1"),
            selectionController = controller
        ).execute()

        assertEquals(emptyList<EffectItem>(), repository.getEffectsForProject("project"))
        assertEquals(SelectionTarget.None, controller.current)
    }

    @Test
    fun `delete command preserves clip selection when effect was not selected`() = runBlocking {
        val repository = FakeEffectRepository(listOf(effect("effect-1")))
        val controller = SelectionController(SelectionTarget.Clip("clip-1"))

        DeleteEffectCommand(
            repository = repository,
            effect = effect("effect-1"),
            selectionController = controller
        ).execute()

        assertEquals(emptyList<EffectItem>(), repository.getEffectsForProject("project"))
        assertEquals(SelectionTarget.Clip("clip-1"), controller.current)
    }

    private fun effect(
        id: String,
        effectId: String = "blur",
        params: Map<String, EffectParamValue> = emptyMap()
    ) = EffectItem(
        id = id,
        projectId = "project",
        effectId = effectId,
        scope = EffectScope.GLOBAL,
        startMs = 0L,
        endMs = 1_000L,
        zOrder = 0,
        params = params
    )

    private fun registryWith(vararg descriptors: EffectDescriptor): EffectRegistry =
        EffectRegistry().apply {
            descriptors.forEach { descriptor ->
                register(
                    EffectRegistration(
                        descriptor = descriptor,
                        factory = EffectFactory { _, _, _ -> FakeGlEffect }
                    )
                )
            }
        }

    private fun descriptor(
        id: String,
        displayName: String,
        specs: List<ParamSpec> = emptyList()
    ) = EffectDescriptor(
        id = id,
        displayName = displayName,
        category = EffectCategory.TRENDY,
        paramSpecs = specs
    )

    private class FakeEffectRepository(initial: List<EffectItem>) : EffectRepository {
        private val effects = MutableStateFlow(initial)

        override suspend fun getEffectsForProject(projectId: String): List<EffectItem> =
            effects.value.filter { it.projectId == projectId }

        override fun observeEffectsForProject(projectId: String): Flow<List<EffectItem>> = effects

        override suspend fun upsertEffect(effect: EffectItem) {
            effects.value = effects.value.filterNot { it.id == effect.id } + effect
        }

        override suspend fun deleteEffect(id: String) {
            effects.value = effects.value.filterNot { it.id == id }
        }

        override suspend fun deleteEffectsForProject(projectId: String) {
            effects.value = effects.value.filterNot { it.projectId == projectId }
        }
    }

    private object FakeGlEffect : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram {
            error("FakeGlEffect is never rendered in JVM tests")
        }
    }
}
