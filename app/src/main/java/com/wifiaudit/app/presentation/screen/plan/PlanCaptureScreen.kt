package com.wifiaudit.app.presentation.screen.plan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.RoomBounds
import com.wifiaudit.app.domain.model.RoomType
import com.wifiaudit.app.domain.model.RepeaterPosition
import com.wifiaudit.app.domain.model.SavedPlan
import com.wifiaudit.app.presentation.AuditCreationViewModel
import com.wifiaudit.app.presentation.screen.common.StepHeader
import com.wifiaudit.app.presentation.theme.AppColors
import com.wifiaudit.app.presentation.theme.AppShape
import com.wifiaudit.app.presentation.theme.AppSpacing
import com.wifiaudit.app.presentation.theme.AppType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlanCaptureScreen(
    auditCreationViewModel: AuditCreationViewModel,
    onNext: () -> Unit,
    viewModel: PlanCaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val photoFile = remember { createPlanFile(context) }
    val photoUri  = remember(photoFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) viewModel.onPhotoCaptured(photoFile.absolutePath)
        else viewModel.onPhotoFailed()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) cameraLauncher.launch(photoUri) }

    fun launchCameraWithPermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) cameraLauncher.launch(photoUri)
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Geste système Retour : depuis une sous-étape, revenir au choix initial plutôt que de
    // quitter l'écran Plan (sinon l'utilisateur perd son travail en cours).
    BackHandler(enabled = uiState.step != PlanStep.OPTION_PICKER) {
        viewModel.backToPicker()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .systemBarsPadding()
    ) {
        StepHeader(
            currentStep = 1,
            // Pas de retour depuis l'écran d'accueil ; sinon retour vers le choix initial.
            onBack = if (uiState.step == PlanStep.OPTION_PICKER) null else viewModel::backToPicker
        )

        when (uiState.step) {
            PlanStep.OPTION_PICKER ->
                OptionPickerStep(
                    savedPlans       = uiState.savedPlans,
                    onCanvasSelected = viewModel::onCanvasOptionSelected,
                    onLoadPlan       = viewModel::loadSavedPlan,
                    onDeletePlan     = viewModel::deleteSavedPlan
                )

            PlanStep.CANVAS_BUILDER ->
                CanvasBuilderStep(
                    rooms     = uiState.editableRooms,
                    onUpdate  = viewModel::onCanvasRoomsConfirmed,
                    onConfirm = {
                        auditCreationViewModel.setPlanImagePath(
                            path  = "",
                            rooms = uiState.editableRooms
                        )
                        onNext()
                    }
                )

            PlanStep.PHOTO_PREVIEW ->
                PhotoPreviewStep(onLaunchCamera = { launchCameraWithPermission() })

            PlanStep.ROOM_CONFIRMATION ->
                RoomConfirmationStep(
                    uiState   = uiState,
                    viewModel = viewModel,
                    onConfirm = {
                        auditCreationViewModel.setPlanImagePath(
                            path  = uiState.planImagePath ?: "",
                            rooms = uiState.editableRooms
                        )
                        onNext()
                    }
                )
        }
    }
}

// ─── Option picker ────────────────────────────────────────────────────────────

