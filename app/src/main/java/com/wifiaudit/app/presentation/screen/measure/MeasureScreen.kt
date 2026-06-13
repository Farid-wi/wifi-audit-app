package com.wifiaudit.app.presentation.screen.measure

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.Position
import com.wifiaudit.app.domain.model.RepeaterPosition
import com.wifiaudit.app.domain.model.RoomType
import com.wifiaudit.app.domain.model.SignalQuality
import com.wifiaudit.app.presentation.screen.common.StepHeader
import com.wifiaudit.app.presentation.screen.common.planBackdrop
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing
import com.wifiaudit.app.presentation.theme.AppType
import androidx.compose.foundation.Image
import kotlin.math.roundToInt

@Composable
fun MeasureScreen(
    auditCreationViewModel: com.wifiaudit.app.presentation.AuditCreationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: MeasureViewModel = hiltViewModel()
) {
    val creationState by auditCreationViewModel.state.collectAsStateWithLifecycle()
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()

    // Synchronise le plan et le SSID cible depuis le ViewModel partagé
    androidx.compose.runtime.LaunchedEffect(creationState.planImagePath) {
        creationState.planImagePath?.let { viewModel.setPlanImagePath(it) }
    }
    androidx.compose.runtime.LaunchedEffect(creationState.ssid) {
        viewModel.setTargetSsid(creationState.ssid)
    }
    androidx.compose.runtime.LaunchedEffect(creationState.rooms) {
        viewModel.setRooms(creationState.rooms)
    }
    // Initialise la file guidée (une seule fois) avec les appareils placés
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.setDevices(creationState.gatewayPosition, creationState.repeaterPositions)
    }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHost.showSnackbar(it) }
    }

    // Toast (bascule auto en mode standard, confirmation mode rapide…) → snackbar éphémère.
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeToast()
        }
    }



    val haptic = LocalHapticFeedback.current
    // Retour haptique court à la fin de chaque mesure réussie.
    LaunchedEffect(uiState.measurementCount) {
        if (uiState.measurementCount > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = AppColors.Background,
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                Snackbar(snackbarData = data, containerColor = AppColors.DarkSurface)
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState.pendingPosition != null && !uiState.isLoading,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut()
            ) {
                val waiting = uiState.scanCooldownSeconds > 0
                ExtendedFloatingActionButton(
                    onClick = { if (!waiting) viewModel.takeMeasurement() },
                    shape = AppShape.Pill,
                    containerColor = if (waiting) AppColors.SignalFair else AppColors.SignalGood,
                    contentColor   = Color.White,
                    icon = {
                        Icon(
                            imageVector = if (waiting) Icons.Outlined.HourglassEmpty else Icons.Outlined.Wifi,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = if (waiting) "Mesure possible dans ${uiState.scanCooldownSeconds} s"
                                   else "Mesurer ici",
                            style = AppType.BodyEmphasis
                        )
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ─── En-tête ──────────────────────────────────────────────────
            StepHeader(currentStep = 5, onBack = onBack)

            MeasureHeader(
                count         = uiState.measurementCount,
                isGuidedPhase = uiState.guidedDevice != null,
                scanMode      = uiState.scanMode,
                modifier      = Modifier.padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.MD)
            )

            // ─── Bannière d'étape (guidage + feu vert) ────────────────────
            val showBanner = uiState.guidedDevice != null ||
                (uiState.scanCooldownSeconds > 0 && uiState.measurementCount > 0)
            AnimatedVisibility(
                visible = showBanner,
                enter = slideInVertically { -it } + fadeIn(),
                exit  = slideOutVertically { -it } + fadeOut()
            ) {
                StepGuidanceBanner(
                    guidedDevice    = uiState.guidedDevice,
                    cooldownSeconds = uiState.scanCooldownSeconds,
                    modifier = Modifier.padding(start = AppSpacing.XXL, end = AppSpacing.XXL, bottom = AppSpacing.SM)
                )
            }

            // ─── Plan interactif ──────────────────────────────────────────
            // Padding bas : le contenu du plan reste au-dessus du FAB « Mesurer ici ».
            Box(modifier = Modifier.weight(1f).padding(horizontal = AppSpacing.LG).padding(top = 14.dp, bottom = 86.dp)) {
                InteractivePlanView(
                    planImagePath      = uiState.planImagePath ?: "",
                    rooms              = creationState.rooms,
                    measurements       = uiState.measurements,
                    pendingPosition    = uiState.pendingPosition,
                    isLoading          = uiState.isLoading,
                    onTap              = { x, y -> viewModel.selectPosition(x, y) },
                    gatewayPosition    = creationState.gatewayPosition,
                    repeaterPositions  = creationState.repeaterPositions,
                    guidedDeviceId     = uiState.guidedDevice?.deviceId,
                    modifier           = Modifier.fillMaxSize()
                )
            }

            // ─── Bouton terminer ──────────────────────────────────────────
            AnimatedVisibility(visible = uiState.canFinish) {
                Button(
                    onClick = {
                        viewModel.saveAndNavigate(creationState, onSaved = onNext)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG),
                    shape = AppShape.Pill,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AppColors.OnAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Voir les résultats", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
                    }
                }
            }
        }
    }

        // Overlay plein écran pendant la mesure — couvre header, bannière et FAB
        // pour n'afficher qu'un seul message cohérent (« Restez immobile »).
        if (uiState.isLoading) {
            MeasuringOverlay(
                deviceLabel = uiState.guidedDevice?.label,
                onCancel    = viewModel::cancelMeasurement
            )
        }
    }
}

