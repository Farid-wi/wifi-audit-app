package com.wifiaudit.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary        = AppColors.Accent,
    onPrimary      = AppColors.OnAccent,
    background     = AppColors.Background,
    onBackground   = AppColors.TextPrimary,
    surface        = AppColors.Surface,
    onSurface      = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceWarm,
    outline        = AppColors.Border,
    outlineVariant = AppColors.BorderSoft,
    secondary      = AppColors.TextMuted,
    onSecondary    = AppColors.OnAccent,
)

@Composable
fun WifiAuditTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
