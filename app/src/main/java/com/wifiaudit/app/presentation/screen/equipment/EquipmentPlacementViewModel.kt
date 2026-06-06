package com.wifiaudit.app.presentation.screen.equipment

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class EquipmentPlacementUiState(
    val gatewayPosition: Pair<Float, Float>? = null,
    val repeaterConfirmed: Boolean = false,
    val repeaterPositions: List<Pair<Float, Float>> = emptyList()
)

@HiltViewModel
class EquipmentPlacementViewModel @Inject constructor() : ViewModel() {

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
}
