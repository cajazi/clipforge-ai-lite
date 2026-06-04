package com.clipforge.ai.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.core.utils.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    onTimeline: () -> Unit,
    onTransitions: () -> Unit,
    onOverlays: () -> Unit,
    onTextOverlay: () -> Unit,
    onMusic: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = AppColors.OnBackground)
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = AppSpacing.sm)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.SurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("AI UHD ▾", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = AppSpacing.sm)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF17C8E8))
                            .clickable(onClick = onExport)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Export", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // ── PREVIEW AREA ──────────────────────────────────────────────
            PreviewContainer(
                modifier          = Modifier.fillMaxWidth().weight(1f),
                thumbnailUri      = uiState.firstAssetUri,
                durationMs        = uiState.totalDurationMs,
                aspectRatioLabel  = uiState.aspectRatioLabel,
                isPlaying         = uiState.isPlaying,
                onPlayPause       = viewModel::togglePlayback,
                onTapPreview      = onPreview
            )

            EditorTimelineStrip(
                tracks = uiState.tracks,
                selectedClipId = uiState.selectedClipId,
                onSelectClip = viewModel::selectClip
            )

            // ── TOOLBAR ───────────────────────────────────────────────────
            Surface(
                modifier    = Modifier.fillMaxWidth(),
                color       = Color(0xFF202020),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm)) {
                    val showingSecondaryToolbar = uiState.activeMode != EditorMode.NONE &&
                        uiState.secondaryToolbarItems.isNotEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showingSecondaryToolbar) {
                            ToolbarBackButton(onClick = viewModel::returnToTimeline)
                        }
                        EditorToolbar(
                            items = if (showingSecondaryToolbar) {
                                uiState.secondaryToolbarItems
                            } else {
                                uiState.mainToolbarItems
                            },
                            selectedMode = uiState.activeMode,
                            selectedAction = if (showingSecondaryToolbar) uiState.activeAction else null,
                            onItemClick = viewModel::selectToolbarItem
                        )
                    }
                    val activeAction = uiState.activeAction
                    if (activeAction != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${activeAction.name.lowercase().replaceFirstChar { it.uppercase() }} selected",
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::returnToTimeline) {
                                Text("Cancel", color = AppColors.TextSecondary)
                            }
                            Button(
                                onClick = viewModel::applyActiveAction,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                Text("Apply", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    items: List<EditorToolbarItem>,
    selectedMode: EditorMode,
    selectedAction: EditAction?,
    onItemClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val selected = item.mode == selectedMode && selectedMode != EditorMode.NONE ||
                item.action == selectedAction && selectedAction != null
            ToolbarButton(
                item = item,
                selected = selected,
                onClick = { onItemClick(item.id) }
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    item: EditorToolbarItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        selected -> Color(0xFF303030)
        item.enabled -> Color.Transparent
        else -> Color.Transparent
    }
    val foreground = when {
        selected -> Color.White
        item.enabled -> AppColors.OnBackground
        else -> AppColors.TextSecondary.copy(alpha = 0.45f)
    }
    Column(
        modifier = Modifier
            .width(78.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable(enabled = item.enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
    Box(contentAlignment = Alignment.TopEnd) {
        item.badge?.let { badge ->
            Text(
                badge,
                color = Color.Black,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF18D8EA))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            item.icon,
            fontSize = 22.sp,
            color = foreground,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(5.dp))
        Text(
            item.label,
            fontSize = 11.sp,
            color = foreground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 12.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
    }
}

@Composable
private fun ToolbarBackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp, end = 4.dp)
            .width(44.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF323232))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back to timeline", tint = Color.White)
    }
}

@Composable
private fun EditorTimelineStrip(
    tracks: List<EditorTrackState>,
    selectedClipId: String?,
    onSelectClip: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(202.dp)
            .background(Color(0xFF111111))
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TimelineRuler()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 84.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                tracks.filter { it.isVisible }.forEach { track ->
                    TrackLane(
                        track = track,
                        selectedClipId = selectedClipId,
                        onSelectClip = onSelectClip
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun TimelineRuler() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("00:00 / 01:15", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("00:00", color = AppColors.TextSecondary, fontSize = 11.sp)
        Text("00:02", color = AppColors.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun TrackLane(
    track: EditorTrackState,
    selectedClipId: String?,
    onSelectClip: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            track.label,
            color = AppColors.TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.width(82.dp).padding(start = AppSpacing.sm),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(end = AppSpacing.md)
        ) {
            if (track.clips.isEmpty()) {
                item {
                    EmptyTrackSlot(track.addLabel ?: "+ Add")
                }
            } else {
                items(track.clips, key = { it.id }) { clip ->
                    TimelineClipBlock(
                        clip = clip,
                        selected = selectedClipId == clip.id,
                        onClick = { onSelectClip(clip.id) }
                    )
                }
                item { AddClipButton() }
            }
        }
    }
}

@Composable
private fun TimelineClipBlock(
    clip: EditorTimelineClipState,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(172.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(Color(0xFFB86A45))
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        clip.thumbnailUri?.let {
            AsyncImage(
                model = it,
                contentDescription = clip.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp))
            )
        }
        Text(
            clip.durationLabel ?: clip.label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun EmptyTrackSlot(label: String) {
    Box(
        modifier = Modifier
            .width(172.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF242424))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun AddClipButton() {
    Box(
        modifier = Modifier
            .width(42.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text("+", color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

// ── PREVIEW CONTAINER ─────────────────────────────────────────────────────────
@Composable
fun PreviewContainer(
    modifier: Modifier         = Modifier,
    thumbnailUri: String?      = null,
    durationMs: Long           = 0L,
    aspectRatioLabel: String   = "9:16",
    isPlaying: Boolean         = false,
    onPlayPause: () -> Unit    = {},
    onTapPreview: () -> Unit   = {}
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(onClick = onTapPreview),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailUri != null) {
            // ── HAS ASSETS — show thumbnail ───────────────────────────────
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUri)
                    .crossfade(true)
                    .build(),
                contentDescription  = "Preview thumbnail",
                contentScale        = ContentScale.Fit,
                modifier            = Modifier.fillMaxSize()
            )

            // Dark gradient overlay at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.35f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )

            // Aspect ratio badge — top left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(AppSpacing.sm)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    aspectRatioLabel,
                    color      = Color.White,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Duration badge — top right
            if (durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(AppSpacing.sm)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        TimeFormatter.formatMs(durationMs),
                        color      = Color.White,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Play / Pause button — center
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector         = Icons.Default.PlayArrow,
                    contentDescription  = if (isPlaying) "Pause" else "Play",
                    tint                = Color.White,
                    modifier            = Modifier.size(36.dp)
                )
            }

            // "Tap to full preview" hint — bottom center
            Text(
                "Tap for full preview",
                color    = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppSpacing.md)
            )

        } else {
            // ── NO ASSETS — empty state ───────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Text("\uD83C\uDFAC", fontSize = 52.sp)
                Text(
                    "Video Preview",
                    color      = AppColors.TextSecondary,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Add media to begin editing",
                    color    = AppColors.TextSecondary.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
