package com.wifiaudit.app.presentation.screen.equipment

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.wifiaudit.app.domain.model.EquipmentType
import com.wifiaudit.app.domain.model.RoomType
import com.wifiaudit.app.presentation.AuditCreationViewModel
import com.wifiaudit.app.presentation.screen.common.StepHeader
import com.wifiaudit.app.presentation.screen.common.planBackdrop
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing
import com.wifiaudit.app.presentation.theme.AppType
import kotlin.math.roundToInt

@Composable
fun EquipmentPlacementScreen(
    auditCreationViewModel: AuditCreationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: EquipmentPlacementViewModel = hiltViewModel()
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val creationState by auditCreationViewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .systemBarsPadding()
    ) {
        StepHeader(currentStep = 2, onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.MD)) {
            val (title, instruction) = when {
                uiState.gatewayPosition == null ->
                    "Placez votre box internet sur le plan" to
                    "Appuyez sur le plan à l'endroit où elle se trouve. Si elle n'est pas sur ce plan, placez-la dans le cadre en bas."
                !uiState.repeaterConfirmed ->
                    "Avez-vous un répéteur Wi-Fi ?" to "Glissez la box pour l'ajuster. Un répéteur amplifie le signal dans les zones éloignées."
                else ->
                    "Où est votre répéteur ?" to "Appuyez pour ajouter un répéteur. Glissez pour le déplacer, touchez ✕ pour le retirer."
            }
            Text(title, style = AppType.CardTitle, color = AppColors.TextPrimary)
            Spacer(Modifier.height(AppSpacing.XS))
            Text(instruction, style = AppType.BodyPrimary, color = AppColors.TextMuted)
        }

        Box(modifier = Modifier.weight(1f).padding(horizontal = AppSpacing.LG).padding(14.dp)) {
            EquipmentPlanView(
                planImagePath = creationState.planImagePath,
                rooms         = creationState.rooms,
                uiState       = uiState,
                onTap         = { x, y ->
                    when {
                        !uiState.repeaterConfirmed -> viewModel.placeGateway(x, y)
                        else                       -> viewModel.addRepeater(x, y)
                    }
                },
                onMoveGateway    = viewModel::moveGatewayBy,
                onMoveRepeater   = viewModel::moveRepeaterBy,
                onRemoveRepeater = viewModel::removeRepeater,
                modifier = Modifier.fillMaxSize()
            )
        }

        OffFloorGatewayZone(
            gatewayPlaced        = uiState.gatewayOnDifferentFloor,
            offFloorRepeaterCount = uiState.offFloorRepeaterCount,
            onTap = {
                if (!uiState.repeaterConfirmed) viewModel.placeGatewayOffFloor()
                else viewModel.addRepeaterOffFloor()
            },
            onRemoveRepeater = viewModel::removeOffFloorRepeater,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.LG)
                .padding(horizontal = 14.dp)
                .padding(bottom = AppSpacing.SM)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.SM)
        ) {
            when {
                uiState.gatewayPosition == null -> {}

                !uiState.repeaterConfirmed -> {
                    Button(
                        onClick   = viewModel::confirmHasRepeater,
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = AppShape.Pill,
                        colors    = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text("Oui, j'ai un répéteur", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
                    }
                    TextButton(
                        onClick = {
                            auditCreationViewModel.setGatewayPosition(
                                uiState.gatewayPosition!!.first,
                                uiState.gatewayPosition!!.second
                            )
                            auditCreationViewModel.setRepeaters(emptyList())
                            onNext()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Non, continuer sans répéteur", style = AppType.BodyEmphasis, color = AppColors.TextMuted)
                    }
                }

                uiState.repeaterPositions.isNotEmpty() || uiState.offFloorRepeaterCount > 0 -> {
                    Button(
                        onClick = {
                            auditCreationViewModel.setGatewayPosition(
                                uiState.gatewayPosition!!.first,
                                uiState.gatewayPosition!!.second
                            )
                            val offFloorPairs = List(uiState.offFloorRepeaterCount) { -1f to -1f }
                            auditCreationViewModel.setRepeaters(uiState.repeaterPositions + offFloorPairs)
                            onNext()
                        },
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = AppShape.Pill,
                        colors    = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        val n = uiState.repeaterPositions.size + uiState.offFloorRepeaterCount
                        Text(
                            "Continuer ($n répéteur${if (n > 1) "s" else ""})",
                            style = AppType.BodyEmphasis, color = AppColors.OnAccent
                        )
                    }
                }
            }
        }
    }
}

