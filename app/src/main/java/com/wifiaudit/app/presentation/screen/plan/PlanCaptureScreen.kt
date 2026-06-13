package com.wifiaudit.app.presentation.screen.plan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.wifiaudit.app.presentation.screen.common.planBackdrop
import com.wifiaudit.app.presentation.screen.common.rememberPressedScale
import com.wifiaudit.app.presentation.screen.common.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    if (uiState.showSaveDialog) {
        SavePlanDialog(
            title       = "Enregistrer ce plan",
            confirmText = "Enregistrer",
            initialName = defaultPlanName(),   // nom intelligent par défaut, éditable
            onDismiss   = viewModel::dismissSaveDialog,
            onConfirm   = viewModel::savePlan
        )
    }

    uiState.renameTarget?.let { target ->
        SavePlanDialog(
            title       = "Renommer le plan",
            confirmText = "Renommer",
            initialName = target.name,
            onDismiss   = viewModel::cancelRename,
            onConfirm   = { newName -> viewModel.renamePlan(target.id, newName) }
        )
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
                    onRenamePlan     = viewModel::startRename,
                    onDuplicatePlan  = viewModel::duplicatePlan,
                    onDeletePlan     = viewModel::deleteSavedPlan,
                    onSeeAllPlans    = viewModel::showAllPlans
                )

            PlanStep.ALL_PLANS ->
                AllPlansStep(
                    savedPlans      = uiState.savedPlans,
                    onLoadPlan      = viewModel::loadSavedPlan,
                    onRenamePlan    = viewModel::startRename,
                    onDuplicatePlan = viewModel::duplicatePlan,
                    onDeletePlan    = viewModel::deleteSavedPlan
                )

            PlanStep.CANVAS_BUILDER ->
                CanvasBuilderStep(
                    rooms     = uiState.editableRooms,
                    planSaved = uiState.planSaved,
                    onUpdate  = viewModel::onCanvasRoomsConfirmed,
                    onSave    = viewModel::showSaveDialog,
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

// ─── Accueil ──────────────────────────────────────────────────────────────────

private fun defaultPlanName(): String =
    "Plan du " + SimpleDateFormat("d MMMM", Locale.FRENCH).format(Date())

@Composable
private fun OptionPickerStep(
    savedPlans: List<SavedPlan>,
    onCanvasSelected: () -> Unit,
    onLoadPlan:       (SavedPlan) -> Unit,
    onRenamePlan:     (SavedPlan) -> Unit,
    onDuplicatePlan:  (String) -> Unit,
    onDeletePlan:     (String) -> Unit,
    onSeeAllPlans:    () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.XXL)
            .padding(bottom = AppSpacing.Section),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.XL)
    ) {
        Spacer(Modifier.height(AppSpacing.LG))

        HomeHeader()

        // ① Hero — action principale
        HeroNewDiagnosticCard(onStart = onCanvasSelected)

        // ② Mes plans (masqué tant qu'il n'y en a aucun — le Hero porte déjà la création)
        if (savedPlans.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
                SectionHeader(
                    title       = "Mes plans",
                    actionLabel = if (savedPlans.size > 3) "Tout voir" else null,
                    onAction    = onSeeAllPlans
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(savedPlans.take(8), key = { it.id }) { plan ->
                        SavedPlanCard(
                            plan        = plan,
                            onLoad      = { onLoadPlan(plan) },
                            onRename    = { onRenamePlan(plan) },
                            onDuplicate = { onDuplicatePlan(plan.id) },
                            onDelete    = { onDeletePlan(plan.id) },
                            modifier    = Modifier.width(160.dp)
                        )
                    }
                    item { CreatePlanCard(onClick = onCanvasSelected) }
                }
            }
        }

        // ③ Mes diagnostics — teaser (écran à venir)
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.MD)) {
            SectionHeader(title = "Mes diagnostics")
            DiagnosticsTeaserCard()
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(AppColors.Accent.copy(alpha = 0.10f), AppShape.Medium),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Wifi, null, tint = AppColors.Accent, modifier = Modifier.size(24.dp))
        }
        Column {
            Text("WiFi Audit", style = AppType.CardTitle, color = AppColors.TextPrimary)
            Text(
                "Cartographiez la couverture Wi-Fi de votre logement",
                style = AppType.ControlLabel, color = AppColors.TextMuted
            )
        }
    }
}