// ─── Barre de progression (partagée entre tous les écrans) ────────────────────

@Composable
fun StepProgressBar(currentStep: Int, totalSteps: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.MD),
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

// ─── En-tête avec compteur ────────────────────────────────────────────────────

@Composable
private fun MeasureHeader(
    count: Int,
    isGuidedPhase: Boolean,
    scanMode: com.wifiaudit.app.domain.model.ScanMode,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isGuidedPhase) "Calibrage des appareils" else "Déplacez-vous dans chaque pièce",
                style = AppType.CardTitle,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            ScanModeChip(scanMode = scanMode)
        }
        Spacer(Modifier.height(AppSpacing.SM))
        MeasureGauge(count = count)
    }
}

/** Jauge de progression des mesures : ●●○○○ + libellé, verte une fois le minimum atteint. */
@Composable
private fun MeasureGauge(count: Int) {
    val reached = count >= MIN_MEASUREMENTS
    val activeColor = if (reached) AppColors.SignalGood else AppColors.Accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS)) {
            repeat(MIN_MEASUREMENTS) { i ->
                val filled = i < count
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(
                            if (filled) activeColor else Color.Transparent,
                            AppShape.Circle
                        )
                        .border(
                            1.5.dp,
                            if (filled) activeColor else AppColors.Border,
                            AppShape.Circle
                        )
                )
            }
        }
        Text(
            text = if (reached) "$count mesures · minimum atteint"
                   else "$count / $MIN_MEASUREMENTS mesures",
            style = AppType.BodyEmphasis,
            color = activeColor
        )
    }
}

/** Petite puce (lecture seule) indiquant le mode actif. */
@Composable
private fun ScanModeChip(
    scanMode: com.wifiaudit.app.domain.model.ScanMode
) {
    val isFast = scanMode == com.wifiaudit.app.domain.model.ScanMode.FAST
    Row(
        modifier = Modifier
            .clip(AppShape.Pill)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.BorderSoft, AppShape.Pill)
            .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (isFast) AppColors.SignalGood else AppColors.TextMeta, AppShape.Circle)
        )
        Spacer(Modifier.width(AppSpacing.XS))
        Text(
            text = if (isFast) "Mode expert" else "Mode standard",
            style = AppType.ControlLabel,
            color = AppColors.TextSecondary
        )
    }
}

// ─── Vue du plan interactive ──────────────────────────────────────────────────

@Composable
private fun InteractivePlanView(
    planImagePath: String,
    rooms: List<CanvasRoom>,
    measurements: List<MeasurementPoint>,
    pendingPosition: Pair<Float, Float>?,
    isLoading: Boolean,
    onTap: (x: Float, y: Float) -> Unit,
    gatewayPosition: Position? = null,
    repeaterPositions: List<RepeaterPosition> = emptyList(),
    guidedDeviceId: String? = null,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(planImagePath) {
        planImagePath.takeIf { it.isNotEmpty() }
            ?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }

    // Pinch-to-zoom
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        panOffset += panChange * scale
    }

    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .transformable(state = transformState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = panOffset.x
                translationY = panOffset.y
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (imageSize != IntSize.Zero) {
                        onTap(
                            (offset.x / imageSize.width).coerceIn(0f, 1f),
                            (offset.y / imageSize.height).coerceIn(0f, 1f)
                        )
                    }
                }
            }
            .onSizeChanged { imageSize = it }
    ) {
        when {
            bitmap != null ->
                Image(
                    bitmap = bitmap,
                    contentDescription = "Plan du logement",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            rooms.isNotEmpty() ->
                MeasureCanvasPlan(rooms = rooms, modifier = Modifier.fillMaxSize())
            else ->
                EmptyPlanPlaceholder(modifier = Modifier.fillMaxSize())
        }

        // Points de mesures enregistrés
        measurements.forEach { point ->
            MeasurementDot(
                x = point.x,
                y = point.y,
                quality = point.quality,
                imageSize = imageSize,
                density = density
            )
        }

        // Point en attente (pulsant pendant la mesure, fixe avant)
        pendingPosition?.let { (px, py) ->
            PendingPositionDot(
                x = px,
                y = py,
                isLoading = isLoading,
                imageSize = imageSize,
                density = density
            )
        }

        // Icônes des appareils sur le plan (aide au positionnement)
        if (imageSize != IntSize.Zero) {
            gatewayPosition?.let { pos ->
                DevicePin(
                    x = pos.x, y = pos.y,
                    icon = Icons.Outlined.Router,
                    color = AppColors.Accent,
                    isHighlighted = guidedDeviceId == "gateway",
                    imageSize = imageSize, density = density
                )
            }
            repeaterPositions.forEach { rep ->
                DevicePin(
                    x = rep.position.x, y = rep.position.y,
                    icon = Icons.Outlined.SettingsInputAntenna,
                    color = AppColors.SignalFair,
                    isHighlighted = guidedDeviceId == rep.id,
                    imageSize = imageSize, density = density
                )
            }
        }
    }
}

