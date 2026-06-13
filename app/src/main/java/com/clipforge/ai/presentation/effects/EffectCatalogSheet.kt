package com.clipforge.ai.presentation.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.effects.EffectCategory

const val EFFECT_CATALOG_SHEET_TAG = "effect_catalog_sheet"
const val EFFECT_CATALOG_EMPTY_TAG = "effect_catalog_empty"
const val EFFECT_CATALOG_TABS_TAG = "effect_catalog_tabs"
const val EFFECT_CATALOG_SELECTED_TAB_TAG = "effect_catalog_selected_tab"
const val EFFECT_CATALOG_PREMIUM_BADGE_TAG = "effect_catalog_premium_badge"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectCatalogSheet(
    visible: Boolean,
    state: EffectCatalogState,
    selectedCategory: EffectCategory?,
    onDismiss: () -> Unit,
    onCategorySelected: (EffectCategory) -> Unit,
    onTileClicked: (EffectCatalogTileState) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF17171F))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(EFFECT_CATALOG_SHEET_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Effects",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Close",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onDismiss)
            )
        }

        EffectCategoryTabs(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected
        )

        if (state.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .testTag(EFFECT_CATALOG_EMPTY_TAG),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No effects available",
                    color = AppColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            val tiles = state.categories
                .filter { selectedCategory == null || it.category == selectedCategory }
                .flatMap { it.tiles }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tiles.forEach { tile ->
                    EffectCatalogTile(
                        tile = tile,
                        onClick = { onTileClicked(tile) }
                    )
                }
            }
        }
    }
}

@Composable
fun EffectCategoryTabs(
    selectedCategory: EffectCategory?,
    onCategorySelected: (EffectCategory) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<EffectCategory> = EffectCategory.ordered
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag(EFFECT_CATALOG_TABS_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val selected = selectedCategory == category
            Text(
                text = category.displayName,
                color = if (selected) Color.White else AppColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Color(0xFF343447) else Color(0xFF20202A))
                    .border(
                        width = 1.dp,
                        color = if (selected) AppColors.Warning else Color(0xFF3A3A46),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onCategorySelected(category) }
                    .then(if (selected) Modifier.testTag(EFFECT_CATALOG_SELECTED_TAB_TAG) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun EffectCatalogTile(
    tile: EffectCatalogTileState,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 104.dp, max = 140.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF20202A))
            .border(1.dp, Color(0xFF3A3A46), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = tile.label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (tile.isPremium) {
            Text(
                text = "PRO",
                color = AppColors.Warning,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(EFFECT_CATALOG_PREMIUM_BADGE_TAG)
            )
        }
    }
}
