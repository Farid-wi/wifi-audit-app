package com.wifiaudit.app.presentation.screen.equipment

import androidx.lifecycle.ViewModel
import com.wifiaudit.app.domain.model.Position
import com.wifiaudit.app.domain.model.RepeaterPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class EquipmentPlacementUiState(
    val gatewayPosition: Pair<Float, Float>?        = null,
    val gatewayOnDifferentFloor: Boolean            = false,
    val repeaterPositions: List<Pair<Float, Float>> = emptyList(),
    val offFloorRepeaterCount: Int                  = 0
)

@HiltViewModel
class EquipmentPlacementViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(EquipmentPlacementUiState())
    val uiState: StateFlow<EquipmentPlacementUiState> = _uiState.asStateFlow()

    fun placeGateway(x: Float, y: Float) {
        _uiState.update { it.copy(gatewayPosition = x to y, gatewayOnDifferentFloor = false) }
    }

    fun placeGatewayOffFloor() {
        _uiState.update { it.copy(gatewayPosition = -1f to -1f, gatewayOnDifferentFloor = true) }
    }

    /** Déplacement relatif de la box (drag sur le plan), borné au plan. */
    fun moveGatewayBy(dx: Float, dy: Float) {
        _uiState.update { s ->
            val gw = s.gatewayPosition ?: return@update s
            s.copy(
                gatewayPosition = (gw.first + dx).coerceIn(0f, 1f) to (gw.second + dy).coerceIn(0f, 1f)
            )
        }
    }

    fun addRepeater(x: Float, y: Float) {
        _uiState.update { it.copy(repeaterPositions = it.repeaterPositions + (x to y)) }
    }

    fun addRepeaterOffFloor() {
        _uiState.update { it.copy(offFloorRepeaterCount = it.offFloorRepeaterCount + 1) }
    }

    fun removeOffFloorRepeater() {
        _uiState.update { it.copy(offFloorRepeaterCount = (it.offFloorRepeaterCount - 1).coerceAtLeast(0)) }
    }

    /** Déplacement relatif d'un répéteur (drag sur le plan), borné au plan. */
    fun moveRepeaterBy(index: Int, dx: Float, dy: Float) {
        _uiState.update { s ->
            if (index !in s.repeaterPositions.indices) return@update s
            val updated = s.repeaterPositions.toMutableList()
            val (x, y) = updated[index]
            updated[index] = (x + dx).coerceIn(0f, 1f) to (y + dy).coerceIn(0f, 1f)
            s.copy(repeaterPositions = updated)
        }
    }

    /** Suppression individuelle d'un répéteur par son index (et pas seulement le dernier). */
    fun removeRepeater(index: Int) {
        _uiState.update { s ->
            if (index !in s.repeaterPositions.indices) return@update s
            s.copy(repeaterPositions = s.repeaterPositions.filterIndexed { i, _ -> i != index })
        }
    }

    // Pré-charge les positions depuis un plan sauvegardé (appelé une seule fois à l'init de l'écran)
    fun initializeFromSavedPlan(gateway: Position?, repeaters: List<RepeaterPosition>) {
        if (_uiState.value.gatewayPosition != null) return
        if (gateway == null) return
        _uiState.update {
            it.copy(
                gatewayPosition   = gateway.x to gateway.y,
                repeaterPositions = repeaters.map { r -> r.position.x to r.position.y }
            )
        }
    }
}
