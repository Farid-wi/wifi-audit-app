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
        // Un (nouveau) plan invalide les anciennes positions d'équipements (relatives au plan) :
        // on les efface pour éviter qu'elles réapparaissent au calibrage d'un audit suivant.
        _state.update {
            it.copy(
                planImagePath     = path,
                rooms             = rooms,
                gatewayPosition   = null,
                repeaterPositions = emptyList()
            )
        }
    }

    fun updateRooms(rooms: List<CanvasRoom>) {
        _state.update { it.copy(rooms = rooms) }
    }

    fun setGatewayPosition(x: Float, y: Float) {
        _state.update { it.copy(gatewayPosition = Position(x, y)) }
    }

    /** Remplace l'ensemble des répéteurs (jamais d'empilement entre passages sur l'écran). */
    fun setRepeaters(positions: List<Pair<Float, Float>>) {
        _state.update {
            it.copy(
                repeaterPositions = positions.map { (x, y) ->
                    RepeaterPosition(UUID.randomUUID().toString(), Position(x, y))
                }
            )
        }
    }

    fun setSsid(ssid: String) {
        _state.update { it.copy(ssid = ssid) }
    }

    /** Repart de zéro pour un nouvel audit (le VM partagé survit au popBackStack). */
    fun reset() {
        _state.value = AuditCreationState()
    }
}

data class AuditCreationState(
    val planImagePath: String?              = null,
    val rooms: List<CanvasRoom>                   = emptyList(),
    val gatewayPosition: Position?          = null,
    val repeaterPositions: List<RepeaterPosition> = emptyList(),
    val ssid: String?                       = null
)
