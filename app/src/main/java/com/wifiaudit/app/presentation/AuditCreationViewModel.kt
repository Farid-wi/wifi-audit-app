package com.wifiaudit.app.presentation

import androidx.lifecycle.ViewModel
import com.wifiaudit.app.domain.model.Position
import com.wifiaudit.app.domain.model.RepeaterPosition
import com.wifiaudit.app.domain.model.CanvasRoom
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel partagé entre tous les écrans du stepper.
 * Créé au niveau du NavHost — instance unique pour tout l'audit en cours.
 */
@HiltViewModel
class AuditCreationViewModel @Inject constructor() : ViewModel() {

    val auditId: String = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(AuditCreationState())
    val state: StateFlow<AuditCreationState> = _state.asStateFlow()

    fun setPlanImagePath(path: String, rooms: List<CanvasRoom> = emptyList()) {
        _state.update { it.copy(planImagePath = path, rooms = rooms) }
    }

    fun updateRooms(rooms: List<CanvasRoom>) {
        _state.update { it.copy(rooms = rooms) }
    }

    fun setGatewayPosition(x: Float, y: Float) {
        _state.update { it.copy(gatewayPosition = Position(x, y)) }
    }

    fun addRepeater(x: Float, y: Float) {
        val rep = RepeaterPosition(UUID.randomUUID().toString(), Position(x, y))
        _state.update { it.copy(repeaterPositions = it.repeaterPositions + rep) }
    }

    fun removeLastRepeater() {
        _state.update { it.copy(repeaterPositions = it.repeaterPositions.dropLast(1)) }
    }

    fun setSsid(ssid: String) {
        _state.update { it.copy(ssid = ssid) }
    }

    fun preloadEquipment(gateway: Position?, repeaters: List<RepeaterPosition>) {
        _state.update { it.copy(gatewayPosition = gateway, repeaterPositions = repeaters) }
    }
}

data class AuditCreationState(
    val planImagePath: String?              = null,
    val rooms: List<CanvasRoom>                   = emptyList(),
    val gatewayPosition: Position?          = null,
    val repeaterPositions: List<RepeaterPosition> = emptyList(),
    val ssid: String?                       = null
)
