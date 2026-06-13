package com.wifiaudit.app.presentation.screen.measure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wifiaudit.app.presentation.screen.common.StepHeader
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing
import com.wifiaudit.app.presentation.theme.AppType

@Composable
fun ScanModeScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: ScanModeViewModel = hiltViewModel()
) {
    var selectedFast   by remember { mutableStateOf(false) }
    var showExpertSteps by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .systemBarsPadding()
    ) {
        StepHeader(currentStep = 4, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AppSpacing.XXL),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)
        ) {
            Spacer(Modifier.height(AppSpacing.MD))

            Text(
                text  = "Sélectionnez le mode de mesure Wi-Fi",
                style = AppType.CardTitle,
                color = AppColors.TextPrimary
            )
            Text(
                text  = "Choisissez le rythme des mesures. Vous pouvez en changer à tout moment en revenant sur cette étape.",
                style = AppType.BodyPrimary,
                color = AppColors.TextMuted
            )

            Spacer(Modifier.height(AppSpacing.XS))

            // ── Mode expert (Android 10+) ─────────────────────────────────────
            if (viewModel.fastModeAvailable) {
                ModeCard(selected = selectedFast, onClick = {
                    selectedFast    = true
                    showExpertSteps = true
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioDot(selected = selectedFast)
                        Spacer(Modifier.width(AppSpacing.SM))
                        Text(
                            text  = "Mode expert",
                            style = AppType.BodyEmphasis,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.width(AppSpacing.SM))
                        Text(
                            text     = "expert",
                            style    = AppType.Micro.copy(fontWeight = FontWeight.Medium),
                            color    = AppColors.Accent,
                            modifier = Modifier
                                .background(AppColors.Accent.copy(alpha = 0.10f), AppShape.Pill)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(Modifier.height(AppSpacing.XS))
                    Text(
                        text  = "Enchaînez vos mesures sans délai d'attente.",
                        style = AppType.BodyPrimary,
                        color = AppColors.TextSecondary
                    )

                    Spacer(Modifier.height(AppSpacing.SM))

                    Row(
                        modifier = Modifier
                            .clickable { showExpertSteps = !showExpertSteps }
                            .padding(vertical = AppSpacing.XS),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS)
                    ) {
                        Text(
                            text  = "Comment l'activer",
                            style = AppType.ControlLabel.copy(fontWeight = FontWeight.Medium),
                            color = AppColors.Accent
                        )
                        Icon(
                            imageVector        = if (showExpertSteps) Icons.Outlined.ExpandLess
                                                 else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint     = AppColors.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = showExpertSteps,
                        enter   = expandVertically(),
                        exit    = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = AppSpacing.XS),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.XS)
                        ) {
                            TutorialStep(1, "Ouvrez les Réglages Android, puis « À propos du téléphone ».")
                            TutorialStep(2, "Touchez 7 fois « Numéro de build » pour activer le mode développeur.")
                            TutorialStep(3, "Dans « Options pour les développeurs », désactivez « Limitation de la recherche Wi-Fi ».")
                            TutorialStep(4, "Revenez ici et touchez « Commencer les mesures ».")
                        }
                    }
                }
            }

            // ── Mode standard (défaut, partout) ──────────────────────────────
            ModeCard(selected = !selectedFast, onClick = { selectedFast = false }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioDot(selected = !selectedFast)
                    Spacer(Modifier.width(AppSpacing.SM))
                    Text(
                        text  = "Mode standard",
                        style = AppType.BodyEmphasis,
                        color = AppColors.TextPrimary
                    )
                }
                Spacer(Modifier.height(AppSpacing.XS))
                Text(
                    text  = "Aucun réglage nécessaire, fonctionne sur tous les téléphones. " +
                            "En raison d'une limitation imposée par Android, prévoyez environ " +
                            "30 secondes entre chaque point de mesure.",
                    style = AppType.BodyPrimary,
                    color = AppColors.TextSecondary
                )
            }
        }

        // ── Bouton en bas ─────────────────────────────────────────────────────
        Button(
            onClick = {
                if (selectedFast) viewModel.chooseFastMode() else viewModel.chooseStandardMode()
                onNext()
            },
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG)
                .height(48.dp),
            shape     = AppShape.Pill,
            colors    = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
        ) {
            Text("Commencer les mesures", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
        }
    }
}

// ─── Point de sélection (radio) ───────────────────────────────────────────────

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        modifier         = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) AppColors.Accent else AppColors.BorderSoft, AppShape.Circle),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(AppColors.Accent, AppShape.Circle)
            )
        }
    }
}

// ─── Carte de mode sélectionnable ─────────────────────────────────────────────

@Composable
private fun ModeCard(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (selected) AppColors.Accent else AppColors.BorderSoft
    val borderWidth = if (selected) 2.dp else 1.dp
    val bgColor     = if (selected) AppColors.Accent.copy(alpha = 0.06f) else AppColors.Surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, AppShape.Large)
            .border(borderWidth, borderColor, AppShape.Large)
            .clickable(onClick = onClick)
            .padding(AppSpacing.XL),
        content = content
    )
}

// ─── Étape numérotée du tutoriel ──────────────────────────────────────────────

@Composable
private fun TutorialStep(number: Int, text: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)
    ) {
        Box(
            modifier         = Modifier
                .size(20.dp)
                .background(AppColors.Accent.copy(alpha = 0.12f), AppShape.Circle),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = number.toString(),
                style = AppType.Micro.copy(fontWeight = FontWeight.SemiBold),
                color = AppColors.Accent
            )
        }
        Text(
            text     = text,
            style    = AppType.ControlLabel,
            color    = AppColors.TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
