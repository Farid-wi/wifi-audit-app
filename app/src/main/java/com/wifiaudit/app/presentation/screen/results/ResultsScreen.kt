package com.wifiaudit.app.presentation.screen.results

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.wifiaudit.app.presentation.screen.common.rememberPressedScale
import com.wifiaudit.app.presentation.screen.common.rememberReducedMotion
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.OverallScore
import com.wifiaudit.app.domain.model.Recommendation
import com.wifiaudit.app.domain.model.RepeaterPosition
import com.wifiaudit.app.domain.model.Severity
import com.wifiaudit.app.domain.model.SignalQuality
import com.wifiaudit.app.domain.model.toUserLabel
import androidx.compose.ui.unit.IntOffset
import com.wifiaudit.app.domain.usecase.HeatmapGrid
import com.wifiaudit.app.presentation.screen.common.StepHeader
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing
import com.wifiaudit.app.presentation.theme.AppType
import androidx.compose.foundation.Image

@Composable
fun ResultsScreen(
    onNewAudit: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Confirmation avant de repartir de zéro (les résultats affichés seront perdus).
    var showNewAuditConfirm by remember { mutableStateOf(false) }
    if (showNewAuditConfirm) {
        NewAuditConfirmDialog(
            onConfirm = {
                showNewAuditConfirm = false
                onNewAudit()
            },
            onDismiss = { showNewAuditConfirm = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .systemBarsPadding()
    ) {
        StepHeader(currentStep = 6, onBack = onBack)

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Accent)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.XXL)
        ) {
            Spacer(Modifier.height(AppSpacing.XL))

            // ─── Titre ───────────────────────────────────────────────────
            Text(
                text = "Votre couverture Wi-Fi",
                style = AppType.SectionTitle,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(AppSpacing.LG))

            // ─── Sélecteur de bande ───────────────────────────────────────
            if (uiState.availableBands.size > 1) {
                BandSelector(
                    availableBands = uiState.availableBands,
                    selectedBand   = uiState.selectedBand,
                    onBandSelected = viewModel::selectBand,
                    modifier       = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(AppSpacing.MD))
            }

            // ─── Heatmap ──────────────────────────────────────────────────
            val gatewayOffPlan    = (uiState.gatewayPosition?.x ?: 0f) < 0f
            val offPlanRepeaters  = uiState.repeaterPositions.filter { it.position.x < 0f }
            val hasOffPlan        = gatewayOffPlan || offPlanRepeaters.isNotEmpty()
            val onPlanRepCount    = uiState.repeaterPositions.count { it.position.x >= 0f }

            PlanWithHeatmap(
                planImagePath      = uiState.planImagePath,
                rooms              = uiState.rooms,
                roomGrids          = uiState.roomGrids,
                measurementDots    = uiState.measurementDots.filter { it.x >= 0f },
                gatewayPosition    = uiState.gatewayPosition?.takeIf { it.x >= 0f },
                repeaterPositions  = uiState.repeaterPositions.filter { it.position.x >= 0f },
                selectedDeviceId   = uiState.selectedDeviceId,
                onDeviceClick      = viewModel::selectDevice,
                modifier           = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(AppShape.Large)
            )

            if (hasOffPlan) {
                Spacer(Modifier.height(AppSpacing.SM))
                HorsPlanResultsZone(
                    gatewayOffPlan     = gatewayOffPlan,
                    offPlanRepeaters   = offPlanRepeaters,
                    repeaterStartIndex = onPlanRepCount + 1,
                    selectedDeviceId   = uiState.selectedDeviceId,
                    onDeviceClick      = viewModel::selectDevice,
                    modifier           = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(AppSpacing.MD))
            HeatmapLegend()

            Spacer(Modifier.height(AppSpacing.XL))

            // ─── Score global ──────────────────────────────────────────────
            OverallScoreCard(score = uiState.overallScore)

            // ─── Badges par pièce ──────────────────────────────────────────
            if (uiState.roomResults.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.XL))
                Text("Par pièce", style = AppType.BodyEmphasis, color = AppColors.TextPrimary)
                Spacer(Modifier.height(AppSpacing.MD))
                RoomBadgesGrid(rooms = uiState.roomResults)
            }

            // ─── Recommandations ──────────────────────────────────────────
            if (uiState.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.XL))
                Text("Conseils", style = AppType.BodyEmphasis, color = AppColors.TextPrimary)
                Spacer(Modifier.height(AppSpacing.MD))
                RecommendationsList(recommendations = uiState.recommendations)
            }

            Spacer(Modifier.height(AppSpacing.Section))
        }

        // ─── Boutons d'action ─────────────────────────────────────────────
        val submitEnabled = uiState.submitState != SubmitState.Loading && uiState.submitState != SubmitState.Success
        val (submitScale, submitSource) = rememberPressedScale(enabled = submitEnabled)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)
        ) {
            Button(
                onClick = { viewModel.submitAudit() },
                interactionSource = submitSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = submitScale; scaleY = submitScale },
                shape = AppShape.Pill,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
                enabled = submitEnabled
            ) {
                if (uiState.submitState == SubmitState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AppColors.OnAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (uiState.submitState == SubmitState.Success) "Enregistré ✓" else "Enregistrer mon audit",
                        style = AppType.BodyEmphasis,
                        color = AppColors.OnAccent
                    )
                }
            }
            OutlinedButton(
                onClick = { showNewAuditConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShape.Pill,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Accent),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Accent)
            ) {
                Text("Nouvel audit", style = AppType.BodyEmphasis, color = AppColors.Accent)
            }
        }
    }
}

