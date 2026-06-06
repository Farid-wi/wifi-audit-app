package com.wifiaudit.app.presentation.screen.results

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.OverallScore
import com.wifiaudit.app.domain.model.Recommendation
import com.wifiaudit.app.domain.model.SignalQuality
import com.wifiaudit.app.domain.model.rssiToQuality
import com.wifiaudit.app.domain.repository.AuditRepository
import com.wifiaudit.app.domain.usecase.GenerateHeatmapUseCase
import com.wifiaudit.app.domain.usecase.GenerateRecommendationsUseCase
import com.wifiaudit.app.domain.usecase.HeatmapGrid
import com.wifiaudit.app.domain.usecase.SubmitAuditUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "WIFI_AUDIT"

data class RoomResult(
    val name: String,
    val type: com.wifiaudit.app.domain.model.RoomType,
    val quality: SignalQuality,
    val hasData: Boolean
)

enum class SubmitState { Idle, Loading, Success, Error }

data class MeasurementDotInfo(val x: Float, val y: Float, val quality: SignalQuality)

data class ResultsUiState(
    val isLoading: Boolean                                  = true,
    val planImagePath: String?                              = null,
    val rooms: List<CanvasRoom>                             = emptyList(),
    val roomGrids: Map<String, HeatmapGrid>                 = emptyMap(),
    val overallScore: OverallScore                          = OverallScore.FAIR,
    val roomResults: List<RoomResult>                       = emptyList(),
    val recommendations: List<Recommendation>               = emptyList(),
    val measurementDots: List<MeasurementDotInfo>           = emptyList(),
    val gatewayPosition: com.wifiaudit.app.domain.model.Position?          = null,
    val repeaterPositions: List<com.wifiaudit.app.domain.model.RepeaterPosition> = emptyList(),
    val submitState: SubmitState                            = SubmitState.Idle
)

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val auditRepository: AuditRepository,
    private val generateHeatmapUseCase: GenerateHeatmapUseCase,
    private val generateRecommendationsUseCase: GenerateRecommendationsUseCase,
    private val submitAuditUseCase: SubmitAuditUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        loadLatestAudit()
    }

    private fun loadLatestAudit() {
        viewModelScope.launch {
            auditRepository.observeAudits().collect { audits ->
                val audit = audits.firstOrNull() ?: run {
                    Log.w(TAG, "observeAudits: liste vide")
                    return@collect
                }

                Log.d(TAG, buildString {
                    appendLine("=== AUDIT CHARGÉ ===")
                    appendLine("  id      : ${audit.id}")
                    appendLine("  ssid    : ${audit.ssid}")
                    appendLine("  pièces  : ${audit.rooms.size}")
                    appendLine("  mesures : ${audit.measurements.size}")
                    if (audit.measurements.isNotEmpty()) {
                        val rssiList = audit.measurements.map { it.rssi }
                        appendLine("  RSSI min/max/moy : ${rssiList.min()}/${rssiList.max()}/${"%.1f".format(rssiList.average())} dBm")
                        audit.measurements.forEachIndexed { i, m ->
                            appendLine("    [${i+1}] x=%.3f y=%.3f rssi=${m.rssi}dBm roomId=${m.roomId ?: "—"}".format(m.x, m.y))
                        }
                    }
                })

                launch(Dispatchers.Default) {
                    // Grilles IDW par pièce (masquées)
                    val roomGrids = generateHeatmapUseCase(audit.rooms, audit.measurements, gridSize = 50)

                    val recommendations = generateRecommendationsUseCase(audit.measurements, audit.rooms)

                    val roomResults = audit.rooms.map { room ->
                        val grid = roomGrids[room.id]
                        val hasData = grid?.hasValues() == true
                        val rssi = if (hasData) grid!!.median().toInt() else -90
                        RoomResult(
                            name    = room.label,
                            type    = room.type,
                            quality = rssiToQuality(rssi),
                            hasData = hasData
                        )
                    }

                    val overallScore = generateHeatmapUseCase.globalScore(audit.rooms, roomGrids)

                    Log.d(TAG, buildString {
                        appendLine("=== HEATMAP PAR PIÈCE ===")
                        roomResults.forEach { r ->
                            appendLine("  ${r.name}: ${if (r.hasData) r.quality else "—"}")
                        }
                        appendLine("  Score global: $overallScore")
                    })

                    _uiState.update {
                        it.copy(
                            isLoading        = false,
                            planImagePath    = audit.planImagePath,
                            rooms            = audit.rooms,
                            roomGrids        = roomGrids,
                            overallScore     = overallScore,
                            roomResults      = roomResults,
                            recommendations  = recommendations,
                            measurementDots  = audit.measurements.map { m ->
                                MeasurementDotInfo(m.x, m.y, rssiToQuality(m.rssi))
                            },
                            gatewayPosition    = audit.gatewayPosition,
                            repeaterPositions  = audit.repeaterPositions
                        )
                    }
                }
            }
        }
    }

    fun submitAudit() {
        if (_uiState.value.submitState == SubmitState.Loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(submitState = SubmitState.Loading) }
            val audit = auditRepository.observeAudits().first().firstOrNull()
            if (audit == null) {
                Log.e(TAG, "submitAudit: aucun audit en base")
                _uiState.update { it.copy(submitState = SubmitState.Error) }
                return@launch
            }
            Log.d(TAG, "Envoi audit ${audit.id} — ${audit.measurements.size} mesures → ${audit.ssid}")
            submitAuditUseCase(audit).fold(
                onSuccess = {
                    Log.d(TAG, "Envoi réussi")
                    _uiState.update { s -> s.copy(submitState = SubmitState.Success) }
                },
                onFailure = { e ->
                    Log.e(TAG, "Envoi échoué: ${e.message}", e)
                    _uiState.update { s -> s.copy(submitState = SubmitState.Error) }
                }
            )
        }
    }
}
