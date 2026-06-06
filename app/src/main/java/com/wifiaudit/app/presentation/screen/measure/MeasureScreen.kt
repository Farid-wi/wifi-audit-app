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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.RoomType
import com.wifiaudit.app.domain.model.SignalQuality
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
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHost.showSnackbar(it) }
    }

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
                ExtendedFloatingActionButton(
                    onClick = viewModel::takeMeasurement,
                    shape = AppShape.Pill,
                    containerColor = AppColors.Accent,
                    contentColor = AppColors.OnAccent,
                    icon = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
                    text = { Text("Mesurer ici", style = AppType.BodyEmphasis) }
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
            StepProgressBar(currentStep = 4, totalSteps = 5)

            MeasureHeader(
                count = uiState.measurementCount,
                modifier = Modifier.padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.MD)
            )

            // ─── Plan interactif ──────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                InteractivePlanView(
                    planImagePath   = uiState.planImagePath ?: "",
                    rooms           = creationState.rooms,
                    measurements    = uiState.measurements,
                    pendingPosition = uiState.pendingPosition,
                    isLoading       = uiState.isLoading,
                    onTap           = { x, y -> viewModel.selectPosition(x, y) },
                    modifier        = Modifier.fillMaxSize()
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
private fun MeasureHeader(count: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Déplacez-vous dans chaque pièce",
            style = AppType.CardTitle,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.height(AppSpacing.XS))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count mesure${if (count > 1) "s" else ""}",
                style = AppType.BodyEmphasis,
                color = if (count >= MIN_MEASUREMENTS) AppColors.SignalGood else AppColors.Accent
            )
            Text(
                text = "  —  Minimum recommandé : $MIN_MEASUREMENTS",
                style = AppType.ControlLabel,
                color = AppColors.TextMuted
            )
        }
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

// ─── Plan canvas (pas de photo) ───────────────────────────────────────────────

@Composable
private fun MeasureCanvasPlan(rooms: List<CanvasRoom>, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .background(Color.White)
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
                        .background(color.copy(alpha = 0.14f), AppShape.Small)
                        .border(1.dp, color.copy(alpha = 0.50f), AppShape.Small),
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