// ─── Point de mesure enregistré (coloré selon qualité signal) ────────────────

@Composable
private fun MeasurementDot(
    x: Float,
    y: Float,
    quality: SignalQuality,
    imageSize: IntSize,
    density: androidx.compose.ui.unit.Density
) {
    if (imageSize == IntSize.Zero) return
    val dotSizeDp = 14.dp
    val dotSizePx = with(density) { dotSizeDp.toPx() }

    val offsetX = (x * imageSize.width - dotSizePx / 2).roundToInt()
    val offsetY = (y * imageSize.height - dotSizePx / 2).roundToInt()

    val color = when (quality) {
        SignalQuality.GOOD -> AppColors.SignalGood
        SignalQuality.FAIR -> AppColors.SignalFair
        SignalQuality.POOR -> AppColors.SignalPoor
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(dotSizeDp)
            .background(color, AppShape.Circle)
            .border(2.dp, Color.White, AppShape.Circle)
    )
}

// ─── Point en attente — pulsant si scan en cours, fixe sinon ─────────────────

@Composable
private fun PendingPositionDot(
    x: Float,
    y: Float,
    isLoading: Boolean,
    imageSize: IntSize,
    density: androidx.compose.ui.unit.Density
) {
    if (imageSize == IntSize.Zero) return

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.5f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val baseSizeDp = 18.dp
    val baseSizePx = with(density) { baseSizeDp.toPx() }
    val currentScale = if (isLoading) pulseScale else 1f

    val offsetX = (x * imageSize.width - baseSizePx * currentScale / 2).roundToInt()
    val offsetY = (y * imageSize.height - baseSizePx * currentScale / 2).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(baseSizeDp * currentScale)
            .background(
                color = AppColors.Accent.copy(alpha = if (isLoading) 0.55f else 0.8f),
                shape = AppShape.Circle
            )
            .border(2.dp, Color.White, AppShape.Circle)
    )
}

// ─── Bannière d'étape : guidage appareil + feu vert (cadence entre mesures) ──