// ─── Confirmation « Nouvel audit » ────────────────────────────────────────────

@Composable
private fun NewAuditConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Démarrer un nouvel audit ?", style = AppType.CardTitle, color = AppColors.TextPrimary) },
        text  = {
            Text(
                "Les résultats affichés seront effacés. Pensez à les envoyer avant si besoin.",
                style = AppType.BodyPrimary, color = AppColors.TextMuted
            )
        },
        confirmButton = {
            Button(
                onClick   = onConfirm,
                shape     = AppShape.Pill,
                colors    = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) { Text("Nouvel audit", style = AppType.BodyEmphasis, color = AppColors.OnAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", style = AppType.BodyPrimary, color = AppColors.TextMuted)
            }
        },
        containerColor = AppColors.Surface,
        shape          = AppShape.Large
    )
}

// ─── Heatmap sur le plan ──────────────────────────────────────────────────────

@Composable
private fun PlanWithHeatmap(
    planImagePath: String?,
    rooms: List<CanvasRoom>,
    roomGrids: Map<String, HeatmapGrid>,
    measurementDots: List<MeasurementDotInfo>,
    gatewayPosition: com.wifiaudit.app.domain.model.Position?,
    repeaterPositions: List<com.wifiaudit.app.domain.model.RepeaterPosition>,
    selectedDeviceId: String?,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(planImagePath) {
        planImagePath?.takeIf { it.isNotEmpty() }
            ?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .background(Color.White)
            .onSizeChanged { boxSize = it }
    ) {
        // ── 1. Fond : photo ou plan canvas ───────────────────────────────────
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Plan",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Fond blanc pour plan canvas (pas de photo)
        // La grille de fond aide à visualiser le plan
        if (bitmap == null && boxSize != androidx.compose.ui.unit.IntSize.Zero) {
            Canvas(Modifier.fillMaxSize()) {
                val step = size.width / 12f
                val gridC = Color(0xFFE0E0E0)
                var x = 0f; while (x <= size.width)  { drawLine(gridC, Offset(x, 0f), Offset(x, size.height), 0.5f); x += step }
                var y = 0f; while (y <= size.height) { drawLine(gridC, Offset(0f, y), Offset(size.width, y), 0.5f); y += step }
            }
        }

        // ── 2. Heatmap IDW — clip arrondi par pièce (corners = AppShape.Small) ───
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerPx = 8.dp.toPx()   // = AppShape.Small radius

            rooms.forEach { room ->
                val grid = roomGrids[room.id] ?: return@forEach
                if (!grid.hasValues()) return@forEach
                val cellW = size.width  / grid.size
                val cellH = size.height / grid.size

                val roomL = room.bounds.left   * size.width
                val roomT = room.bounds.top    * size.height
                val roomR = room.bounds.right  * size.width
                val roomB = room.bounds.bottom * size.height

                // Clip arrondi = même forme que la bordure de la pièce
                drawContext.canvas.save()
                drawContext.canvas.clipPath(
                    Path().apply {
                        addRoundRect(
                            RoundRect(roomL, roomT, roomR, roomB, CornerRadius(cornerPx))
                        )
                    }
                )

                for (row in 0 until grid.size) {
                    for (col in 0 until grid.size) {
                        val rssi = grid.valueAt(row, col)
                        if (rssi.isNaN()) continue

                        val cl = col * cellW;  val cr = cl + cellW
                        val ct = row * cellH;  val cb = ct + cellH

                        // Intersection cellule ∩ pièce (optimisation : réduit les drawRect)
                        val dl = maxOf(cl, roomL); val dr = minOf(cr, roomR)
                        val dt = maxOf(ct, roomT); val db = minOf(cb, roomB)
                        if (dr <= dl || db <= dt) continue

                        drawRect(
                            color   = rssiToColor(rssi).copy(alpha = 0.65f),
                            topLeft = Offset(dl, dt),
                            size    = Size(dr - dl, db - dt)
                        )
                    }
                }

                drawContext.canvas.restore()
            }
        }

        // ── 3. Contours + labels de TOUTES les pièces ───────────────────────
        if (boxSize != androidx.compose.ui.unit.IntSize.Zero) {
            rooms.forEach { room ->
                val hasData = roomGrids[room.id]?.hasValues() == true
                val l = (room.bounds.left   * boxSize.width).toInt()
                val t = (room.bounds.top    * boxSize.height).toInt()
                val w = with(density) { ((room.bounds.right  - room.bounds.left) * boxSize.width).toDp() }
                val h = with(density) { ((room.bounds.bottom - room.bounds.top)  * boxSize.height).toDp() }

                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(l, t) }
                        .size(w, h)
                        .then(
                            if (!hasData)
                                Modifier.background(Color(0xFFE5E5EA).copy(alpha = 0.60f), AppShape.Small)
                            else Modifier
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (hasData) Color.White.copy(alpha = 0.85f)
                                    else Color(0xFFAEAEB2).copy(alpha = 0.7f),
                            shape = AppShape.Small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Label centré avec fond semi-transparent pour lisibilité
                    Text(
                        text  = room.label,
                        style = AppType.Micro,
                        color = if (hasData) Color.White else Color(0xFF6E6E73),
                        modifier = Modifier
                            .background(
                                color = if (hasData) Color.Black.copy(alpha = 0.35f)
                                        else Color.Transparent,
                                shape = AppShape.Small
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // ── 4. Points de mesure : petits et translucides — la couleur de pièce
        //       (synthèse) reste la lecture principale, les points sont des repères.
        if (boxSize != androidx.compose.ui.unit.IntSize.Zero) {
            measurementDots.forEach { dot ->
                val dotDp  = 8.dp
                val dotPx  = with(density) { dotDp.toPx() }
                val ox = (dot.x * boxSize.width  - dotPx / 2).toInt()
                val oy = (dot.y * boxSize.height - dotPx / 2).toInt()
                val dotColor = when (dot.quality) {
                    SignalQuality.GOOD -> AppColors.SignalGood
                    SignalQuality.FAIR -> AppColors.SignalFair
                    SignalQuality.POOR -> AppColors.SignalPoor
                }
                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(ox, oy) }
                        .size(dotDp)
                        .background(dotColor.copy(alpha = 0.55f), AppShape.Circle)
                        .border(1.dp, Color.White.copy(alpha = 0.9f), AppShape.Circle)
                )
            }

            // ── 5. Équipements (cliquables pour afficher leur heatmap) ────────
            gatewayPosition?.let { gw ->
                EquipmentPin(
                    x = gw.x, y = gw.y,
                    color = AppColors.Accent,
                    icon = Icons.Outlined.Router,
                    boxSize = boxSize, density = density,
                    isSelected = selectedDeviceId == "gateway",
                    onClick = { onDeviceClick("gateway") }
                )
            }
            repeaterPositions.forEach { rep ->
                EquipmentPin(
                    x = rep.position.x, y = rep.position.y,
                    color = AppColors.SignalFair,
                    icon = Icons.Outlined.SettingsInputAntenna,
                    boxSize = boxSize, density = density,
                    isSelected = selectedDeviceId == rep.id,
                    onClick = { onDeviceClick(rep.id) }
                )
            }
        }
    }
}

@Composable
private fun EquipmentPin(
    x: Float, y: Float,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    boxSize: androidx.compose.ui.unit.IntSize,
    density: androidx.compose.ui.unit.Density,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val sizeDp = 32.dp
    val sizePx = with(density) { sizeDp.toPx() }
    val ox = (x * boxSize.width  - sizePx / 2).toInt()
    val oy = (y * boxSize.height - sizePx / 2).toInt()
    val bgColor   = if (isSelected) color else Color.White.copy(alpha = 0.92f)
    val iconColor = if (isSelected) Color.White else color
    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(ox, oy) }
            .size(sizeDp)
            .background(bgColor, AppShape.Circle)
            .border(2.dp, color, AppShape.Circle)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
    }
}