@Composable
private fun HeroNewDiagnosticCard(onStart: () -> Unit) {
    val (heroScale, heroSource) = rememberPressedScale()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Accent.copy(alpha = 0.08f), AppShape.Large)
            .border(1.dp, AppColors.Accent.copy(alpha = 0.18f), AppShape.Large)
            .padding(AppSpacing.XL),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.LG)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(AppColors.Accent, AppShape.Medium),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.GridView, null, tint = AppColors.OnAccent, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Nouveau diagnostic", style = AppType.BodyEmphasis, color = AppColors.TextPrimary)
                Text(
                    "Dessinez le plan, placez vos équipements, mesurez le signal.",
                    style = AppType.ControlLabel, color = AppColors.TextMuted
                )
            }
        }
        Button(
            onClick = onStart,
            interactionSource = heroSource,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = heroScale; scaleY = heroScale },
            shape = AppShape.Pill,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Text("Commencer", style = AppType.BodyEmphasis, color = AppColors.OnAccent)
            Spacer(Modifier.width(AppSpacing.XS))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward, null,
                tint = AppColors.OnAccent, modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = AppType.BodyEmphasis, color = AppColors.TextPrimary)
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = AppType.ControlLabel,
                color = AppColors.Accent,
                modifier = Modifier.clip(AppShape.Small).clickable(onClick = onAction).padding(AppSpacing.XS)
            )
        }
    }
}

/** Miniature réelle d'un plan : les pièces dessinées en rectangles colorés. */
@Composable
private fun PlanThumbnail(rooms: List<CanvasRoom>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        rooms.forEach { r ->
            val l = r.bounds.left * size.width
            val t = r.bounds.top * size.height
            val w = (r.bounds.right - r.bounds.left) * size.width
            val h = (r.bounds.bottom - r.bounds.top) * size.height
            val c = roomTypeColor(r.type)
            val topLeft = androidx.compose.ui.geometry.Offset(l, t)
            val rectSize = androidx.compose.ui.geometry.Size(w, h)
            drawRect(c.copy(alpha = 0.18f), topLeft = topLeft, size = rectSize)
            drawRect(
                c.copy(alpha = 0.55f), topLeft = topLeft, size = rectSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )
        }
    }
}

@Composable
private fun SavedPlanCard(
    plan: SavedPlan,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(plan.createdAt) {
        SimpleDateFormat("d MMM", Locale.FRENCH).format(Date(plan.createdAt))
    }
    Column(
        modifier = modifier
            .clip(AppShape.Large)
            .background(AppColors.Surface, AppShape.Large)
            .border(1.dp, AppColors.BorderSoft, AppShape.Large)
            .clickable(onClick = onLoad)
            .padding(AppSpacing.SM),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.XS)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(AppShape.Medium)
                .background(Color.White)
                .border(1.dp, AppColors.BorderSoft, AppShape.Medium)
        ) {
            PlanThumbnail(plan.rooms, Modifier.fillMaxSize().padding(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    plan.name, style = AppType.BodyEmphasis, color = AppColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${plan.rooms.size} pièce${if (plan.rooms.size > 1) "s" else ""} · $dateStr",
                    style = AppType.Micro, color = AppColors.TextMuted
                )
            }
            PlanContextMenu(onRename = onRename, onDuplicate = onDuplicate, onDelete = onDelete)
        }
    }
}

@Composable
private fun PlanContextMenu(
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.MoreVert, "Options du plan", tint = AppColors.TextMuted,
                 modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Renommer", style = AppType.BodyPrimary) },
                onClick = { expanded = false; onRename() },
                leadingIcon = { Icon(Icons.Outlined.Edit, null) }
            )
            DropdownMenuItem(
                text = { Text("Dupliquer", style = AppType.BodyPrimary) },
                onClick = { expanded = false; onDuplicate() },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }
            )
            DropdownMenuItem(
                text = { Text("Supprimer", style = AppType.BodyPrimary, color = AppColors.SignalPoor) },
                onClick = { expanded = false; onDelete() },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = AppColors.SignalPoor) }
            )
        }
    }
}

@Composable
private fun CreatePlanCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .height(150.dp)
            .clip(AppShape.Large)
            .background(AppColors.Accent.copy(alpha = 0.06f), AppShape.Large)
            .border(1.5.dp, AppColors.Accent.copy(alpha = 0.35f), AppShape.Large)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Add, null, tint = AppColors.Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(AppSpacing.XS))
        Text("Créer", style = AppType.BodyEmphasis, color = AppColors.Accent)
    }
}

