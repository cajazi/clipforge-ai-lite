package com.clipforge.ai.presentation.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing

@Composable
fun SplashScreen(
    onHome: () -> Unit,
    onLogin: () -> Unit,
    viewModel: SplashViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            is SplashDestination.Home  -> onHome()
            is SplashDestination.Login -> onLogin()
            null -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Box(modifier = Modifier.size(88.dp)
                .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)),
                    RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Text("CF", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Text("ClipForge AI", fontSize = 28.sp, fontWeight = FontWeight.Black,
                color = AppColors.OnBackground)
            Text("AI Video Editor", fontSize = 14.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.height(AppSpacing.xl))
            LinearProgressIndicator(
                progress   = { uiState.progress },
                modifier   = Modifier.width(180.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                color      = AppColors.Primary,
                trackColor = AppColors.SurfaceVariant
            )
        }
    }
}