/**
 * Gradient continu RSSI → couleur.
 * Chaque valeur dBm produit une teinte unique par interpolation entre points d'ancrage.
 *
 * Points d'ancrage (du meilleur au pire signal) :
 *  -20 dBm → vert vif        (excellent, proche de la box)
 *  -55 dBm → vert moyen      (seuil GOOD/FAIR)
 *  -63 dBm → jaune-vert      (zone intermédiaire)
 *  -72 dBm → orange          (seuil FAIR/POOR)
 *  -85 dBm → rouge           (mauvais signal)
 * -100 dBm → rouge foncé     (signal très faible)
 */
private val RSSI_STOPS = listOf(
    -20f  to Color(0xFF00C853),  // vert brillant
    -55f  to Color(0xFF1D9E75),  // vert moyen  (≡ AppColors.SignalGood)
    -63f  to Color(0xFFBDD00E),  // jaune-vert  (transition)
    -72f  to Color(0xFFEF9F27),  // orange      (≡ AppColors.SignalFair)
    -85f  to Color(0xFFE24B4A),  // rouge       (≡ AppColors.SignalPoor)
    -100f to Color(0xFF7B0000),  // rouge foncé
)

private fun rssiToColor(rssi: Float): Color {
    val v = rssi.coerceIn(-100f, -20f)
    for (i in 0 until RSSI_STOPS.size - 1) {
        val (r1, c1) = RSSI_STOPS[i]
        val (r2, c2) = RSSI_STOPS[i + 1]
        if (v >= r2) {
            // t = 0 → couleur r1 (meilleur), t = 1 → couleur r2 (pire)
            val t = (r1 - v) / (r1 - r2)
            return lerp(c1, c2, t.coerceIn(0f, 1f))
        }
    }
    return RSSI_STOPS.last().second
}

