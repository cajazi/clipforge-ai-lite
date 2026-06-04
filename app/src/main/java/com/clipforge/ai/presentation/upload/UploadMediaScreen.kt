package com.clipforge.ai.presentation.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import com.clipforge.ai.domain.model.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadMediaScreen(
    projectId: String,
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: UploadMediaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris -> if (uris.isNotEmpty()) viewModel.onMediaPicked(uris) }

    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onMediaPicked(listOf(it)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Add Media", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold)
                        Text("${uiState.mediaItems.size} item(s) selected",
                            color = AppColors.TextSecondary, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(AppColors.Background).padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                // Upload progress
                if (uiState.isUploading) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Uploading to cloud...", color = AppColors.TextSecondary, fontSize = 12.sp)
                            Text("${uiState.uploadProgress}%", color = AppColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { uiState.uploadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = AppColors.Primary, trackColor = AppColors.Surface
                        )
                    }
                }

                // Add more
                OutlinedButton(
                    onClick = { multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Primary),
                    enabled = !uiState.isUploading
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AppColors.Primary)
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    Text("Add More Media", color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
                }

                // Continue / Upload button
                Button(
                    onClick = {
                        if (uiState.allUploaded) {
                            onNext()
                        } else {
                            viewModel.uploadAllToSupabase { onNext() }
                        }
                    },
                    enabled = uiState.canProceed && !uiState.isUploading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            if (uiState.canProceed && !uiState.isUploading)
                                Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryVariant))
                            else Brush.linearGradient(listOf(AppColors.SurfaceVariant, AppColors.SurfaceVariant)),
                            shape = RoundedCornerShape(14.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isUploading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                Text("Uploading...", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            }
                        } else {
                            Text(
                                if (uiState.allUploaded) "Continue to Editor" else "Upload & Continue",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = if (uiState.canProceed) Color.White else AppColors.TextSecondary
                            )
                        }
                    }
                }
            }
        },
        containerColor = AppColors.Background
    ) { padding ->
        if (uiState.mediaItems.isEmpty()) {
            EmptyPickerState(
                onPickSingle   = { singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onPickMultiple = { multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                modifier       = Modifier.padding(padding)
            )
        } else {
            MediaGrid(
                items    = uiState.mediaItems,
                onRemove = { viewModel.removeItem(it) },
                onAddMore = { multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                modifier  = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun EmptyPickerState(onPickSingle: () -> Unit, onPickMultiple: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(AppColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) { Text("\uD83D\uDCF9", fontSize = 48.sp) }
        Spacer(modifier = Modifier.height(AppSpacing.lg))
        Text("Add Your Media", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.OnBackground)
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Text("Pick videos and images from your gallery", fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(AppSpacing.xl))
        Button(
            onClick = onPickMultiple,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) { Text("Pick Multiple Files", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        OutlinedButton(
            onClick = onPickSingle,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.SurfaceVariant)
        ) { Text("Pick Single File", color = AppColors.OnBackground) }
    }
}

@Composable
private fun MediaGrid(items: List<MediaItem>, onRemove: (Uri) -> Unit, onAddMore: () -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        modifier = modifier
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            MediaItemCard(item = item, onRemove = { onRemove(item.uri) })
        }
        item {
            Box(
                modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Surface)
                    .border(1.dp, AppColors.SurfaceVariant, RoundedCornerShape(8.dp))
                    .clickable(onClick = onAddMore),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = AppColors.Primary, modifier = Modifier.size(28.dp))
                    Text("Add", fontSize = 11.sp, color = AppColors.Primary)
                }
            }
        }
    }
}

@Composable
private fun MediaItemCard(item: MediaItem, onRemove: () -> Unit) {
    Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(item.uri).crossfade(true).build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Upload status overlay
        if (item.isUploading) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
        if (item.uploadedUrl != null && !item.isUploading) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp).clip(CircleShape).background(AppColors.Success), contentAlignment = Alignment.Center) {
                Text("\u2713", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        if (item.uploadFailed) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp).clip(CircleShape).background(AppColors.Error), contentAlignment = Alignment.Center) {
                Text("!", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        // Bottom info bar
        Box(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(AppSpacing.xs)
        ) {
            Column {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(3.dp))
                        .background(when (item.mediaType) { MediaType.VIDEO -> AppColors.Primary; MediaType.AUDIO -> AppColors.Accent; else -> AppColors.Secondary })
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(when (item.mediaType) { MediaType.VIDEO -> "VID"; MediaType.AUDIO -> "AUD"; else -> "IMG" },
                        fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(FileSizeFormatter.format(item.sizeBytes), fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        // Remove button
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) { Text("x", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
}
