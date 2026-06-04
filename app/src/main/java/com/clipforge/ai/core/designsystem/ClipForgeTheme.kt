package com.clipforge.ai.core.designsystem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
private val DarkColorScheme = darkColorScheme(
    primary          = AppColors.Primary,
    onPrimary        = AppColors.OnPrimary,
    primaryContainer = AppColors.PrimaryVariant,
    secondary        = AppColors.Secondary,
    tertiary         = AppColors.Accent,
    background       = AppColors.Background,
    surface          = AppColors.Surface,
    surfaceVariant   = AppColors.SurfaceVariant,
    onBackground     = AppColors.OnBackground,
    onSurface        = AppColors.OnSurface,
    onSurfaceVariant = AppColors.TextSecondary,
    outline          = AppColors.Border,
    error            = AppColors.Error
)
@Composable
fun ClipForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = AppTypography, content = content)
}