// ─── Zone hors-plan (résultats) ──────────────────────────────────────────────

@Composable
private fun HorsPlanResultsZone(
    gatewayOffPlan: Boolean,
    offPlanRepeaters: List<RepeaterPosition>,
    repeaterStartIndex: Int,
    selectedDeviceId: String?,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .border(1.5.dp, AppColors.BorderSoft, AppShape.Medium),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS)
        ) {
            if (gatewayOffPlan) {
                HorsPlanDevicePin(
                    icon       = Icons.Outlined.Router,
                    color      = AppColors.Accent,
                    isSelected = selectedDeviceId == "gateway",
                    onClick    = { onDeviceClick("gateway") }
                )
            }
            offPlanRepeaters.forEachIndexed { i, rep ->
                HorsPlanDevicePin(
                    icon       = Icons.Outlined.SettingsInputAntenna,
                    color      = AppColors.SignalFair,
                    index      = repeaterStartIndex + i,
                    isSelected = selectedDeviceId == rep.id,
                    onClick    = { onDeviceClick(rep.id) }
                )
            }
            Spacer(Modifier.width(AppSpacing.XS))
            Text("Hors plan", style = AppType.ControlLabel, color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun HorsPlanDevicePin(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    index: Int? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val containerDp = 40.dp
    val innerDp     = 32.dp
    val bgColor     = if (isSelected) color else Color.White.copy(alpha = 0.92f)
    val iconColor   = if (isSelected) Color.White else color
    Box(modifier = Modifier.size(containerDp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(innerDp)
                .background(bgColor, AppShape.Circle)
                .border(2.dp, color, AppShape.Circle)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
        }
        if (index != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val pad = ((containerDp - innerDp) / 2).roundToPx()
                        IntOffset(pad, pad - 8.dp.roundToPx())
                    }
                    .size(14.dp)
                    .background(Color(0xFFAEAEB2), AppShape.Circle)
                    .border(1.5.dp, Color.White, AppShape.Circle),
                contentAlignment = Alignment.Center
            ) {
                Text("$index", color = Color.White, style = AppType.Micro)
            }
        }
    }
}

