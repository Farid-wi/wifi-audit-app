package com.wifiaudit.app.presentation.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing

/**
 * Shared top header for every step of the stepper: an optional back button followed by the
 * progress indicator. Pass [onBack] = null on the very first reachable screen (no place to go back).
 *
 * Replaces the bare `StepProgressBar` at the top of each screen so that back-navigation is always
 * visible and discoverable (the previous flow was forward-only).
 */
@Composable
fun StepHeader(
    currentStep: Int,
    totalSteps: Int = 5,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppSpacing.SM,
                end = AppSpacing.XXL,
                top = AppSpacing.SM,
                bottom = AppSpacing.SM
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)
    ) {
        if (onBack != null) {
            // 48dp = zone tactile minimale (règle UX 7).
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Retour",
                    tint = AppColors.TextSecondary
                )
            }
        } else {
            Spacer(Modifier.width(AppSpacing.XS))
        }

        // Segments de progression — un par étape, remplis jusqu'à l'étape courante.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS)
        ) {
            repeat(totalSteps) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(
                            color = if (i < currentStep) AppColors.Accent else AppColors.BorderSoft,
                            shape = AppShape.Pill
                        )
                )
            }
        }
    }
}
