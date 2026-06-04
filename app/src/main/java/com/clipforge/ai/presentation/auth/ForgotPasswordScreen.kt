package com.clipforge.ai.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background)
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = AppColors.OnBackground)
                }
            }
            Spacer(Modifier.height(20.dp))

            if (uiState.isSent) {
                Spacer(Modifier.height(40.dp))
                Text("\uD83D\uDCE7", fontSize = 64.sp)
                Spacer(Modifier.height(20.dp))
                Text("Check your email", fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    color = AppColors.OnBackground)
                Spacer(Modifier.height(8.dp))
                Text("We sent a password reset link to\n${uiState.email}",
                    fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                PremiumButton(text = "Back to Sign In", isLoading = false,
                    enabled = true, onClick = onBack)
            } else {
                Text("Reset your password", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    color = AppColors.OnBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Enter your email and we'll send you a link to reset your password.",
                    fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                OutlinedTextField(
                    value = uiState.email, onValueChange = viewModel::onEmailChange,
                    label = { Text("Email address") },
                    isError = uiState.emailError != null,
                    supportingText = uiState.emailError?.let { { Text(it, color = AppColors.Error, fontSize = 12.sp) } },
                    leadingIcon = { Icon(Icons.Default.MailOutline, null, tint = AppColors.TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    colors = premiumFieldColors()
                )
                uiState.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = AppColors.Error, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth()
                            .background(AppColors.Surface, RoundedCornerShape(12.dp))
                            .padding(10.dp))
                }
                Spacer(Modifier.height(24.dp))
                PremiumButton(text = "Send Reset Link", isLoading = uiState.isLoading,
                    enabled = !uiState.isLoading, onClick = viewModel::sendReset)
            }
        }
    }
}