@Composable
private fun OptionPickerStep(
    savedPlans: List<SavedPlan>,
    onCanvasSelected: () -> Unit,
    onLoadPlan:       (SavedPlan) -> Unit,
    onDeletePlan:     (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.XXL),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.LG)
    ) {
        Spacer(Modifier.height(AppSpacing.Section))

        if (savedPlans.isNotEmpty()) {
            Text("Mes plans enregistrés", style = AppType.SectionTitle, color = AppColors.TextPrimary)
            Spacer(Modifier.height(AppSpacing.XS))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(savedPlans, key = { it.id }) { plan ->
                    SavedPlanCard(
                        plan     = plan,
                        onLoad   = { onLoadPlan(plan) },
                        onDelete = { onDeletePlan(plan.id) }
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.MD))
        }

        Text("Nouveau plan", style = AppType.SectionTitle, color = AppColors.TextPrimary)
        Text(
            "Créez le plan de votre logement en quelques gestes.",
            style = AppType.BodyPrimary, color = AppColors.TextMuted
        )
        Spacer(Modifier.height(AppSpacing.SM))

        PlanOptionCard(
            title       = "Dessiner mon plan",
            description = "Ajoutez vos pièces et placez-les directement dans l'app",
            icon        = Icons.Outlined.GridView,
            onClick     = onCanvasSelected,
            modifier    = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SavedPlanCard(
    plan: SavedPlan,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(plan.createdAt) {
        SimpleDateFormat("dd MMM", Locale.FRENCH).format(Date(plan.createdAt))
    }
    Column(
        modifier = Modifier
            .width(140.dp)
            .background(AppColors.Surface, AppShape.Large)
            .border(1.dp, AppColors.BorderSoft, AppShape.Large)
            .clickable(onClick = onLoad)
            .padding(AppSpacing.MD),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.XS)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(AppColors.Accent.copy(alpha = 0.10f), AppShape.Medium),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.GridView, null,
                    tint = AppColors.Accent, modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick  = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Outlined.Close, "Supprimer",
                    tint = AppColors.TextMuted, modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            plan.name,
            style    = AppType.BodyEmphasis,
            color    = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${plan.rooms.size} pièce${if (plan.rooms.size > 1) "s" else ""}  ·  $dateStr",
            style = AppType.ControlLabel, color = AppColors.TextMuted
        )
    }
}

@Composable
private fun PlanOptionCard(
    title: String, description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(AppColors.Surface, AppShape.Large)
            .border(1.dp, AppColors.BorderSoft, AppShape.Large)
            .clickable(onClick = onClick)
            .padding(AppSpacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(AppColors.Accent.copy(alpha = 0.10f), AppShape.Medium),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(26.dp))
        }
        Text(title, style = AppType.BodyEmphasis, color = AppColors.TextPrimary)
        Text(description, style = AppType.ControlLabel, color = AppColors.TextMuted,
             textAlign = TextAlign.Center)
    }
}

// ─── Canvas builder ───────────────────────────────────────────────────────────

private val ROOM_SLOTS = listOf(
    RoomBounds(0.03f, 0.03f, 0.47f, 0.33f),
    RoomBounds(0.53f, 0.03f, 0.97f, 0.33f),
    RoomBounds(0.03f, 0.37f, 0.47f, 0.67f),
    RoomBounds(0.53f, 0.37f, 0.97f, 0.67f),
    RoomBounds(0.03f, 0.71f, 0.47f, 0.97f),
    RoomBounds(0.53f, 0.71f, 0.97f, 0.97f),
)

private enum class Corner { TL, TR, BL, BR }

@Composable
private fun CanvasBuilderStep(
    rooms: List<CanvasRoom>,
    onUpdate: (List<CanvasRoom>) -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AppSpacing.LG)
        ) {
            Spacer(Modifier.height(AppSpacing.MD))
            Text(
                "Dessinez votre logement",
                style = AppType.CardTitle, color = AppColors.TextPrimary,
                modifier = Modifier.padding(horizontal = AppSpacing.SM)
            )
            Spacer(Modifier.height(AppSpacing.XS))
            Text(
                "Ajoutez une pièce · Touchez pour sélectionner · Glissez pour déplacer · Coins pour redimensionner",
                style = AppType.ControlLabel, color = AppColors.TextMuted,
                modifier = Modifier.padding(horizontal = AppSpacing.SM)
            )
            Spacer(Modifier.height(AppSpacing.MD))

            LazyRow(
                contentPadding = PaddingValues(horizontal = AppSpacing.SM),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)
            ) {
                items(RoomType.entries) { type ->
                    FilterChip(
                        selected = false,
                        onClick  = {
                            val slot = ROOM_SLOTS.getOrElse(rooms.size % ROOM_SLOTS.size) { ROOM_SLOTS[0] }
                            val shift = (rooms.size / ROOM_SLOTS.size) * 0.03f
                            val bounds = RoomBounds(
                                left   = (slot.left   + shift).coerceIn(0f, 0.9f),
                                top    = (slot.top    + shift).coerceIn(0f, 0.9f),
                                right  = (slot.right  + shift).coerceIn(0.1f, 1f),
                                bottom = (slot.bottom + shift).coerceIn(0.1f, 1f)
                            )
                            onUpdate(rooms + CanvasRoom(type = type, bounds = bounds))
                        },
                        label  = { Text(type.displayName, style = AppType.ControlLabel) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = AppColors.Surface),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = false,
                            borderColor = AppColors.BorderSoft, borderWidth = 1.dp
                        ),
                        shape  = AppShape.Pill
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.MD))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(14.dp)
            ) {
                RoomCanvas(
                    rooms    = rooms,
                    onUpdate = onUpdate,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Surface, AppShape.Large)
                        .border(1.dp, AppColors.BorderSoft, AppShape.Large)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG)
        ) {
            Button(
                onClick   = onConfirm,
                enabled   = rooms.isNotEmpty(),
                modifier  = Modifier.fillMaxWidth(),
                shape     = AppShape.Pill,
                colors    = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
            ) {
                Text(
                    if (rooms.isEmpty()) "Ajoutez au moins une pièce"
                    else "Continuer (${rooms.size} pièce${if (rooms.size > 1) "s" else ""})",
                    style = AppType.BodyEmphasis, color = AppColors.OnAccent
                )
            }
        }
    }
}

