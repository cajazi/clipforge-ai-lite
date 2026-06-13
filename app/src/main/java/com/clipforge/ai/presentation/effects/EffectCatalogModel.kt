package com.clipforge.ai.presentation.effects

import com.clipforge.ai.core.effects.EffectCategory
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectReleasePolicy

data class EffectCatalogState(
    val categories: List<EffectCatalogCategoryState>
) {
    val isEmpty: Boolean = categories.all { it.tiles.isEmpty() }

    companion object {
        val Empty = EffectCatalogState(emptyList())
    }
}

data class EffectCatalogCategoryState(
    val category: EffectCategory,
    val title: String,
    val tiles: List<EffectCatalogTileState>
)

data class EffectCatalogTileState(
    val effectId: String,
    val label: String,
    val category: EffectCategory,
    val isPremium: Boolean
)

fun buildEffectCatalogState(
    registry: EffectRegistry,
    releasePolicy: EffectReleasePolicy
): EffectCatalogState {
    val tiles = registry.all()
        .map { it.descriptor }
        .filter { releasePolicy.isReleased(it.id) }
        .sortedWith(compareBy({ it.category.order }, { it.displayName }, { it.id }))
        .map { descriptor ->
            EffectCatalogTileState(
                effectId = descriptor.id,
                label = descriptor.displayName,
                category = descriptor.category,
                isPremium = descriptor.isPremium
            )
        }

    if (tiles.isEmpty()) return EffectCatalogState.Empty

    val categories = EffectCategory.ordered.mapNotNull { category ->
        val categoryTiles = tiles.filter { it.category == category }
        if (categoryTiles.isEmpty()) {
            null
        } else {
            EffectCatalogCategoryState(
                category = category,
                title = category.displayName,
                tiles = categoryTiles
            )
        }
    }

    return EffectCatalogState(categories)
}
