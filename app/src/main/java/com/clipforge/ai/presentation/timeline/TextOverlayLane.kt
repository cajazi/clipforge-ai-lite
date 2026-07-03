package com.clipforge.ai.presentation.timeline

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
import com.clipforge.ai.domain.model.TextOverlay
import com.clipforge.ai.domain.selection.SelectionTarget
import kotlin.math.roundToInt

@Composable
fun TextOverlayLane(
    overlays: List<TextOverlay>,
    selectedTextOverlayId: String?,
    pxPerMs: Float,
    scrollState: ScrollState,
    playheadTrackLead: Dp,
    playheadTrackTrail: Dp,
    onSelectTextOverlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = remember(overlays, selectedTextOverlayId) {
        buildTextOverlayChipUiModels(overlays, selectedTextOverlayId)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TEXT_LANE_HEIGHT)
            .padding(end = 8.dp)
            .background(Color(0xFF1E2028))
            .horizontalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.height(TEXT_LANE_HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(playheadTrackLead))
            if (chips.isEmpty()) {
                EmptyTextOverlayLane()
            } else {
                TextOverlayChipStrip(
                    chips = chips,
                    pxPerMs = pxPerMs,
                    onSelectTextOverlay = onSelectTextOverlay
                )
            }
            Spacer(Modifier.width(playheadTrackTrail))
        }
    }
}

data class TextOverlayChipUiModel(
    val id: String,
    val label: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val zIndex: Int,
    val isSelected: Boolean
)

data class TextOverlayChipPlacement(
    val offsetPx: Int,
    val widthPx: Int
)

fun buildTextOverlayChipUiModels(
    overlays: List<TextOverlay>,
    selectedTextOverlayId: String? = null
): List<TextOverlayChipUiModel> =
    overlays
        .sortedWith(compareBy<TextOverlay> { it.zIndex }.thenBy { it.windowStartMs }.thenBy { it.id })
        .map { overlay ->
            TextOverlayChipUiModel(
                id = overlay.id,
                label = overlay.renderSpec.text.ifBlank { "Text" },
                startMs = overlay.windowStartMs,
                endMs = overlay.windowEndMs,
                durationMs = (overlay.windowEndMs - overlay.windowStartMs).coerceAtLeast(1L),
                zIndex = overlay.zIndex,
                isSelected = overlay.id == selectedTextOverlayId
            )
        }

fun textOverlayChipPlacement(
    chip: TextOverlayChipUiModel,
    pxPerMs: Float
): TextOverlayChipPlacement =
    TextOverlayChipPlacement(
        offsetPx = (chip.startMs * pxPerMs).roundToInt(),
        widthPx = (chip.durationMs * pxPerMs).roundToInt().coerceAtLeast(MIN_TEXT_CHIP_WIDTH_PX)
    )

fun textOverlayChipSelectionId(chip: TextOverlayChipUiModel): String = chip.id

fun selectedTextOverlayId(selectionTarget: SelectionTarget): String? =
    selectionTarget.textOverlayId

fun shouldClearStaleSelectedTextOverlay(
    selectionTarget: SelectionTarget,
    overlays: List<TextOverlay>
): Boolean {
    val selectedId = selectedTextOverlayId(selectionTarget) ?: return false
    return overlays.none { it.id == selectedId }
}

@Composable
private fun EmptyTextOverlayLane() {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF292B34))
    )
}

@Composable
private fun TextOverlayChipStrip(
    chips: List<TextOverlayChipUiModel>,
    pxPerMs: Float,
    onSelectTextOverlay: (String) -> Unit
) {
    val density = LocalDensity.current
    Box {
        chips.forEach { chip ->
            val placement = textOverlayChipPlacement(chip, pxPerMs)
            TextOverlayChip(
                chip = chip,
                width = with(density) { placement.widthPx.toDp() },
                modifier = Modifier.offset {
                    IntOffset(
                        x = placement.offsetPx,
                        y = 0
                    )
                },
                onClick = { onSelectTextOverlay(textOverlayChipSelectionId(chip)) }
            )
        }
        val stripWidth = chips.maxOfOrNull {
            textOverlayChipPlacement(it, pxPerMs).let { placement -> placement.offsetPx + placement.widthPx }
        } ?: 0
        Spacer(Modifier.width(with(density) { stripWidth.toDp() }).height(TEXT_LANE_HEIGHT))
    }
}

@Composable
private fun TextOverlayChip(
    chip: TextOverlayChipUiModel,
    width: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (chip.isSelected) AppColors.Warning else Color(0xFF4D6D78)
    val fillColor = if (chip.isSelected) Color(0xFF3A3150) else Color(0xFF243942)
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

private val TEXT_LANE_HEIGHT = 32.dp
private const val MIN_TEXT_CHIP_WIDTH_PX = 28