// ─── Vue du plan avec équipements ────────────────────────────────────────────

@Composable
private fun EquipmentPlanView(
    planImagePath: String?,
    rooms: List<CanvasRoom>,
    uiState: EquipmentPlacementUiState,
    onTap: (x: Float, y: Float) -> Unit,
    onMoveGateway: (dx: Float, dy: Float) -> Unit,
    onMoveRepeater: (index: Int, dx: Float, dy: Float) -> Unit,
    onRemoveRepeater: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(planImagePath) {
        planImagePath?.takeIf { it.isNotEmpty() }
            ?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        panOffset += pan * scale
    }
    var planSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .transformable(state = transformState)
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = panOffset.x; translationY = panOffset.y }
            .pointerInput(uiState) {
                detectTapGestures { offset ->
                    if (planSize != IntSize.Zero) {
                        onTap(
                            (offset.x / planSize.width).coerceIn(0f, 1f),
                            (offset.y / planSize.height).coerceIn(0f, 1f)
                        )
                    }
                }
            }
            .onSizeChanged { planSize = it }
    ) {
        when {
            bitmap != null ->
                Image(bitmap = bitmap, contentDescription = "Plan",
                      contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            rooms.isNotEmpty() ->
                CanvasRoomsPlan(rooms = rooms, modifier = Modifier.fillMaxSize())
            else ->
                Box(Modifier.fillMaxSize().background(AppColors.Surface),
                    contentAlignment = Alignment.Center) {
                    Text("Aucun plan disponible", style = AppType.BodyPrimary, color = AppColors.TextMuted)
                }
        }

        if (uiState.gatewayPosition != null && !uiState.gatewayOnDifferentFloor) {
            val (gx, gy) = uiState.gatewayPosition
            DraggableEquipmentPin(
                x = gx, y = gy, type = EquipmentType.GATEWAY,
                imageSize = planSize, density = density,
                onMoveBy = onMoveGateway
            )
        }
        uiState.repeaterPositions.forEachIndexed { index, (rx, ry) ->
            DraggableEquipmentPin(
                x = rx, y = ry, type = EquipmentType.REPEATER,
                imageSize = planSize, density = density,
                onMoveBy = { dx, dy -> onMoveRepeater(index, dx, dy) },
                onDelete = { onRemoveRepeater(index) }
            )
        }
    }
}