@Composable
private fun DiagnosticsTeaserCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.SurfaceAlt, AppShape.Large)
            .padding(AppSpacing.XL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White, AppShape.Medium),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Schedule, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Bientôt disponible", style = AppType.BodyEmphasis, color = AppColors.TextSecondary)
            Text(
                "Retrouvez ici l'historique de vos audits et leurs heatmaps.",
                style = AppType.ControlLabel, color = AppColors.TextMuted
            )
        }
    }
}

// ─── Liste complète des plans (« Tout voir ») ─────────────────────────────────

@Composable
private fun AllPlansStep(
    savedPlans: List<SavedPlan>,
    onLoadPlan: (SavedPlan) -> Unit,
    onRenamePlan: (SavedPlan) -> Unit,
    onDuplicatePlan: (String) -> Unit,
    onDeletePlan: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.XXL)
            .padding(bottom = AppSpacing.Section)
    ) {
        Spacer(Modifier.height(AppSpacing.SM))
        Text("Mes plans", style = AppType.SectionTitle, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppSpacing.LG))

        if (savedPlans.isEmpty()) {
            Text("Aucun plan enregistré.", style = AppType.BodyPrimary, color = AppColors.TextMuted)
        } else {
            // Grille 2 colonnes via lignes chunkées (peu d'éléments → pas besoin de lazy grid).
            savedPlans.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.MD),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD)
                ) {
                    row.forEach { plan ->
                        SavedPlanCard(
                            plan        = plan,
                            onLoad      = { onLoadPlan(plan) },
                            onRename    = { onRenamePlan(plan) },
                            onDuplicate = { onDuplicatePlan(plan.id) },
                            onDelete    = { onDeletePlan(plan.id) },
                            modifier    = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
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
    planSaved: Boolean,
    onUpdate: (List<CanvasRoom>) -> Unit,
    onSave: () -> Unit,
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
                        .clip(AppShape.Large)
                        .planBackdrop(withBorder = true)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.XXL, vertical = AppSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.SM)
        ) {
            // Enregistrer le plan (pièces seules) — disponible dès qu'une pièce est dessinée.
            if (rooms.isNotEmpty()) {
                AnimatedVisibility(visible = planSaved, enter = fadeIn(), exit = fadeOut()) {
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
                    onClick  = onSave,
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

    // Scale-in stagger: each new room grows from 0.82 → 1.0, 40 ms apart.
    val reducedMotion = rememberReducedMotion()
    val scaleMap = remember { mutableStateMapOf<String, Animatable<Float, *>>() }
    LaunchedEffect(rooms.map { it.id }.toString()) {
        val currentIds = rooms.map { it.id }.toSet()
        // Remove stale entries (deleted rooms).
        scaleMap.keys.toSet().minus(currentIds).forEach { scaleMap.remove(it) }
        // Animate newly added rooms.
        rooms.forEachIndexed { index, room ->
            if (!scaleMap.containsKey(room.id)) {
                val anim = Animatable(if (reducedMotion) 1f else 0.82f)
                scaleMap[room.id] = anim
                if (!reducedMotion) {
                    launch {
                        delay(index * 40L)
                        anim.animateTo(1f, tween(120))
                    }
                }
            }
        }
    }

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
        // Le fond + la grille de points sont fournis par planBackdrop() sur le conteneur.
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
                val roomScale = scaleMap[room.id]?.value ?: 1f

                Box(
                    modifier = Modifier
                        .offset { IntOffset(lPx, tPx) }
                        .size(wDp, hDp)
                        .graphicsLayer { scaleX = roomScale; scaleY = roomScale }
                        .background(color.copy(alpha = if (isSelected) 0.20f else 0.12f), AppShape.Medium)
                        .border(if (isSelected) 2.dp else 1.dp,
                                if (isSelected) color else color.copy(alpha = 0.45f),
                                AppShape.Medium)
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

// ─── Dialog : saisie du nom du plan ──────────────────────────────────────────

@Composable
private fun SavePlanDialog(
    title: String,
    confirmText: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(title, style = AppType.CardTitle, color = AppColors.TextPrimary) },
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
            ) { Text(confirmText, style = AppType.BodyEmphasis, color = AppColors.OnAccent) }
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

// ─── Utilitaires ──────────────────────────────────────────────────────────────

private fun createPlanFile(context: Context): File {
    val dir = File(context.filesDir, "plans").also { it.mkdirs() }
    return File(dir, "plan_${System.currentTimeMillis()}.jpg")
}