@Composable
private fun RoomCanvas(
    rooms: List<CanvasRoom>,
    onUpdate: (List<CanvasRoom>) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize  by remember { mutableStateOf(IntSize.Zero) }
    var selectedId  by remember { mutableStateOf<String?>(null) }
    var renamingId  by remember { mutableStateOf<String?>(null) }
    var renameText  by remember { mutableStateOf("") }
    val density     = LocalDensity.current

    val latestRooms    = rememberUpdatedState(rooms)
    val latestOnUpdate = rememberUpdatedState(onUpdate)

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (canvasSize == IntSize.Zero) return@detectTapGestures
                    val cx = offset.x / canvasSize.width
                    val cy = offset.y / canvasSize.height
                    if (latestRooms.value.none { it.bounds.contains(cx, cy) }) {
                        selectedId = null
                        renamingId = null
                    }
                }
            }
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val step = size.width / 10f
            val gridColor = Color(0xFFD1D1D6)
            var x = 0f; while (x <= size.width)  { drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f),    androidx.compose.ui.geometry.Offset(x, size.height), 0.5f); x += step }
            var y = 0f; while (y <= size.height) { drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y),    0.5f); y += step }
        }

        if (rooms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Appuyez sur un type de pièce ci-dessus",
                    style = AppType.BodyPrimary, color = AppColors.TextMuted)
            }
        }

        if (canvasSize != IntSize.Zero) {

            rooms.forEach { room ->
                val isSelected = room.id == selectedId
                val isRenaming = room.id == renamingId
                val color      = roomTypeColor(room.type)
                val lPx = (room.bounds.left   * canvasSize.width).toInt()
                val tPx = (room.bounds.top    * canvasSize.height).toInt()
                val wDp = with(density) { ((room.bounds.right  - room.bounds.left) * canvasSize.width).toDp() }
                val hDp = with(density) { ((room.bounds.bottom - room.bounds.top)  * canvasSize.height).toDp() }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(lPx, tPx) }
                        .size(wDp, hDp)
                        .background(color.copy(alpha = if (isSelected) 0.20f else 0.12f), AppShape.Small)
                        .border(if (isSelected) 2.dp else 1.dp,
                                if (isSelected) color else color.copy(alpha = 0.45f),
                                AppShape.Small)
                        .pointerInput(room.id, canvasSize) {
                            detectDragGestures(
                                onDragStart = { selectedId = room.id; renamingId = null }
                            ) { _, d ->
                                val r = latestRooms.value.firstOrNull { it.id == room.id } ?: return@detectDragGestures
                                val b = r.bounds; val w = b.right - b.left; val h = b.bottom - b.top
                                val dx = d.x / canvasSize.width; val dy = d.y / canvasSize.height
                                val nl = (b.left + dx).coerceIn(0f, 1f - w)
                                val nt = (b.top  + dy).coerceIn(0f, 1f - h)
                                latestOnUpdate.value(latestRooms.value.map { rm ->
                                    if (rm.id != room.id) rm
                                    else rm.copy(bounds = b.copy(left = nl, top = nt, right = nl + w, bottom = nt + h))
                                })
                            }
                        }
                        .pointerInput(room.id) {
                            detectTapGestures(
                                onTap       = { selectedId = room.id; renamingId = null },
                                onDoubleTap = { selectedId = room.id; renamingId = room.id; renameText = room.label }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRenaming) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        BasicTextField(
                            value           = renameText,
                            onValueChange   = { renameText = it },
                            singleLine      = true,
                            textStyle       = AppType.ControlLabel.copy(color = color, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val label = renameText.trim().ifEmpty { room.type.displayName }
                                latestOnUpdate.value(latestRooms.value.map { r ->
                                    if (r.id == room.id) r.copy(label = label) else r
                                })
                                renamingId = null
                            }),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(Color.White.copy(alpha = 0.92f), AppShape.Small)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .focusRequester(focusRequester)
                        )
                    } else {
                        Text(room.label, style = AppType.ControlLabel, color = color,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }

            val selected = rooms.firstOrNull { it.id == selectedId }
            if (selected != null) {
                val color = roomTypeColor(selected.type)
                val b     = selected.bounds
                val lPx   = (b.left   * canvasSize.width).toInt()
                val tPx   = (b.top    * canvasSize.height).toInt()
                val rPx   = (b.right  * canvasSize.width).toInt()
                val bPx   = (b.bottom * canvasSize.height).toInt()

                val delPx   = with(density) { 26.dp.toPx().toInt() }
                val centerX = (lPx + rPx) / 2
                Box(
                    modifier = Modifier
                        .offset { IntOffset(centerX - delPx / 2, tPx - delPx) }
                        .size(with(density) { delPx.toDp() })
                        .background(AppColors.SignalPoor, AppShape.Circle)
                        .pointerInput(selected.id) {
                            detectTapGestures {
                                latestOnUpdate.value(latestRooms.value.filter { it.id != selected.id })
                                selectedId = null; renamingId = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }

                listOf(
                    Corner.TL to IntOffset(lPx, tPx),
                    Corner.TR to IntOffset(rPx, tPx),
                    Corner.BL to IntOffset(lPx, bPx),
                    Corner.BR to IntOffset(rPx, bPx),
                ).forEach { (corner, pos) ->
                    val touchPx = with(density) { 36.dp.toPx().toInt() }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(pos.x - touchPx / 2, pos.y - touchPx / 2) }
                            .size(with(density) { touchPx.toDp() })
                            .pointerInput(selected.id, corner, canvasSize) {
                                detectDragGestures { _, d ->
                                    val r = latestRooms.value.firstOrNull { it.id == selected.id } ?: return@detectDragGestures
                                    val rb = r.bounds
                                    val dx = d.x / canvasSize.width
                                    val dy = d.y / canvasSize.height
                                    val newBounds = when (corner) {
                                        Corner.TL -> rb.copy(
                                            left = (rb.left + dx).coerceIn(0f, rb.right  - RoomBounds.MIN_SIZE),
                                            top  = (rb.top  + dy).coerceIn(0f, rb.bottom - RoomBounds.MIN_SIZE)
                                        )
                                        Corner.TR -> rb.copy(
                                            right = (rb.right + dx).coerceIn(rb.left + RoomBounds.MIN_SIZE, 1f),
                                            top   = (rb.top   + dy).coerceIn(0f, rb.bottom - RoomBounds.MIN_SIZE)
                                        )
                                        Corner.BL -> rb.copy(
                                            left   = (rb.left   + dx).coerceIn(0f, rb.right - RoomBounds.MIN_SIZE),
                                            bottom = (rb.bottom + dy).coerceIn(rb.top + RoomBounds.MIN_SIZE, 1f)
                                        )
                                        Corner.BR -> rb.copy(
                                            right  = (rb.right  + dx).coerceIn(rb.left + RoomBounds.MIN_SIZE, 1f),
                                            bottom = (rb.bottom + dy).coerceIn(rb.top + RoomBounds.MIN_SIZE, 1f)
                                        )
                                    }
                                    latestOnUpdate.value(latestRooms.value.map { rm ->
                                        if (rm.id != selected.id) rm else rm.copy(bounds = newBounds)
                                    })
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(14.dp).background(Color.White, AppShape.Circle).border(2.dp, color, AppShape.Circle))
                    }
                }
            }
        }
    }
}