@Composable
private fun CanvasRoomsPlan(rooms: List<CanvasRoom>, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .planBackdrop()
            .onSizeChanged { size = it }
    ) {
        if (size != IntSize.Zero) {
            rooms.forEach { room ->
                val color = equipRoomColor(room.type)
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
                    Text(room.label, style = AppType.ControlLabel,
                         color = color, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun OffFloorGatewayZone(
    gatewayPlaced: Boolean,
    offFloorRepeaterCount: Int,
    onTap: () -> Unit,
    onRemoveRepeater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEmpty = !gatewayPlaced && offFloorRepeaterCount == 0
    Box(
        modifier = modifier
            .height(64.dp)
            .border(1.5.dp, Color.Black, AppShape.Medium)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center
    ) {
        if (isEmpty) {
            Text("Hors plan", style = AppType.ControlLabel, color = AppColors.TextMuted)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS)
            ) {
                if (gatewayPlaced) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White, AppShape.Circle)
                            .border(2.dp, AppColors.Accent, AppShape.Circle)
                            .pointerInput(Unit) { detectTapGestures { } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Router, contentDescription = null,
                             tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                    }
                }
                repeat(offFloorRepeaterCount) { index ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White, AppShape.Circle)
                                    .border(2.dp, AppColors.SignalFair, AppShape.Circle)
                                    .pointerInput(Unit) { detectTapGestures { } },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.SettingsInputAntenna, contentDescription = null,
                                     tint = AppColors.SignalFair, modifier = Modifier.size(18.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset { IntOffset(0, (-8).dp.roundToPx()) }
                                    .size(14.dp)
                                    .background(AppColors.SignalPoor, AppShape.Circle)
                                    .border(1.5.dp, Color.White, AppShape.Circle)
                                    .pointerInput(Unit) { detectTapGestures { onRemoveRepeater() } },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = null,
                                     tint = Color.White, modifier = Modifier.size(8.dp))
                            }
                        }
                        Text(
                            "${index + 1}",
                            style = AppType.ControlLabel,
                            color = AppColors.TextMuted
                        )
                    }
                }
            }
        }
    }
}

private fun equipRoomColor(type: RoomType): Color = when (type) {
    RoomType.SALON    -> Color(0xFF5AC8FA)
    RoomType.KITCHEN  -> Color(0xFFFF9500)
    RoomType.BEDROOM  -> Color(0xFF5E5CE6)
    RoomType.OFFICE   -> Color(0xFF30B0C7)
    RoomType.BATHROOM -> Color(0xFF34C759)
    RoomType.HALLWAY  -> Color(0xFFAEAEB2)
    RoomType.DINING   -> Color(0xFFFF6B6B)
    RoomType.OTHER    -> Color(0xFF8E8E93)
}

/**
 * Pin d'équipement déplaçable sur le plan (drag). Les répéteurs reçoivent en plus un badge ✕
 * pour une suppression individuelle. On consomme les gestes pour ne pas déclencher le tap "placer"
 * du plan situé en dessous.
 *
 * [onMoveBy] reçoit un déplacement RELATIF normalisé (dx, dy) à chaque cran du drag — pas de
 * position absolue, ce qui évite tout décalage si le composable se recompose pendant le glissement.
 */
@Composable
private fun DraggableEquipmentPin(
    x: Float, y: Float,
    type: EquipmentType,
    imageSize: IntSize,
    density: androidx.compose.ui.unit.Density,
    onMoveBy: (dx: Float, dy: Float) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    if (imageSize == IntSize.Zero) return
    val sizeDp = 32.dp
    val sizePx = with(density) { sizeDp.toPx() }
    val ox = (x * imageSize.width  - sizePx / 2).roundToInt()
    val oy = (y * imageSize.height - sizePx / 2).roundToInt()

    val (icon, color) = when (type) {
        EquipmentType.GATEWAY   -> Icons.Outlined.Router               to AppColors.Accent
        EquipmentType.REPEATER  -> Icons.Outlined.SettingsInputAntenna to AppColors.SignalFair
        EquipmentType.MESH_NODE -> Icons.Outlined.Hub                  to AppColors.SignalGood
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(ox, oy) }
            .size(sizeDp)
            .pointerInput(type, imageSize) {
                detectDragGestures { change, drag ->
                    change.consume()
                    if (imageSize.width > 0 && imageSize.height > 0) {
                        onMoveBy(drag.x / imageSize.width, drag.y / imageSize.height)
                    }
                }
            }
            // Absorbe le tap pour éviter d'ajouter un équipement sous le pin.
            .pointerInput(type) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .background(Color.White, AppShape.Circle)
                .border(2.dp, color, AppShape.Circle),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }

        if (onDelete != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .background(AppColors.SignalPoor, AppShape.Circle)
                    .border(2.dp, Color.White, AppShape.Circle)
                    .pointerInput(Unit) { detectTapGestures { onDelete() } },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Retirer ce répéteur",
                     tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}