@Composable
private fun StepGuidanceBanner(
    guidedDevice: GuidedDeviceInfo?,
    cooldownSeconds: Int,
    modifier: Modifier = Modifier
) {
    val waiting = cooldownSeconds > 0
    val title = when {
        guidedDevice != null && waiting -> "Rejoignez ${guidedDevice.label}"
        guidedDevice != null            -> "Approchez-vous de ${guidedDevice.label}"
        waiting                         -> "Déplacez-vous au point suivant"
        else                            -> "Choisissez un point sur le plan"
    }
    val statusText = if (waiting) "Mesure possible dans $cooldownSeconds s — restez en mouvement"
                     else "C'est le moment — appuyez sur \"Mesurer ici\""
    val dotColor = if (waiting) AppColors.SignalFair else AppColors.SignalGood

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Accent.copy(alpha = 0.10f), AppShape.Medium)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.25f), AppShape.Medium)
            .padding(horizontal = AppSpacing.LG, vertical = AppSpacing.MD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
    ) {
        Icon(
            imageVector = when {
                guidedDevice?.isGateway == true -> Icons.Outlined.Router
                guidedDevice != null            -> Icons.Outlined.SettingsInputAntenna
                else                            -> Icons.Outlined.DirectionsWalk
            },
            contentDescription = null,
            tint = AppColors.Accent,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = AppType.BodyEmphasis,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor, AppShape.Circle)
                )
                Spacer(Modifier.width(AppSpacing.XS))
                Text(
                    text  = statusText,
                    style = AppType.ControlLabel,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

// ─── Overlay plein écran pendant la mesure ───────────────────────────────────

@Composable
private fun MeasuringOverlay(
    deviceLabel: String?,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Fond sombre quasi opaque : aucun plan visible derrière (un seul focus).
            .background(Color(0xFF15181D).copy(alpha = 0.94f))
            // Capture tous les gestes : rien ne passe à travers l'overlay.
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Cercle bleu + icône Wi-Fi, ceinturé d'un anneau de progression.
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = AppColors.Accent,
                    strokeWidth = 4.dp
                )
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(AppColors.Accent, AppShape.Circle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = AppColors.OnAccent,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.Section))
            // Un seul message à la fois.
            Text("Restez immobile", style = AppType.CardTitle, color = Color.White)
            Spacer(Modifier.height(AppSpacing.XS))
            Text(
                text = if (deviceLabel != null)
                    "Mesure du signal près de $deviceLabel…"
                else
                    "Mesure du signal en cours…",
                style = AppType.BodyPrimary,
                color = Color.White.copy(alpha = 0.70f)
            )
        }

        // Bouton fantôme « Annuler » bien détaché en bas, dans la safe-area.
        androidx.compose.material3.TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppSpacing.Section)
        ) {
            Text("Annuler", style = AppType.BodyEmphasis, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

// ─── Icône d'appareil sur le plan (non-interactive) ──────────────────────────

@Composable
private fun DevicePin(
    x: Float, y: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isHighlighted: Boolean,
    imageSize: IntSize,
    density: androidx.compose.ui.unit.Density
) {
    val sizeDp  = 28.dp
    val sizePx  = with(density) { sizeDp.toPx() }
    val offsetX = (x * imageSize.width  - sizePx / 2).roundToInt()
    val offsetY = (y * imageSize.height - sizePx / 2).roundToInt()

    val pulseTransition = rememberInfiniteTransition(label = "device_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "device_alpha"
    )

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(sizeDp)
            .background(
                color = if (isHighlighted) color.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.85f),
                shape = AppShape.Circle
            )
            .border(
                width = if (isHighlighted) 2.5.dp else 1.5.dp,
                color = if (isHighlighted) color.copy(alpha = if (isHighlighted) pulseAlpha else 1f)
                        else color.copy(alpha = 0.7f),
                shape = AppShape.Circle
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isHighlighted) Color.White else color,
            modifier = Modifier.size(14.dp)
        )
    }
}

// ─── Plan canvas (pas de photo) ───────────────────────────────────────────────

@Composable
private fun MeasureCanvasPlan(rooms: List<CanvasRoom>, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .planBackdrop()
            .onSizeChanged { size = it }
    ) {
        if (size != IntSize.Zero) {
            rooms.forEach { room ->
                val color = measureRoomColor(room.type)
                val lPx = (room.bounds.left   * size.width).toInt()
                val tPx = (room.bounds.top    * size.height).toInt()
                val wDp = with(density) { ((room.bounds.right  - room.bounds.left) * size.width).toDp() }
                val hDp = with(density) { ((room.bounds.bottom - room.bounds.top)  * size.height).toDp() }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(lPx, tPx) }
                        .size(wDp, hDp)
                        .background(color.copy(alpha = 0.14f), AppShape.Medium)
                        .border(1.dp, color.copy(alpha = 0.50f), AppShape.Medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(room.label, style = AppType.ControlLabel, color = color,
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

private fun measureRoomColor(type: RoomType): Color = when (type) {
    RoomType.SALON    -> Color(0xFF5AC8FA)
    RoomType.KITCHEN  -> Color(0xFFFF9500)
    RoomType.BEDROOM  -> Color(0xFF5E5CE6)
    RoomType.OFFICE   -> Color(0xFF30B0C7)
    RoomType.BATHROOM -> Color(0xFF34C759)
    RoomType.HALLWAY  -> Color(0xFFAEAEB2)
    RoomType.DINING   -> Color(0xFFFF6B6B)
    RoomType.OTHER    -> Color(0xFF8E8E93)
}

// ─── Placeholder si aucun plan disponible ────────────────────────────────────

@Composable
private fun EmptyPlanPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(AppColors.Surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Aucun plan disponible", style = AppType.BodyPrimary, color = AppColors.TextMuted)
            Spacer(Modifier.height(AppSpacing.XS))
            Text(
                "Revenez à l'étape précédente pour photographier votre plan",
                style = AppType.ControlLabel,
                color = AppColors.TextMeta
            )
        }
    }
}
