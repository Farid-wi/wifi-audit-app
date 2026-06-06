package com.wifiaudit.app.presentation.screen.network

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.wifiaudit.app.presentation.screen.measure.StepProgressBar
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing
import com.wifiaudit.app.presentation.theme.AppType

@Composable
fun NetworkSelectionScreen(
    auditCreationViewModel: AuditCreationViewModel,
    onNext: () -> Unit,
    viewModel: NetworkSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
        StepProgressBar(currentStep = 3, totalSteps = 5)

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
            uiState.permissionDenied -> PermissionDeniedMessage(modifier = Modifier.weight(1f))

            uiState.isLoading -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.Accent)
            }

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
private fun PermissionDeniedMessage(modifier: Modifier = Modifier) {
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
        Icon(
            Icons.Outlined.Wifi,
            contentDescription = null,
            tint = if (network.isConnected) AppColors.Accent else AppColors.TextMuted,
            modifier = Modifier.size(22.dp)
        )
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
