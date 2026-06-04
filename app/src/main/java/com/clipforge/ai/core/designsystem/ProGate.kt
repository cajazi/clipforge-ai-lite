package com.clipforge.ai.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wraps content that requires Pro.
 * If the user is free, shows an upgrade prompt instead.
 */
@Composable
fun ProGate(
    isPro: Boolean,
    featureName: String,
    onUpgrade: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isPro) {
        content()
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(AppColors.Surface, AppColors.SurfaceVariant))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("\uD83D\uDD12", fontSize = 40.sp)
                Text(
                    "$featureName is a Pro feature",
                    fontWeight = FontWeight.Bold,
                    color = AppColors.OnBackground,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Upgrade to ClipForge Pro to unlock this and all other premium features.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onUpgrade,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(
                                Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Upgrade to Pro", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