private fun roomTypeColor(type: RoomType): Color = when (type) {
    RoomType.SALON    -> Color(0xFF5AC8FA)
    RoomType.KITCHEN  -> Color(0xFFFF9500)
    RoomType.BEDROOM  -> Color(0xFF5E5CE6)
    RoomType.OFFICE   -> Color(0xFF30B0C7)
    RoomType.BATHROOM -> Color(0xFF34C759)
    RoomType.HALLWAY  -> Color(0xFFAEAEB2)
    RoomType.DINING   -> Color(0xFFFF6B6B)
    RoomType.OTHER    -> Color(0xFF8E8E93)
}

// ─── Option Photo ─────────────────────────────────────────────────────────────

@Composable
private fun PhotoPreviewStep(onLaunchCamera: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppSpacing.XXL),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(Modifier.height(AppSpacing.Section))
            Text("Photographiez votre plan", style = AppType.SectionTitle, color = AppColors.TextPrimary)
            Spacer(Modifier.height(AppSpacing.MD))
            Text(
                "Cadrez bien votre dessin. Assurez-vous que les noms des pièces sont lisibles.",
                style = AppType.BodyPrimary, color = AppColors.TextMuted
            )
        }

        Button(
            onClick = onLaunchCamera,
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape.Pill,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
            Spacer(Modifier.size(AppSpacing.SM))
            Text("Ouvrir l'appareil photo", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
        }
    }
}