// ─── Sélecteur de bande ──────────────────────────────────────────────────────

@Composable
private fun BandSelector(
    availableBands: List<String>,
    selectedBand: String?,
    onBandSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        item {
            BandChip(
                label    = "Toutes les bandes",
                selected = selectedBand == null,
                onClick  = { onBandSelected(null) }
            )
        }
        items(availableBands) { band ->
            BandChip(
                label    = band.replace("GHz", " GHz"),
                selected = selectedBand == band,
                onClick  = { onBandSelected(band) }
            )
        }
    }
}

@Composable
private fun BandChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, style = AppType.ControlLabel) },
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppColors.Accent,
            selectedLabelColor     = AppColors.OnAccent,
            containerColor         = AppColors.Surface,
            labelColor             = AppColors.TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled             = true,
            selected            = selected,
            borderColor         = AppColors.BorderSoft,
            selectedBorderColor = AppColors.Accent,
            borderWidth         = 1.dp,
            selectedBorderWidth = 1.dp
        ),
        shape = AppShape.Pill
    )
}

// ─── Légende heatmap ─────────────────────────────────────────────────────────

@Composable
private fun HeatmapLegend(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.XS)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.LG)) {
            LegendItem(color = AppColors.SignalGood, label = "Signal fort")
            LegendItem(color = AppColors.SignalFair, label = "Signal moyen")
            LegendItem(color = AppColors.SignalPoor, label = "Signal faible")
        }
        Text(
            "Couleur de pièce : niveau global · Points : vos mesures individuelles",
            style = AppType.Micro, color = AppColors.TextMeta
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, AppShape.Circle)
        )
        Text(label, style = AppType.Micro, color = AppColors.TextMuted)
    }
}

// ─── Score global ─────────────────────────────────────────────────────────────

