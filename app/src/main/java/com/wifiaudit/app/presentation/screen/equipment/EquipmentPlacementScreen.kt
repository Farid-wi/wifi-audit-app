package com.wifiaudit.app.presentation.screen.equipment

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
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

    if (uiState.showSaveDialog) {
        SavePlanDialog(
            onDismiss = viewModel::dismissSaveDialog,
            onConfirm = { name ->
                viewModel.savePlan(
                    name          = name,
                    planImagePath = creationState.planImagePath ?: "",
                    rooms         = creationState.rooms,
                    gateway       = uiState.gatewayPosition!!,
                    repeaters     = uiState.repeaterPositions
                )
            }
        )
    }

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
                    "Où se trouve votre box ?" to "Appuyez sur le plan à l'endroit où elle est placée."
                !uiState.repeaterConfirmed ->
                    "Avez-vous un répéteur Wi-Fi ?" to "Touchez à nouveau le plan pour déplacer la box. Un répéteur amplifie le signal dans les zones éloignées."
                else ->
                    "Où est votre répéteur ?" to "Appuyez sur le plan pour le positionner."
            }
            Text(title, style = AppType.CardTitle, color = AppColors.TextPrimary)
            Spacer(Modifier.height(AppSpacing.XS))
            Text(instruction, style = AppType.BodyPrimary, color = AppColors.TextMuted)
        }

        Box(modifier = Modifier.weight(1f)) {
            EquipmentPlanView(
                planImagePath = creationState.planImagePath,
                rooms         = creationState.rooms,
                uiState       = uiState,
                onTap         = { x, y ->
                    when {
                        // Tant que le répéteur n'est pas confirmé, un tap (re)place la box → déplaçable.
                        !uiState.repeaterConfirmed -> viewModel.placeGateway(x, y)
                        else                       -> viewModel.addRepeater(x, y)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.SM)
        ) {
            // Bouton "Enregistrer ce plan" disponible dès que la GW est placée
            if (uiState.gatewayPosition != null) {
                AnimatedVisibility(visible = uiState.planSaved, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null,
                             tint = AppColors.SignalGood, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(AppSpacing.XS))
                        Text("Plan enregistré", style = AppType.ControlLabel, color = AppColors.SignalGood)
                    }
                }
                OutlinedButton(
                    onClick  = viewModel::showSaveDialog,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = AppShape.Pill,
                    border   = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Accent)
                ) {
                    Icon(Icons.Outlined.BookmarkBorder, null,
                         tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(AppSpacing.XS))
                    Text("Enregistrer ce plan", style = AppType.BodyEmphasis, color = AppColors.Accent)
                }
            }

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
                            onNext()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Non, continuer sans répéteur", style = AppType.BodyEmphasis, color = AppColors.TextMuted)
                    }
                }

                uiState.repeaterPositions.isNotEmpty() -> {
                    Button(
                        onClick = {
                            auditCreationViewModel.setGatewayPosition(
                                uiState.gatewayPosition!!.first,
                                uiState.gatewayPosition!!.second
                            )
                            uiState.repeaterPositions.forEach { (x, y) ->
                                auditCreationViewModel.addRepeater(x, y)
                            }
                            onNext()
                        },
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = AppShape.Pill,
                        colors    = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        val n = uiState.repeaterPositions.size
                        Text(
                            "Continuer ($n répéteur${if (n > 1) "s" else ""})",
                            style = AppType.BodyEmphasis, color = AppColors.OnAccent
                        )
                    }
                    TextButton(
                        onClick  = viewModel::removeLastRepeater,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retirer le dernier répéteur", style = AppType.BodyEmphasis, color = AppColors.TextMuted)
                    }
                }
            }
        }
    }
}

// ─── Dialog : saisie du nom du plan ──────────────────────────────────────────

@Composable
private fun SavePlanDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Enregistrer ce plan", style = AppType.CardTitle, color = AppColors.TextPrimary) },
        text    = {
            OutlinedTextField(
                value           = name,
                onValueChange   = { name = it },
                placeholder     = { Text("Nom du plan", style = AppType.BodyPrimary, color = AppColors.TextMeta) },
                singleLine      = true,
                shape           = AppShape.Medium,
                modifier        = Modifier.fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) })
            )
        },
        confirmButton = {
            Button(
                onClick   = { onConfirm(name) },
                enabled   = name.isNotBlank(),
                shape     = AppShape.Pill,
                colors    = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) { Text("Enregistrer", style = AppType.BodyEmphasis, color = AppColors.OnAccent) }
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

// ─── Vue du plan avec équipements ────────────────────────────────────────────

@Composable
private fun EquipmentPlanView(
    planImagePath: String?,
    rooms: List<CanvasRoom>,
    uiState: EquipmentPlacementUiState,
    onTap: (x: Float, y: Float) -> Unit,
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

        uiState.gatewayPosition?.let { (gx, gy) ->
            EquipmentIcon(x = gx, y = gy, type = EquipmentType.GATEWAY,
                          imageSize = planSize, density = density)
        }
        uiState.repeaterPositions.forEach { (rx, ry) ->
            EquipmentIcon(x = rx, y = ry, type = EquipmentType.REPEATER,
                          imageSize = planSize, density = density)
        }
    }
}

@Composable
private fun CanvasRoomsPlan(rooms: List<CanvasRoom>, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .background(Color.White)
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
                        .background(color.copy(alpha = 0.14f), AppShape.Small)
                        .border(1.dp, color.copy(alpha = 0.50f), AppShape.Small),
                    contentAlignment = Alignment.Center
                ) {
                    Text(room.label, style = AppType.ControlLabel,
                         color = color, modifier = Modifier.padding(horizontal = 4.dp))
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

@Composable
private fun EquipmentIcon(
    x: Float, y: Float,
    type: EquipmentType,
    imageSize: IntSize,
    density: androidx.compose.ui.unit.Density
) {
    if (imageSize == IntSize.Zero) return
    val sizeDp = 32.dp
    val sizePx = with(density) { sizeDp.toPx() }
    val ox = (x * imageSize.width  - sizePx / 2).roundToInt()
    val oy = (y * imageSize.height - sizePx / 2).roundToInt()

    val (icon, color) = when (type) {
        EquipmentType.GATEWAY   -> Icons.Outlined.Router              to AppColors.Accent
        EquipmentType.REPEATER  -> Icons.Outlined.SettingsInputAntenna to AppColors.SignalFair
        EquipmentType.MESH_NODE -> Icons.Outlined.Hub                 to AppColors.SignalGood
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(ox, oy) }
            .size(sizeDp)
            .background(Color.White, AppShape.Circle)
            .border(2.dp, color, AppShape.Circle),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}
