package com.wifiaudit.app.presentation.screen.measure

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiaudit.app.domain.model.Audit
import com.wifiaudit.app.domain.model.AuditStatus
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.Measurement
import com.wifiaudit.app.domain.model.Position
import com.wifiaudit.app.domain.model.SignalQuality
import com.wifiaudit.app.domain.repository.AuditRepository
import com.wifiaudit.app.domain.usecase.RunPingUseCase
import com.wifiaudit.app.domain.usecase.ScanWifiUseCase
import com.wifiaudit.app.presentation.AuditCreationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "WIFI_AUDIT"

data class MeasurementPoint(
    val x: Float,
    val y: Float,
    val quality: SignalQuality
)

data class MeasureUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val measurements: List<MeasurementPoint> = emptyList(),
    val pendingPosition: Pair<Float, Float>? = null,
    val planImagePath: String? = null,
    val error: String? = null
) {
    val measurementCount: Int get() = measurements.size
    val canFinish: Boolean get() = measurementCount >= MIN_MEASUREMENTS
}

const val MIN_MEASUREMENTS = 5

@HiltViewModel
class MeasureViewModel @Inject constructor(
    private val scanWifiUseCase: ScanWifiUseCase,
    private val runPingUseCase: RunPingUseCase,
    private val auditRepository: AuditRepository
) : ViewModel() {

    private val auditId = UUID.randomUUID().toString()
    private var targetSsid: String? = null
    private var rooms: List<CanvasRoom> = emptyList()

    // Mesures complètes conservées en mémoire pour la sauvegarde finale
    private val fullMeasurements = mutableListOf<Measurement>()

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    fun setPlanImagePath(path: String) {
        _uiState.update { it.copy(planImagePath = path) }
    }

    fun setTargetSsid(ssid: String?) {
        targetSsid = ssid
    }

    fun setRooms(canvasRooms: List<CanvasRoom>) {
        rooms = canvasRooms
    }

    fun selectPosition(x: Float, y: Float) {
        _uiState.update { it.copy(pendingPosition = x to y, error = null) }
    }

    fun takeMeasurement() {
        val pos = _uiState.value.pendingPosition ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scanWifiUseCase(pos.first, pos.second, auditId, targetSsid).fold(
                onSuccess = { measurement ->
                    // Rattacher la mesure à la pièce qui la contient
                    val roomId = rooms.firstOrNull { it.bounds.contains(measurement.x, measurement.y) }?.id
                    val measurement = measurement.copy(roomId = roomId)
                    fullMeasurements += measurement
                    Log.d(TAG, "Mesure #${fullMeasurements.size} ajoutée — RSSI=${measurement.rssi} dBm quality=${measurement.quality}")
                    _uiState.update { s ->
                        s.copy(
                            isLoading       = false,
                            pendingPosition = null,
                            measurements    = s.measurements + MeasurementPoint(
                                x       = measurement.x,
                                y       = measurement.y,
                                quality = measurement.quality
                            )
                        )
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Scan échoué: ${e.message}", e)
                    _uiState.update { s ->
                        s.copy(isLoading = false, error = e.message ?: "Erreur de mesure")
                    }
                }
            )
        }
    }

    /**
     * Persiste l'audit complet dans Room puis appelle [onSaved].
     * Appelé depuis l'écran juste avant la navigation vers les résultats.
     */
    fun saveAndNavigate(creationState: AuditCreationState, onSaved: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val audit = Audit(
                id                = auditId,
                ssid              = creationState.ssid ?: "Inconnu",
                planImagePath     = creationState.planImagePath ?: "",
                gatewayPosition   = creationState.gatewayPosition ?: Position(0.5f, 0.5f),
                repeaterPositions = creationState.repeaterPositions,
                rooms             = creationState.rooms,
                measurements      = fullMeasurements.toList(),
                status            = AuditStatus.DRAFT
            )

            Log.d(TAG, buildString {
                appendLine("=== SAUVEGARDE AUDIT ===")
                appendLine("  auditId    : $auditId")
                appendLine("  ssid       : ${audit.ssid}")
                appendLine("  planImage  : ${audit.planImagePath}")
                appendLine("  gateway    : ${audit.gatewayPosition}")
                appendLine("  répéteurs  : ${audit.repeaterPositions.size}")
                appendLine("  pièces     : ${audit.rooms.size}")
                appendLine("  mesures    : ${audit.measurements.size}")
                audit.measurements.forEachIndexed { i, m ->
                    appendLine("    [${i+1}] x=%.3f y=%.3f rssi=${m.rssi}dBm bssid=${m.bssid}".format(m.x, m.y))
                }
            })

            auditRepository.saveAudit(audit)
            _uiState.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
