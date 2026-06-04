package com.clipforge.ai.presentation.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextOverlayScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: TextOverlayViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val colorOptions = listOf("#FFFFFF", "#000000", "#FF6584", "#6C63FF", "#43E97B", "#FFD166", "#EF233C", "#43E8F0")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Text", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.OnBackground)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.addToTimeline(); onBack() }) {
                        Text("Add", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // Preview
            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.text.isEmpty()) {
                    Text("Your text preview", color = Color.White.copy(alpha = 0.3f), fontSize = 18.sp)
                } else {
                    Text(
                        uiState.text,
                        color = Color.White,
                        fontSize = uiState.fontSize.sp,
                        fontWeight = if (uiState.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (uiState.isItalic) FontStyle.Italic else FontStyle.Normal
                    )
                }
            }

            // Text input
            OutlinedTextField(
                value = uiState.text,
                onValueChange = viewModel::onTextChange,
                placeholder = { Text("Enter your text...", color = AppColors.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Primary,
                    unfocusedBorderColor = AppColors.SurfaceVariant,
                    focusedTextColor = AppColors.OnBackground,
                    unfocusedTextColor = AppColors.OnBackground,
                    cursorColor = AppColors.Primary,
                    focusedContainerColor = AppColors.Surface,
                    unfocusedContainerColor = AppColors.Surface
                )
            )

            // Style row
            Text("Style", fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                // Bold
                Box(
                    modifier = Modifier
                        .size(44.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (uiState.isBold) AppColors.Primary else AppColors.Surface)
                        .clickable { viewModel.toggleBold() },
                    contentAlignment = Alignment.Center
                ) { Text("B", fontWeight = FontWeight.Black, color = if (uiState.isBold) Color.White else AppColors.OnSurface, fontSize = 16.sp) }

                // Italic
                Box(
                    modifier = Modifier
                        .size(44.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (uiState.isItalic) AppColors.Primary else AppColors.Surface)
                        .clickable { viewModel.toggleItalic() },
                    contentAlignment = Alignment.Center
                ) { Text("I", fontStyle = FontStyle.Italic, color = if (uiState.isItalic) Color.White else AppColors.OnSurface, fontSize = 16.sp) }
            }

            // Font size slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Font Size", fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground, fontSize = 14.sp)
                    Text("${uiState.fontSize.toInt()}sp", color = AppColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Slider(
                    value = uiState.fontSize,
                    onValueChange = { viewModel.setFontSize(it) },
                    valueRange = 12f..72f,
                    colors = SliderDefaults.colors(thumbColor = AppColors.Primary, activeTrackColor = AppColors.Primary)
                )
            }

            // Color picker
            Text("Color", fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                colorOptions.forEach { hex ->
                    val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                    Box(
                        modifier = Modifier
                            .size(36.dp).clip(CircleShape)
                            .background(color)
                            .then(if (uiState.selectedColor == hex) Modifier.border(2.dp, AppColors.Primary, CircleShape) else Modifier)
                            .clickable { viewModel.setColor(hex) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.md))

            // Add button
            Button(
                onClick = { viewModel.addToTimeline(); onBack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                enabled = uiState.text.isNotBlank()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(
                            if (uiState.text.isNotBlank())
                                Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryVariant))
                            else Brush.linearGradient(listOf(AppColors.SurfaceVariant, AppColors.SurfaceVariant)),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Add Text to Timeline", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = if (uiState.text.isNotBlank()) Color.White else AppColors.TextSecondary)
                }
            }
        }
    }
}