// ─── Confirmation pièces (option Photo) ──────────────────────────────────────

@Composable
private fun RoomConfirmationStep(
    uiState: PlanCaptureUiState,
    viewModel: PlanCaptureViewModel,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.XXL)
    ) {
        Spacer(Modifier.height(AppSpacing.LG))
        Text("Vérifiez les pièces détectées", style = AppType.CardTitle, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppSpacing.XS))
        Text("Ajoutez ou supprimez des pièces si besoin.",
             style = AppType.ControlLabel, color = AppColors.TextMuted)
        Spacer(Modifier.height(AppSpacing.LG))

        uiState.planImagePath?.let { path ->
            val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Plan photographié",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(AppShape.Large)
                        .background(AppColors.Surface)
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.LG))

        if (uiState.isDetecting) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Accent)
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.SM)
            ) {
                uiState.editableRooms.forEach { room ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.Surface, AppShape.Medium)
                            .padding(horizontal = AppSpacing.LG, vertical = AppSpacing.MD),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)) {
                            Box(modifier = Modifier.size(8.dp)
                                .background(roomTypeColor(room.type), AppShape.Circle))
                            Text(room.label, style = AppType.BodyPrimary, color = AppColors.TextPrimary)
                        }
                        IconButton(onClick = { viewModel.removeRoom(room.id) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Supprimer",
                                 tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)
                ) {
                    OutlinedTextField(
                        value = uiState.newRoomLabel,
                        onValueChange = viewModel::setNewRoomLabel,
                        placeholder = { Text("Nom d'une pièce", style = AppType.BodyPrimary, color = AppColors.TextMeta) },
                        modifier = Modifier.weight(1f),
                        shape = AppShape.Medium,
                        singleLine = true
                    )
                    IconButton(
                        onClick  = { viewModel.addRoom(uiState.newRoomLabel) },
                        enabled  = uiState.newRoomLabel.isNotBlank()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Ajouter", tint = AppColors.Accent)
                    }
                }
            }
        }

        Spacer(Modifier.height(AppSpacing.MD))
        Button(
            onClick   = onConfirm,
            modifier  = Modifier.fillMaxWidth().padding(bottom = AppSpacing.LG),
            shape     = AppShape.Pill,
            colors    = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
            enabled   = !uiState.isDetecting
        ) {
            Text("Ça semble bon, continuer", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
        }
    }
}

// ─── Utilitaires ──────────────────────────────────────────────────────────────

private fun createPlanFile(context: Context): File {
    val dir = File(context.filesDir, "plans").also { it.mkdirs() }
    return File(dir, "plan_${System.currentTimeMillis()}.jpg")
}
