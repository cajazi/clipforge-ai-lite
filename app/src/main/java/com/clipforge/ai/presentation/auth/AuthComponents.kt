package com.clipforge.ai.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing

@Composable
fun PremiumButton(text: String, isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled && !isLoading,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                if (enabled && !isLoading)
                    Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary))
                else Brush.linearGradient(listOf(AppColors.SurfaceVariant, AppColors.SurfaceVariant)),
                RoundedCornerShape(18.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp),
                    color = Color.White, strokeWidth = 2.5.dp)
            } else {
                Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    color = if (enabled) Color.White else AppColors.TextSecondary)
            }
        }
    }
}

@Composable
fun GoogleSignInButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Google G
            Box(modifier = Modifier.size(24.dp)
                .background(AppColors.SurfaceVariant, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center) {
                Text("G", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.OnBackground)
            }
            Text("Continue with Google", fontWeight = FontWeight.SemiBold,
                color = AppColors.OnBackground, fontSize = 15.sp)
        }
    }
}

@Composable
fun AuthDivider() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = AppColors.Border, thickness = 1.dp)
        Text("or continue with", fontSize = 13.sp, color = AppColors.TextSecondary)
        HorizontalDivider(modifier = Modifier.weight(1f), color = AppColors.Border, thickness = 1.dp)
    }
}

@Composable
fun premiumFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = AppColors.Primary,
    unfocusedBorderColor    = AppColors.Border,
    errorBorderColor        = AppColors.Error,
    focusedTextColor        = Color.White,
    unfocusedTextColor      = AppColors.OnBackground,
    focusedLabelColor       = AppColors.Primary,
    unfocusedLabelColor     = AppColors.TextSecondary,
    cursorColor             = AppColors.Primary,
    focusedContainerColor   = AppColors.SurfaceVariant,
    unfocusedContainerColor = AppColors.Surface,
    errorContainerColor     = AppColors.Error.copy(alpha = 0.05f)
)
