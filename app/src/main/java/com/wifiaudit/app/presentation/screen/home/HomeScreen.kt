package com.wifiaudit.app.presentation.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiaudit.app.domain.model.AuditStatus
import com.wifiaudit.app.domain.model.OverallScore
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing
import com.wifiaudit.app.presentation.theme.AppType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNewAudit: () -> Unit,
    onOpenAudit: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── Header ──────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.XL)
            ) {
                Text(
                    text = "Mes diagnostics",
                    style = AppType.CardTitle,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(AppSpacing.XS))
                Text(
                    text = "Vos audits Wi-Fi sauvegardés",
                    style = AppType.BodyPrimary,
                    color = AppColors.TextMuted
                )
            }

            // ─── Content ─────────────────────────────────────────────────────
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppColors.Accent)
                    }
                }

                uiState.audits.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Wifi,
                                contentDescription = null,
                                tint = AppColors.BorderSoft,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "Aucun diagnostic",
                                style = AppType.BodyPrimary,
                                color = AppColors.TextMuted
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.XXL),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)
                    ) {
                        items(uiState.audits) { audit ->
                            AuditCard(
                                item = audit,
                                onClick = { onOpenAudit(audit.id) }
                            )
                        }
                        item { Spacer(Modifier.height(AppSpacing.Section)) }
                    }
                }
            }
        }

        // ─── FAB "Nouveau diagnostic" ─────────────────────────────────────────
        Button(
            onClick = onNewAudit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppSpacing.XXL, start = AppSpacing.XXL, end = AppSpacing.XXL)
                .fillMaxWidth(),
            shape = AppShape.Pill,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Wifi,
                contentDescription = null,
                tint = AppColors.OnAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(AppSpacing.SM))
            Text(
                text = "Nouveau diagnostic",
                style = AppType.BodyEmphasis,
                color = AppColors.OnAccent
            )
        }
    }
}

@Composable
private fun AuditCard(
    item: AuditListItem,
    onClick: () -> Unit
) {
    val scoreColor = when (item.score) {
        OverallScore.GOOD -> AppColors.SignalGood
        OverallScore.FAIR -> AppColors.SignalFair
        OverallScore.POOR -> AppColors.SignalPoor
        null              -> AppColors.BorderSoft
    }
    val scoreLabel = when (item.score) {
        OverallScore.GOOD -> "Bonne"
        OverallScore.FAIR -> "Moyenne"
        OverallScore.POOR -> "À améliorer"
        null              -> null
    }
    val statusLabel = when (item.status) {
        AuditStatus.DRAFT   -> "Local"
        AuditStatus.PENDING -> "En attente"
        AuditStatus.SYNCED  -> "Envoyé"
    }

    val dateFormatter = SimpleDateFormat("d MMM yyyy · HH'h'mm", Locale.FRENCH)
    val dateStr = dateFormatter.format(Date(item.createdAt))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape.Large)
            .border(1.dp, AppColors.BorderSoft, AppShape.Large)
            .background(AppColors.Surface, AppShape.Large)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Barre colorée latérale ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(64.dp)
                .background(scoreColor)
        )

        Spacer(Modifier.width(AppSpacing.MD))

        // ── Corps de la carte ─────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).padding(vertical = AppSpacing.MD)) {
            Text(
                text = if (item.name.isNotBlank()) item.name else item.ssid.ifBlank { "Réseau inconnu" },
                style = AppType.BodyEmphasis,
                color = AppColors.TextPrimary
            )
            if (item.name.isNotBlank()) {
                Text(
                    text = item.ssid,
                    style = AppType.Micro,
                    color = AppColors.TextMeta
                )
            }
            Spacer(Modifier.height(AppSpacing.XS))
            Text(
                text = dateStr,
                style = AppType.Micro,
                color = AppColors.TextMeta
            )
            Spacer(Modifier.height(AppSpacing.XS))
            Text(
                text = "${item.roomCount} pièce${if (item.roomCount > 1) "s" else ""} · ${item.measurementCount} mesure${if (item.measurementCount > 1) "s" else ""}",
                style = AppType.Micro,
                color = AppColors.TextMuted
            )
        }

        // ── Chips score + statut + flèche ─────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.XS),
            modifier = Modifier.padding(end = AppSpacing.MD, top = AppSpacing.SM, bottom = AppSpacing.SM)
        ) {
            if (scoreLabel != null) {
                Box(
                    modifier = Modifier
                        .background(scoreColor.copy(alpha = 0.12f), AppShape.Small)
                        .padding(horizontal = AppSpacing.SM, vertical = AppSpacing.XS)
                ) {
                    Text(
                        text = scoreLabel,
                        style = AppType.Micro,
                        color = scoreColor
                    )
                }
            }
            Box(
                modifier = Modifier
                    .background(AppColors.SurfaceAlt, AppShape.Small)
                    .padding(horizontal = AppSpacing.SM, vertical = AppSpacing.XS)
            ) {
                Text(
                    text = statusLabel,
                    style = AppType.Micro,
                    color = AppColors.TextMuted
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.TextMeta,
            modifier = Modifier.padding(end = AppSpacing.MD).size(20.dp)
        )
    }
}
