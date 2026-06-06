package com.wifiaudit.app.presentation.screen.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.Position
import com.wifiaudit.app.domain.model.RepeaterPosition
import com.wifiaudit.app.domain.model.SavedPlan
import com.wifiaudit.app.domain.repository.SavedPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EquipmentPlacementUiState(
    val gatewayPosition: Pair<Float, Float>?    = null,
    val repeaterConfirmed: Boolean              = false,
    val repeaterPositions: List<Pair<Float, Float>> = emptyList(),
    val showSaveDialog: Boolean                 = false,
    val planSaved: Boolean                      = false
)

@HiltViewModel
class EquipmentPlacementViewModel @Inject constructor(
    private val savedPlanRepository: SavedPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EquipmentPlacementUiState())
    val uiState: StateFlow<EquipmentPlacementUiState> = _uiState.asStateFlow()

    fun placeGateway(x: Float, y: Float) {
        _uiState.update { it.copy(gatewayPosition = x to y) }
    }

    fun confirmHasRepeater() {
        _uiState.update { it.copy(repeaterConfirmed = true) }
    }

    fun addRepeater(x: Float, y: Float) {
        _uiState.update { it.copy(repeaterPositions = it.repeaterPositions + (x to y)) }
    }

    // Pré-charge les positions depuis un plan sauvegardé (appelé une seule fois à l'init de l'écran)
    fun initializeFromSavedPlan(gateway: Position?, repeaters: List<RepeaterPosition>) {
        if (_uiState.value.gatewayPosition != null) return
        if (gateway == null) return
        _uiState.update {
            it.copy(
                gatewayPosition   = gateway.x to gateway.y,
                repeaterPositions = repeaters.map { r -> r.position.x to r.position.y },
                repeaterConfirmed = repeaters.isNotEmpty()
            )
        }
    }

    // ── Sauvegarde du plan complet (pièces + GW + répéteurs) ─────────────────

    fun showSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = true) }
    }

    fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }

    fun savePlan(
        name: String,
        planImagePath: String,
        rooms: List<CanvasRoom>,
        gateway: Pair<Float, Float>,
        repeaters: List<Pair<Float, Float>>
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val plan = SavedPlan(
                name              = trimmed,
                planImagePath     = planImagePath,
                rooms             = rooms,
                gatewayPosition   = Position(gateway.first, gateway.second),
                repeaterPositions = repeaters.mapIndexed { i, (x, y) ->
                    RepeaterPosition(id = "rep_$i", position = Position(x, y))
                }
            )
            savedPlanRepository.save(plan)
            _uiState.update { it.copy(showSaveDialog = false, planSaved = true) }
            delay(2_000)
            _uiState.update { it.copy(planSaved = false) }
        }
    }
}
