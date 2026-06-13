package com.wifiaudit.app.presentation.screen.network

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiaudit.app.domain.repository.WifiRepository
import com.wifiaudit.app.presentation.AuditCreationViewModel
import com.wifiaudit.app.presentation.screen.common.StepHeader
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing
import com.wifiaudit.app.presentation.theme.AppType

@Composable
fun NetworkSelectionScreen(
    auditCreationViewModel: AuditCreationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: NetworkSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Ouvre la page Réglages de l'app pour accorder la permission refusée définitivement.
    fun openAppSettings() {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    // Permissions nécessaires pour accéder aux résultats de scan Wi-Fi
    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // On charge dès qu'au moins la localisation est accordée
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.NEARBY_WIFI_DEVICES] == true) {
            viewModel.loadNetworks()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // Vérification et demande de permission au premier affichage
    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) viewModel.loadNetworks()
        else permissionLauncher.launch(requiredPermissions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .systemBarsPadding()
    ) {
        StepHeader(currentStep = 3, onBack = onBack)

        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Quel est votre réseau Wi-Fi ?", style = AppType.CardTitle, color = AppColors.TextPrimary)
                Spacer(Modifier.height(AppSpacing.XS))
                Text("Choisissez votre réseau dans la liste.", style = AppType.BodyPrimary, color = AppColors.TextMuted)
            }
            IconButton(onClick = viewModel::loadNetworks) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Actualiser", tint = AppColors.Accent)
            }
        }

        when {
            uiState.permissionDenied -> PermissionDeniedMessage(
                onOpenSettings = ::openAppSettings,
                modifier = Modifier.weight(1f)
            )

            uiState.isLoading -> SkeletonNetworkList(modifier = Modifier.weight(1f))

            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.networks) { network ->
                    NetworkItem(
                        network    = network,
                        isSelected = uiState.selectedSsid == network.ssid,
                        onSelect   = { viewModel.selectNetwork(network.ssid) }
                    )
                    HorizontalDivider(color = AppColors.BorderSoft, thickness = 0.5.dp)
                }
                if (uiState.networks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(AppSpacing.Section),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Aucun réseau détecté.\nVérifiez que le Wi-Fi est activé\net que la localisation est autorisée.",
                                style = AppType.BodyPrimary,
                                color = AppColors.TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                // Carte d'aide en bas de liste.
                item { NetworkHelpCard() }
            }
        }

        Button(
            onClick = {
                val ssid = uiState.selectedSsid ?: return@Button
                auditCreationViewModel.setSsid(ssid)
                onNext()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG),
            shape = AppShape.Pill,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp),
            enabled = uiState.selectedSsid != null
        ) {
            Text("Continuer", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
        }
    }
}

@Composable
private fun PermissionDeniedMessage(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(AppSpacing.XXL)
        ) {
            Text(
                "Accès à la localisation refusé",
                style = AppType.BodyEmphasis,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(AppSpacing.SM))
            Text(
                "Android exige la permission de localisation pour détecter les réseaux Wi-Fi. " +
                "Activez-la dans les réglages de l'application.",
                style = AppType.BodyPrimary,
                color = AppColors.TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AppSpacing.LG))
            Button(
                onClick = onOpenSettings,
                shape = AppShape.Pill,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text("Ouvrir les réglages", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
            }
        }
    }
}

@Composable
private fun NetworkItem(
    network: WifiRepository.VisibleNetwork,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
    ) {
        SignalBars(level = network.level, active = network.isConnected || isSelected)
        Text(
            text = network.ssid,
            style = AppType.BodyPrimary,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (network.isConnected) {
            Badge(containerColor = AppColors.SignalGood) {
                Text(
                    "Connecté",
                    style = AppType.Micro,
                    color = AppColors.OnAccent,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent)
        )
    }
}

/** Nombre de barres (1..4) à partir du RSSI — jamais affiché en clair (règle « pas de dBm »). */
private fun signalBars(rssi: Int): Int = when {
    rssi >= -55 -> 4
    rssi >= -67 -> 3
    rssi >= -78 -> 2
    else        -> 1
}

@Composable
private fun SignalBars(level: Int, active: Boolean) {
    val bars = signalBars(level)
    val onColor  = if (active) AppColors.Accent else AppColors.TextSecondary
    val offColor = AppColors.BorderSoft
    val heights = listOf(7.dp, 11.dp, 15.dp, 19.dp)
    Row(
        modifier = Modifier.width(24.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(4) { i ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(heights[i])
                    .background(if (i < bars) onColor else offColor, AppShape.Small)
            )
        }
    }
}

/** Skeleton animé (shimmer) affiché pendant le scan, à la place d'un écran vide. */
@Composable
private fun SkeletonNetworkList(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    val shimmer = AppColors.BorderSoft.copy(alpha = alpha)
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
            ) {
                Box(Modifier.size(20.dp).background(shimmer, AppShape.Small))
                Box(Modifier.height(14.dp).fillMaxWidth(0.55f).background(shimmer, AppShape.Small))
            }
            HorizontalDivider(color = AppColors.BorderSoft, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun NetworkHelpCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG)
            .background(AppColors.Accent.copy(alpha = 0.06f), AppShape.Large)
            .padding(AppSpacing.LG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
    ) {
        Icon(Icons.Outlined.Info, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
        Text(
            "Vous ne voyez pas votre réseau ? Rapprochez-vous de votre box, puis actualisez.",
            style = AppType.ControlLabel, color = AppColors.TextSecondary
        )
    }
}
