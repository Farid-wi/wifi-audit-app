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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class PlanStep { OPTION_PICKER, CANVAS_BUILDER, PHOTO_PREVIEW, ROOM_CONFIRMATION }

data class PlanCaptureUiState(
    val step: PlanStep                  = PlanStep.OPTION_PICKER,
    val planImagePath: String?          = null,
    val detectedRooms: List<CanvasRoom> = emptyList(),
    val editableRooms: List<CanvasRoom> = emptyList(),
    val isDetecting: Boolean            = false,
    val newRoomLabel: String            = "",
    val savedPlans: List<SavedPlan>     = emptyList()
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
        _uiState.update { it.copy(step = PlanStep.CANVAS_BUILDER) }
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
