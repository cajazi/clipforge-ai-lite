package com.clipforge.ai.presentation.upload

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.core.utils.FileSizeFormatter
import com.clipforge.ai.core.utils.TimeFormatter
import com.clipforge.ai.domain.model.MediaType

private const val TAG = "MediaImportScreen"

private enum class PickerCategory(
    val label: String, val emoji: String,
    val mediaType: MediaType, val isVisual: Boolean
) {
    VIDEO         ("Video",         "\uD83C\uDFAC", MediaType.VIDEO,         true),
    IMAGE         ("Image",         "\uD83D\uDDBC\uFE0F", MediaType.IMAGE,   true),
    AUDIO         ("Audio",         "\uD83C\uDFB5", MediaType.AUDIO,         false),
    LOGO          ("Logo",          "\uD83C\uDFA8", MediaType.LOGO,          true),
    OVERLAY_IMAGE ("Overlay Image", "\uD83D\uDDBC\uFE0F", MediaType.OVERLAY_IMAGE, true),
    OVERLAY_VIDEO ("Overlay Video", "\uD83C\uDFAC", MediaType.OVERLAY_VIDEO, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaImportScreen(
    projectId: String,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: MediaImportViewModel = viewModel()
) {
    val uiState         by viewModel.uiState.collectAsState()
    var activeCategory  by remember { mutableStateOf<PickerCategory?>(null) }

    val multiVisualPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) activeCategory?.let { viewModel.onMediaPicked(uris, it.mediaType) }
        activeCategory = null
    }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onAudioPicked(uris)
        activeCategory = null
    }

    LaunchedEffect(activeCategory) {
        val cat = activeCategory ?: return@LaunchedEffect
        if (!cat.isVisual) {
            audioPicker.launch(arrayOf("audio/*"))
        } else {
            val req = when (cat.mediaType) {
                MediaType.VIDEO, MediaType.OVERLAY_VIDEO ->
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                else ->
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            }
            multiVisualPicker.launch(req)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Import Media", color = AppColors.OnBackground,
                            fontWeight = FontWeight.SemiBold)
                        Text("${uiState.assets.size} asset(s) selected",
                            color = AppColors.TextSecondary, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = AppColors.OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(AppColors.Background)
                    .navigationBarsPadding().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
            ) {
                Button(
                    onClick  = {
                        Log.d(TAG, "MediaImport Continue clicked projectId=$projectId assets=${uiState.assets.size}")
                        viewModel.saveAndContinue(projectId, onContinue)
                    },
                    enabled  = uiState.canProceed && !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            if (uiState.canProceed && !uiState.isSaving)
                                Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryVariant))
                            else Brush.linearGradient(listOf(AppColors.SurfaceVariant, AppColors.SurfaceVariant)),
                            shape = RoundedCornerShape(14.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isSaving) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                    color = Color.White, strokeWidth = 2.dp)
                                Text("Saving...", fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp, color = Color.White)
                            }
                        } else {
                            Text("Continue to Timeline  \u2192",
                                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                color = if (uiState.canProceed) Color.White else AppColors.TextSecondary,
                                maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        },
        containerColor = AppColors.Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            item(key = "chips") {
                Column(modifier = Modifier.padding(top = AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Text("Add media", fontSize = 13.sp, color = AppColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = AppSpacing.md))
                    LazyRow(contentPadding = PaddingValues(horizontal = AppSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        items(PickerCategory.values().toList(), key = { it.name }) { cat ->
                            val count = uiState.assets.count { it.mediaType == cat.mediaType }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (count > 0) AppColors.Primary.copy(alpha = 0.15f) else AppColors.Surface)
                                    .border(1.dp, if (count > 0) AppColors.Primary else AppColors.SurfaceVariant, CircleShape)
                                    .clickable { activeCategory = cat }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(cat.emoji, fontSize = 14.sp)
                                    Text(cat.label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        color = if (count > 0) AppColors.Primary else AppColors.OnBackground,
                                        maxLines = 1)
                                    if (count > 0) {
                                        Box(modifier = Modifier.size(18.dp).clip(CircleShape)
                                            .background(AppColors.Primary),
                                            contentAlignment = Alignment.Center) {
                                            Text(count.toString(), fontSize = 10.sp,
                                                color = Color.White, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(32.dp))
                    }
                }
            }

            if (uiState.assets.isEmpty() && !uiState.isLoading) {
                item(key = "empty") {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)
                        .clip(RoundedCornerShape(16.dp)).background(AppColors.Surface)
                        .padding(AppSpacing.xxl), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            Text("\uD83D\uDCF2", fontSize = 48.sp)
                            Text("No media added yet", fontWeight = FontWeight.SemiBold,
                                color = AppColors.OnBackground, fontSize = 16.sp)
                            Text("Tap a category above to pick\nvideos, images, audio or overlays",
                                fontSize = 13.sp, color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center, lineHeight = 18.sp)
                        }
                    }
                }
            }

            if (uiState.assets.isNotEmpty()) {
                item(key = "asset_header") {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Selected Assets", fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground)
                        Text("Use arrows to reorder", fontSize = 11.sp, color = AppColors.TextSecondary)
                    }
                }
                items(uiState.assets, key = { it.id }) { asset ->
                    AssetCard(
                        asset      = asset,
                        isFirst    = uiState.assets.first().id == asset.id,
                        isLast     = uiState.assets.last().id  == asset.id,
                        onRemove   = { viewModel.removeAsset(asset.id) },
                        onMoveUp   = { viewModel.moveUp(asset.id) },
                        onMoveDown = { viewModel.moveDown(asset.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetCard(
    asset: ImportedAsset, isFirst: Boolean, isLast: Boolean,
    onRemove: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit
) {
    val typeEmoji = when (asset.mediaType) {
        MediaType.VIDEO         -> "\uD83C\uDFAC"
        MediaType.IMAGE         -> "\uD83D\uDDBC\uFE0F"
        MediaType.AUDIO         -> "\uD83C\uDFB5"
        MediaType.LOGO          -> "\uD83C\uDFA8"
        MediaType.OVERLAY_IMAGE -> "\uD83D\uDDBC\uFE0F"
        MediaType.OVERLAY_VIDEO -> "\uD83C\uDFAC"
    }
    val typeLabel = when (asset.mediaType) {
        MediaType.VIDEO         -> "Video"
        MediaType.IMAGE         -> "Image"
        MediaType.AUDIO         -> "Audio"
        MediaType.LOGO          -> "Logo"
        MediaType.OVERLAY_IMAGE -> "Overlay"
        MediaType.OVERLAY_VIDEO -> "Overlay"
    }
    val typeColor = when (asset.mediaType) {
        MediaType.VIDEO, MediaType.OVERLAY_VIDEO -> AppColors.Primary
        MediaType.AUDIO -> AppColors.Accent
        MediaType.LOGO  -> AppColors.Warning
        else            -> AppColors.Secondary
    }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)
        .clip(RoundedCornerShape(14.dp)).background(AppColors.Surface).padding(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {

        Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
            .background(AppColors.SurfaceVariant)) {
            if (asset.mediaType != MediaType.AUDIO) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(asset.uri).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.3f), AppColors.Accent.copy(alpha = 0.1f)))),
                    contentAlignment = Alignment.Center) { Text("\uD83C\uDFB5", fontSize = 30.sp) }
            }
            if (asset.mediaType == MediaType.VIDEO || asset.mediaType == MediaType.OVERLAY_VIDEO) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center) {
                        Text("\u25B6", fontSize = 9.sp, color = Color.Black)
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(typeEmoji, fontSize = 14.sp)
                Box(modifier = Modifier.clip(RoundedCornerShape(5.dp))
                    .background(typeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(typeLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = typeColor)
                }
            }
            if (asset.durationMs != null && asset.durationMs > 0) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("\u23F1", fontSize = 11.sp, color = AppColors.TextSecondary)
                    Text(TimeFormatter.formatMs(asset.durationMs), fontSize = 12.sp,
                        color = AppColors.OnBackground, fontWeight = FontWeight.Medium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("\uD83D\uDCBE", fontSize = 11.sp, color = AppColors.TextSecondary)
                Text(FileSizeFormatter.format(asset.sizeBytes), fontSize = 12.sp,
                    color = AppColors.TextSecondary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "Up",
                    tint = if (!isFirst) AppColors.TextSecondary else AppColors.SurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Down",
                    tint = if (!isLast) AppColors.TextSecondary else AppColors.SurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
        }

        Box(modifier = Modifier.size(34.dp).clip(CircleShape)
            .background(AppColors.Error.copy(alpha = 0.12f)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center) {
            Text("\u00D7", color = AppColors.Error, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