@Composable
private fun OverallScoreCard(score: OverallScore, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val (bg, textColor, icon) = when (score) {
        OverallScore.GOOD -> Triple(Color(0xFFE1F5EE), Color(0xFF085041), Icons.Outlined.CheckCircle)
        OverallScore.FAIR -> Triple(Color(0xFFFAEEDA), Color(0xFF633806), Icons.Outlined.Info)
        OverallScore.POOR -> Triple(Color(0xFFFCEBEB), Color(0xFF791F1F), Icons.Outlined.Warning)
    }

    val targetProgress = when (score) {
        OverallScore.GOOD -> 1f
        OverallScore.FAIR -> 0.66f
        OverallScore.POOR -> 0.33f
    }

    // Ring animates from 0 → target after a brief settle delay.
    var animTarget by remember { mutableStateOf(if (reducedMotion) targetProgress else 0f) }
    LaunchedEffect(targetProgress) {
        if (!reducedMotion) {
            delay(300)
            animTarget = targetProgress
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue   = animTarget,
        animationSpec = tween(durationMillis = 900),
        label         = "scoreRing"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, AppShape.Large)
            .padding(AppSpacing.LG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Couverture globale", style = AppType.ControlLabel, color = textColor.copy(alpha = 0.7f))
            Text(score.toUserLabel(), style = AppType.CardTitle, color = textColor)
        }
        // Score visuel : anneau de progression par niveau, icône au centre.
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress  = { animatedProgress },
                modifier  = Modifier.fillMaxSize(),
                color     = textColor,
                trackColor = textColor.copy(alpha = 0.15f),
                strokeWidth = 4.dp
            )
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
        }
    }
}

// ─── Badges par pièce ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomBadgesGrid(rooms: List<RoomResult>, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.SM)
    ) {
        rooms.forEachIndexed { index, room ->
            // Each badge fades + scales in with a 60 ms stagger.
            var visible by remember(room.name) { mutableStateOf(reducedMotion) }
            LaunchedEffect(room.name) {
                if (!reducedMotion) {
                    delay(index * 60L)
                    visible = true
                }
            }
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f)
            ) {
                RoomBadge(room = room)
            }
        }
    }
}

@Composable
private fun RoomBadge(room: RoomResult) {
    val (bg, textColor, symbol) = when {
        !room.hasData                        -> Triple(Color(0xFFF2F2F7), Color(0xFF8E8E93), "○")
        room.quality == SignalQuality.GOOD   -> Triple(Color(0xFFE1F5EE), Color(0xFF085041), "✓")
        room.quality == SignalQuality.FAIR   -> Triple(Color(0xFFFAEEDA), Color(0xFF633806), "⚠")
        else                                 -> Triple(Color(0xFFFCEBEB), Color(0xFF791F1F), "✕")
    }

    Row(
        modifier = Modifier
            .background(bg, AppShape.Medium)
            .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS)
    ) {
        Text(symbol, color = textColor, style = AppType.BodyEmphasis)
        Text(room.name, color = textColor, style = AppType.BodyPrimary)
        if (!room.hasData) {
            Text("· non mesurée", color = textColor.copy(alpha = 0.85f), style = AppType.Micro)
        }
    }
}

// ─── Liste de recommandations ─────────────────────────────────────────────────

@Composable
private fun RecommendationsList(recommendations: List<Recommendation>) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
        recommendations.forEach { rec -> RecommendationItem(rec) }
    }
}

@Composable
private fun RecommendationItem(rec: Recommendation) {
    val accentColor = when (rec.severity) {
        Severity.HIGH   -> AppColors.SignalPoor
        Severity.MEDIUM -> AppColors.SignalFair
        Severity.LOW    -> AppColors.SignalGood
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface, AppShape.Medium)
            .padding(AppSpacing.LG),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
    ) {
        Box(
            modifier = Modifier
                .size(3.dp, 40.dp)
                .background(accentColor, AppShape.Pill)
        )
        Text(
            text = rec.message,
            style = AppType.BodyPrimary,
            color = AppColors.TextPrimary
        )
    }
}
