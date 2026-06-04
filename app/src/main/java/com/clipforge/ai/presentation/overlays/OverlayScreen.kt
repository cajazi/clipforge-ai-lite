package com.clipforge.ai.presentation.overlays

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.domain.model.OverlayType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: OverlayViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> /* TODO: add overlay asset */ }

    val tabs = listOf(
        OverlayType.IMAGE to "Image",
        OverlayType.VIDEO to "Video",
        OverlayType.LOGO  to "Logo"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Overlays", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Tab row
            Row(
                modifier = Modifier.fillMaxWidth().background(AppColors.Surface).padding(AppSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                tabs.forEach { (type, label) ->
                    val selected = uiState.selectedTab == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AppColors.Primary else Color.Transparent)
                            .clickable { viewModel.selectTab(type) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selected) Color.White else AppColors.TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                    }
                }
            }

            // Content
            Box(modifier = Modifier.fillMaxSize().padding(AppSpacing.md)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val (emoji, title, subtitle) = when (uiState.selectedTab) {
                        OverlayType.IMAGE -> Triple("\uD83D\uDDBC\uFE0F", "Image Overlays", "Add images on top of your video")
                        OverlayType.VIDEO -> Triple("\uD83C\uDFAC", "Video Overlays", "Layer videos over your project")
                        OverlayType.LOGO  -> Triple("\uD83C\uDFA8", "Logo / Watermark", "Add your brand logo")
                        else              -> Triple("\uD83D\uDDBC\uFE0F", "Overlays", "Add overlays to your video")
                    }

                    Text(emoji, fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.OnBackground)
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Text(subtitle, fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(AppSpacing.xl))

                    Button(
                        onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        modifier = Modifier.fillMaxWidth(0.7f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryVariant)), shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("Pick from Gallery", fontWeight = FontWeight.SemiBold, color = Color.White) }
                    }
                }
            }
        }
    }
}
