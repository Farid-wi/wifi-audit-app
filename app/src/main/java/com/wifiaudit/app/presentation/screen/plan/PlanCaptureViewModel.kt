package com.wifiaudit.app.presentation.screen.plan

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.RoomBounds
import com.wifiaudit.app.domain.model.RoomType
import com.wifiaudit.app.domain.model.SavedPlan
import com.wifiaudit.app.domain.repository.SavedPlanRepository
import com.wifiaudit.app.domain.usecase.DetectRoomsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class PlanStep { OPTION_PICKER, ALL_PLANS, CANVAS_BUILDER, PHOTO_PREVIEW, ROOM_CONFIRMATION }

data class PlanCaptureUiState(
    val step: PlanStep                  = PlanStep.OPTION_PICKER,
    val planImagePath: String?          = null,
    val detectedRooms: List<CanvasRoom> = emptyList(),
    val editableRooms: List<CanvasRoom> = emptyList(),
    val isDetecting: Boolean            = false,
    val newRoomLabel: String            = "",
    val savedPlans: List<SavedPlan>     = emptyList(),
    val showSaveDialog: Boolean         = false,
    val planSaved: Boolean              = false,
    /** Plan en cours de renommage (non-null = dialog de renommage affiché). */
    val renameTarget: SavedPlan?        = null
)

@HiltViewModel
class PlanCaptureViewModel @Inject constructor(
    private val detectRoomsUseCase: DetectRoomsUseCase,
    private val savedPlanRepository: SavedPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanCaptureUiState())
    val uiState: StateFlow<PlanCaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            savedPlanRepository.observeAll().collect { plans ->
                _uiState.update { it.copy(savedPlans = plans) }
            }
        }
    }

    // ── Navigation entre options ──────────────────────────────────────────────

    fun onCanvasOptionSelected() {
        // « Commencer » / « + Créer » = nouveau plan vierge : on repart toujours de zéro,
        // même si un plan enregistré a été chargé puis abandonné juste avant.
        _uiState.update {
            it.copy(
                step          = PlanStep.CANVAS_BUILDER,
                editableRooms = emptyList(),
                detectedRooms = emptyList(),
                planImagePath = null
            )
        }
    }

    /** Retour vers le choix initial (utilisé par le bouton Retour et le geste système). */
    fun backToPicker() {
        _uiState.update { it.copy(step = PlanStep.OPTION_PICKER) }
    }

    /** Ouvre la liste complète des plans enregistrés (« Tout voir »). */
    fun showAllPlans() {
        _uiState.update { it.copy(step = PlanStep.ALL_PLANS) }
    }

    // ── Actions sur un plan enregistré (menu ⋯) ───────────────────────────────

    fun startRename(plan: SavedPlan) {
        _uiState.update { it.copy(renameTarget = plan) }
    }

    fun cancelRename() {
        _uiState.update { it.copy(renameTarget = null) }
    }

    fun renamePlan(planId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            savedPlanRepository.rename(planId, newName)
            _uiState.update { it.copy(renameTarget = null) }
        }
    }

    fun duplicatePlan(planId: String) {
        viewModelScope.launch { savedPlanRepository.duplicate(planId) }
    }

    fun onPhotoOptionSelected() {
        _uiState.update { it.copy(step = PlanStep.PHOTO_PREVIEW) }
    }

    fun onCanvasRoomsConfirmed(rooms: List<CanvasRoom>) {
        _uiState.update { it.copy(editableRooms = rooms) }
    }

    // ── Chargement d'un plan sauvegardé ───────────────────────────────────────

    fun loadSavedPlan(plan: SavedPlan) {
        _uiState.update {
            it.copy(
                editableRooms = plan.rooms,
                planImagePath = plan.planImagePath.takeIf { p -> p.isNotEmpty() },
                step          = PlanStep.CANVAS_BUILDER
            )
        }
    }

    fun deleteSavedPlan(planId: String) {
        viewModelScope.launch { savedPlanRepository.delete(planId) }
    }

    // ── Enregistrement du plan (pièces seules, avant placement des équipements) ─

    fun showSaveDialog() {
        if (_uiState.value.editableRooms.isEmpty()) return
        _uiState.update { it.copy(showSaveDialog = true) }
    }

    fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }

    fun savePlan(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        if (state.editableRooms.isEmpty()) return
        viewModelScope.launch {
            savedPlanRepository.save(
                SavedPlan(
                    name          = trimmed,
                    planImagePath = state.planImagePath ?: "",
                    rooms         = state.editableRooms
                )
            )
            _uiState.update { it.copy(showSaveDialog = false, planSaved = true) }
            delay(2_000)
            _uiState.update { it.copy(planSaved = false) }
        }
    }

    // ── Option Photo — ML Kit ─────────────────────────────────────────────────

    fun onPhotoCaptured(imagePath: String) {
        _uiState.update { it.copy(planImagePath = imagePath, isDetecting = true) }
        viewModelScope.launch {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            val rooms = if (bitmap != null) {
                runCatching { detectRoomsUseCase(bitmap) }.getOrElse { emptyList() }
            } else emptyList()
            _uiState.update {
                it.copy(
                    isDetecting   = false,
                    detectedRooms = rooms,
                    editableRooms = rooms,
                    step          = if (rooms.isNotEmpty()) PlanStep.ROOM_CONFIRMATION
                                    else PlanStep.CANVAS_BUILDER
                )
            }
        }
    }

    fun onPhotoFailed() {
        _uiState.update { it.copy(step = PlanStep.PHOTO_PREVIEW) }
    }

    // ── Édition manuelle des pièces ───────────────────────────────────────────

    fun addRoom(label: String, type: RoomType = RoomType.OTHER) {
        if (label.isBlank()) return
        val room = CanvasRoom(
            id     = UUID.randomUUID().toString(),
            type   = type,
            label  = label.trim().replaceFirstChar { it.uppercase() },
            bounds = RoomBounds(left = 0.1f, top = 0.1f, right = 0.5f, bottom = 0.5f)
        )
        _uiState.update { it.copy(editableRooms = it.editableRooms + room, newRoomLabel = "") }
    }

    fun removeRoom(roomId: String) {
        _uiState.update { it.copy(editableRooms = it.editableRooms.filter { r -> r.id != roomId }) }
    }

    fun setNewRoomLabel(label: String) {
        _uiState.update { it.copy(newRoomLabel = label) }
    }
}
