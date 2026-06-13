package com.clipforge.ai.presentation.effects

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.selection.SelectionTarget
import kotlin.math.roundToInt

@Composable
fun TimelineEffectLane(
    effects: List<EffectItem>,
    selectionTarget: SelectionTarget,
    pxPerMs: Float,
    scrollState: ScrollState,
    playheadTrackLead: Dp,
    playheadTrackTrail: Dp,
    onSelectEffect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = remember(effects, selectionTarget) {
        buildEffectChipUiModels(effects, selectionTarget)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(EFFECT_LANE_HEIGHT)
            .padding(end = 8.dp)
            .background(Color(0xFF20202A))
            .horizontalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.height(EFFECT_LANE_HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(playheadTrackLead))
            if (chips.isEmpty()) {
                EmptyEffectLane()
            } else {
                EffectChipStrip(
                    chips = chips,
                    pxPerMs = pxPerMs,
                    onSelectEffect = onSelectEffect
                )
            }
            Spacer(Modifier.width(playheadTrackTrail))
        }
    }
}

@Composable
private fun EmptyEffectLane() {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A2A34))
    )
}

@Composable
private fun EffectChipStrip(
    chips: List<EffectChipUiModel>,
    pxPerMs: Float,
    onSelectEffect: (String) -> Unit
) {
    val density = LocalDensity.current
    Box {
        chips.forEach { chip ->
            val widthPx = ((chip.durationMs * pxPerMs).roundToInt()).coerceAtLeast(MIN_EFFECT_CHIP_WIDTH_PX)
            EffectChip(
                chip = chip,
                width = with(density) { widthPx.toDp() },
                modifier = Modifier.offset {
                    IntOffset(
                        x = (chip.startMs * pxPerMs).roundToInt(),
                        y = 0
                    )
                },
                onClick = { onSelectEffect(chip.id) }
            )
        }
        val stripWidth = chips.maxOfOrNull { ((it.endMs * pxPerMs).roundToInt()).coerceAtLeast(MIN_EFFECT_CHIP_WIDTH_PX) } ?: 0
        Spacer(Modifier.width(with(density) { stripWidth.toDp() }).height(EFFECT_LANE_HEIGHT))
    }
}

@Composable
private fun EffectChip(
    chip: EffectChipUiModel,
    width: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (chip.isSelected) AppColors.Warning else Color(0xFF56566B)
    val fillColor = if (chip.isSelected) Color(0xFF3A3150) else Color(0xFF273640)
    Box(
        modifier = modifier
            .width(width)
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fillColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = chip.label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val EFFECT_LANE_HEIGHT = 32.dp
private const val MIN_EFFECT_CHIP_WIDTH_PX = 28
