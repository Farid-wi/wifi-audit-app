package com.wifiaudit.app.presentation.screen.measure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing
import com.wifiaudit.app.presentation.theme.AppType

/**
 * Modal shown at the start of a measurement session (and re-openable from the Measure screen).
 * Lets the user pick the scan cadence:
 *  - Fast mode (only on Android 10+) — requires disabling Wi-Fi scan throttling in developer
 *    options; a "Vérifier" button empirically probes whether throttling is actually off.
 *  - Standard mode — the default, safe everywhere.
 *
 * UI text is in French; the dialog never surfaces technical terms (RSSI, BSSID, throttle…).
 */
@Composable
fun ScanModeDialog(
    fastModeAvailable: Boolean,
    isDetecting: Boolean,
    detectionMessage: String?,
    onChooseStandard: () -> Unit,
    onVerifyFast: () -> Unit
) {
    Dialog(onDismissRequest = { /* choix obligatoire : pas de fermeture par clic extérieur */ }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Background, AppShape.Large)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.XXL)
        ) {
            Text(
                text = "Comment mesurer ?",
                style = AppType.CardTitle,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(AppSpacing.XS))
            Text(
                text = "Choisissez le rythme des mesures. Vous pourrez en changer à tout moment.",
                style = AppType.BodyPrimary,
                color = AppColors.TextMuted
            )

            Spacer(Modifier.height(AppSpacing.XL))

            // ── Option A — Mode rapide (Android 10+ uniquement) ──────────────
            if (fastModeAvailable) {
                FastModeCard(
                    isDetecting = isDetecting,
                    detectionMessage = detectionMessage,
                    onVerify = onVerifyFast
                )
                Spacer(Modifier.height(AppSpacing.LG))
            }

            // ── Option B — Mode standard (défaut, partout) ───────────────────
            StandardModeCard(
                enabled = !isDetecting,
                onChoose = onChooseStandard
            )
        }
    }
}

@Composable
private fun FastModeCard(
    isDetecting: Boolean,
    detectionMessage: String?,
    onVerify: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Accent.copy(alpha = 0.06f), AppShape.Large)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.25f), AppShape.Large)
            .padding(AppSpacing.XL)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = AppColors.Accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpacing.SM))
            Text(
                text = "Mode rapide",
                style = AppType.BodyEmphasis,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.width(AppSpacing.SM))
            Text(
                text = "recommandé pour les pros",
                style = AppType.ControlLabel,
                color = AppColors.Accent
            )
        }
        Spacer(Modifier.height(AppSpacing.SM))
        Text(
            text = "Mesures enchaînées, sans temps d'attente. Nécessite un réglage à faire une " +
                "seule fois sur votre téléphone :",
            style = AppType.BodyPrimary,
            color = AppColors.TextSecondary
        )

        Spacer(Modifier.height(AppSpacing.MD))

        TutorialStep(1, "Ouvrez les Réglages Android, puis « À propos du téléphone ».")
        TutorialStep(2, "Touchez 7 fois « Numéro de build » pour activer le mode développeur.")
        TutorialStep(3, "Dans « Options pour les développeurs », désactivez " +
            "« Limitation de la recherche Wi-Fi ».")
        TutorialStep(4, "Revenez ici et touchez « Vérifier ».")

        Spacer(Modifier.height(AppSpacing.LG))

        // Message de résultat (échec / encore actif) — couleur orange d'avertissement.
        if (detectionMessage != null) {
            Text(
                text = detectionMessage,
                style = AppType.ControlLabel,
                color = AppColors.SignalFair
            )
            Spacer(Modifier.height(AppSpacing.MD))
        }

        Button(
            onClick = onVerify,
            enabled = !isDetecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = AppShape.Pill,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
        ) {
            if (isDetecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = AppColors.OnAccent,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(AppSpacing.SM))
                Text("Vérification…", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
            } else {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = AppColors.OnAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(AppSpacing.SM))
                Text("Vérifier", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
            }
        }
    }
}

@Composable
private fun StandardModeCard(
    enabled: Boolean,
    onChoose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface, AppShape.Large)
            .border(1.dp, AppColors.BorderSoft, AppShape.Large)
            .padding(AppSpacing.XL)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpacing.SM))
            Text(
                text = "Mode standard",
                style = AppType.BodyEmphasis,
                color = AppColors.TextPrimary
            )
        }
        Spacer(Modifier.height(AppSpacing.SM))
        Text(
            text = "Fonctionne sur tous les téléphones, sans réglage. Un court temps d'attente " +
                "s'affiche entre chaque mesure.",
            style = AppType.BodyPrimary,
            color = AppColors.TextSecondary
        )

        Spacer(Modifier.height(AppSpacing.LG))

        OutlinedButton(
            onClick = onChoose,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = AppShape.Pill
        ) {
            Text(
                text = "Utiliser le mode standard",
                style = AppType.BodyEmphasis,
                color = AppColors.TextPrimary
            )
        }
    }
}

@Composable
private fun TutorialStep(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.XS),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(AppColors.Accent.copy(alpha = 0.12f), AppShape.Circle),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = AppType.Micro.copy(fontWeight = FontWeight.SemiBold),
                color = AppColors.Accent
            )
        }
        Text(
            text = text,
            style = AppType.ControlLabel,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
