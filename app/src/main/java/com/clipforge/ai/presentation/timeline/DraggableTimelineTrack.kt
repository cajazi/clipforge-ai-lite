package com.clipforge.ai.presentation.timeline

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.utils.TimeFormatter
import com.clipforge.ai.domain.model.MediaAsset
import com.clipforge.ai.domain.model.MediaType
import com.clipforge.ai.domain.model.TimelineItem

private val CLIP_WIDTH  = 88.dp
private val CLIP_HEIGHT = 90.dp
private val CLIP_GAP    = 6.dp

@Composable
fun DraggableTimelineTrack(
    clips: List<TimelineItem>,
    mediaAssets: Map<String, MediaAsset> = emptyMap(),
    selectedClipId: String?,
    isLoading: Boolean,
    onSelectClip: (String?) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipWidthPx = with(LocalDensity.current) { (CLIP_WIDTH + CLIP_GAP).toPx() }

    // Drag state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX  by remember { mutableStateOf(0f) }
    var targetIndex  by remember { mutableStateOf<Int?>(null) }

    // Optimistic display list — mirrors Room but allows instant visual reorder
    val displayClips = remember(clips) { mutableStateListOf<TimelineItem>().also { it.addAll(clips) } }
    LaunchedEffect(clips) {
        if (draggedIndex == null) {
            displayClips.clear()
            displayClips.addAll(clips)
        }
    }

    Column(modifier = modifier.background(Color(0xFF080810))) {

        // ── Track header ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.width(3.dp).height(14.dp)
                    .clip(RoundedCornerShape(2.dp)).background(AppColors.TimelineClip))
                Text("VIDEO TRACK", fontSize = 10.sp, color = AppColors.TextSecondary,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            when {
                isLoading              -> Text("Loading...", fontSize = 10.sp, color = AppColors.TextSecondary)
                draggedIndex != null   -> Text("Release to drop", fontSize = 10.sp, color = AppColors.Primary)
                displayClips.isEmpty() -> Text("Import media to begin", fontSize = 10.sp, color = AppColors.TextSecondary)
                else                   -> Text("${displayClips.size} clip${if (displayClips.size != 1) "s" else ""}  •  long press to reorder",
                    fontSize = 10.sp, color = AppColors.TextSecondary)
            }
        }

        // ── Scrollable track ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CLIP_HEIGHT + 20.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(CLIP_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.width(200.dp).fillMaxHeight(),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(28.dp))
                    }
                }
                displayClips.isEmpty() -> {
                    Box(
                        modifier = Modifier.width(220.dp).fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp)).background(AppColors.TimelineTrack)
                            .border(1.dp, AppColors.TimelineClip.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("\uD83D\uDCF9", fontSize = 24.sp)
                            Text("No clips in timeline", color = AppColors.TextSecondary,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("Import media to begin",
                                color = AppColors.TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                    }
                }
                else -> {
                    displayClips.forEachIndexed { index, clip ->
                        val isDragged    = draggedIndex == index
                        val isDropTarget = targetIndex == index && draggedIndex != null && draggedIndex != index
                        val elevation by animateDpAsState(if (isDragged) 16.dp else 0.dp, label = "elev")

                        DraggableClipCard(
                            clip         = clip,
                            mediaAsset   = mediaAssets[clip.mediaAssetId],
                            isSelected   = clip.id == selectedClipId && !isDragged,
                            isDragged    = isDragged,
                            isDropTarget = isDropTarget,
                            dragOffsetX  = if (isDragged) dragOffsetX else 0f,
                            elevation    = elevation,
                            modifier     = Modifier.pointerInput(clip.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffsetX  = 0f
                                        targetIndex  = index
                                    },
                                    onDragEnd = {
                                        val from = draggedIndex
                                        val to   = targetIndex
                                        if (from != null && to != null && from != to) {
                                            val item = displayClips.removeAt(from)
                                            displayClips.add(to.coerceIn(0, displayClips.size), item)
                                            onReorder(from, to)
                                        }
                                        draggedIndex = null
                                        dragOffsetX  = 0f
                                        targetIndex  = null
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffsetX  = 0f
                                        targetIndex  = null
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX += dragAmount.x
                                        val rawHover = (index * clipWidthPx + dragOffsetX) / clipWidthPx
                                        targetIndex = rawHover.toInt().coerceIn(0, displayClips.size - 1)
                                    }
                                )
                            },
                            onClick = { if (!isDragged) onSelectClip(clip.id) }
                        )
                    }

                    // Add more
                    Box(
                        modifier = Modifier.size(width = 52.dp, height = CLIP_HEIGHT)
                            .clip(RoundedCornerShape(8.dp)).background(AppColors.TimelineTrack)
                            .border(1.dp, AppColors.SurfaceVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("+", color = AppColors.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Add", color = AppColors.TextSecondary, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── DRAGGABLE CLIP CARD ───────────────────────────────────────────────────────
@Composable
private fun DraggableClipCard(
    clip: TimelineItem,
    mediaAsset: MediaAsset?,
    isSelected: Boolean,
    isDragged: Boolean,
    isDropTarget: Boolean,
    dragOffsetX: Float,
    elevation: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val mediaType = mediaAsset?.mediaType ?: MediaType.VIDEO
    val thumbnailUri = mediaAsset?.localUri
    val durationMs = (clip.endMs - clip.startMs).coerceAtLeast(0L)
    val label = "Clip ${clip.orderIndex + 1}"

    val typeLabel = when (mediaType) {
        MediaType.VIDEO, MediaType.OVERLAY_VIDEO -> "VIDEO"
        MediaType.AUDIO                          -> "AUDIO"
        else                                     -> "IMG"
    }
    val typeColor = when (mediaType) {
        MediaType.VIDEO, MediaType.OVERLAY_VIDEO -> AppColors.Primary
        MediaType.AUDIO                          -> AppColors.Accent
        else                                     -> AppColors.Secondary
    }

    Box(
        modifier = modifier
            .width(CLIP_WIDTH)
            .height(CLIP_HEIGHT)
            .zIndex(if (isDragged) 10f else 1f)
            .graphicsLayer {
                translationX    = if (isDragged) dragOffsetX else 0f
                scaleX          = if (isDragged) 1.07f else 1f
                scaleY          = if (isDragged) 1.07f else 1f
                shadowElevation = elevation.toPx()
            }
            .shadow(elevation, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .then(
                when {
                    isDragged    -> Modifier.border(2.dp, AppColors.Primary, RoundedCornerShape(10.dp))
                    isDropTarget -> Modifier.border(2.dp, AppColors.Primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    isSelected   -> Modifier.border(2.dp, AppColors.Primary, RoundedCornerShape(10.dp))
                    else         -> Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                }
            )
            .clickable(enabled = !isDragged, onClick = onClick)
    ) {
        // Thumbnail / fill
        if (thumbnailUri != null && mediaType != MediaType.AUDIO) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUri).crossfade(false).build(),
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
        } else if (mediaType == MediaType.AUDIO) {
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(AppColors.Accent.copy(alpha = 0.6f),
                    AppColors.Accent.copy(alpha = 0.25f)))),
                contentAlignment = Alignment.Center) {
                Text("\uD83C\uDFB5", fontSize = 26.sp)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(typeColor.copy(alpha = 0.7f), typeColor.copy(alpha = 0.3f)))))
        }

        // Drag handle (visible while dragging)
        if (isDragged) {
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp)
                .clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.75f))
                .padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text("\u2630", fontSize = 9.sp, color = Color.Black)
            }
        }

        // Selected / drag top highlight
        if (isSelected || isDragged) {
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(
                AppColors.Primary, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)))
        }

        // Type badge — top left
        Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
            .clip(RoundedCornerShape(4.dp)).background(typeColor.copy(alpha = 0.85f))
            .padding(horizontal = 5.dp, vertical = 2.dp)) {
            Text(typeLabel, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Black)
        }

        // Drop target indicator
        if (isDropTarget) {
            Box(modifier = Modifier.align(Alignment.CenterStart).width(3.dp).fillMaxHeight()
                .background(AppColors.Primary, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)))
        }

        // Order number — center
        Box(modifier = Modifier.align(Alignment.Center).size(26.dp).clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Text("${clip.orderIndex + 1}", fontSize = 11.sp,
                color = Color.White, fontWeight = FontWeight.Black)
        }

        // Label + duration — bottom
        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(horizontal = 5.dp, vertical = 4.dp)) {
            Text(label, fontSize = 9.sp, color = Color.White,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(TimeFormatter.formatMs(durationMs), fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.75f))
        }
    }
}
