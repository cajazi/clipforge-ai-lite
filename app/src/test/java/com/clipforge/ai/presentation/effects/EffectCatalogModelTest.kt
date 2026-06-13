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
import com.clipforge.ai.core.effects.EffectReleasePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectCatalogModelTest {

    @Test
    fun `catalog is empty when release policy has no released ids`() {
        val registry = registryOf(
            descriptor("brightness", "Brightness", EffectCategory.TRENDY)
        )

        val state = buildEffectCatalogState(registry, EffectReleasePolicy())

        assertTrue(state.isEmpty)
        assertEquals(EffectCatalogState.Empty, state)
    }

    @Test
    fun `catalog includes released and registered effect`() {
        val registry = registryOf(
            descriptor("brightness", "Brightness", EffectCategory.TRENDY, isPremium = true)
        )

        val state = buildEffectCatalogState(
            registry = registry,
            releasePolicy = releasedPolicy("brightness")
        )

        assertEquals(1, state.categories.size)
        assertEquals(EffectCategory.TRENDY, state.categories.single().category)
        assertEquals("Trendy", state.categories.single().title)
        assertEquals(
            listOf(EffectCatalogTileState("brightness", "Brightness", EffectCategory.TRENDY, isPremium = true)),
            state.categories.single().tiles
        )
    }

    @Test
    fun `catalog excludes registered but unreleased effect`() {
        val registry = registryOf(
            descriptor("brightness", "Brightness", EffectCategory.TRENDY),
            descriptor("contrast", "Contrast", EffectCategory.TRENDY)
        )

        val state = buildEffectCatalogState(
            registry = registry,
            releasePolicy = releasedPolicy("brightness")
        )

        assertEquals(listOf("brightness"), state.categories.single().tiles.map { it.effectId })
    }

    @Test
    fun `catalog excludes released but unregistered id`() {
        val registry = registryOf(
            descriptor("brightness", "Brightness", EffectCategory.TRENDY)
        )

        val state = buildEffectCatalogState(
            registry = registry,
            releasePolicy = releasedPolicy("missing")
        )

        assertTrue(state.isEmpty)
    }

    @Test
    fun `catalog ordering is deterministic`() {
        val registry = registryOf(
            descriptor("grain", "Grain", EffectCategory.RETRO),
            descriptor("z-trendy", "Zoom", EffectCategory.TRENDY),
            descriptor("a-trendy", "Alpha", EffectCategory.TRENDY),
            descriptor("blur", "Blur", EffectCategory.BLUR)
        )

        val state = buildEffectCatalogState(
            registry = registry,
            releasePolicy = releasedPolicy("grain", "z-trendy", "a-trendy", "blur")
        )

        assertEquals(listOf(EffectCategory.TRENDY, EffectCategory.RETRO, EffectCategory.BLUR), state.categories.map { it.category })
        assertEquals(listOf("a-trendy", "z-trendy"), state.categories.first().tiles.map { it.effectId })
    }

    @Test
    fun `catalog groups by existing effect category`() {
        val registry = registryOf(
            descriptor("brightness", "Brightness", EffectCategory.TRENDY),
            descriptor("vhs", "VHS", EffectCategory.RETRO)
        )

        val state = buildEffectCatalogState(
            registry = registry,
            releasePolicy = releasedPolicy("brightness", "vhs")
        )

        assertEquals(listOf("Trendy", "Retro"), state.categories.map { it.title })
        assertEquals(listOf("brightness"), state.categories[0].tiles.map { it.effectId })
        assertEquals(listOf("vhs"), state.categories[1].tiles.map { it.effectId })
    }

    private fun registryOf(vararg descriptors: EffectDescriptor): EffectRegistry =
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
        category: EffectCategory,
        isPremium: Boolean = false
    ) = EffectDescriptor(
        id = id,
        displayName = displayName,
        category = category,
        paramSpecs = emptyList(),
        isPremium = isPremium
    )

    private fun releasedPolicy(vararg ids: String) =
        EffectReleasePolicy(exportReadyIds = ids.toSet(), releasedIds = ids.toSet())

    private object FakeGlEffect : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram {
            error("FakeGlEffect is never rendered in JVM tests")
        }
    }
}
