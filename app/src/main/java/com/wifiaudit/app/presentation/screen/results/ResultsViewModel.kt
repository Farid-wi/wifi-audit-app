package com.wifiaudit.app.presentation.screen.results

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiaudit.app.domain.model.Audit
import com.wifiaudit.app.domain.model.AuditStatus
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
private val BAND_ORDER = listOf("2.4GHz", "5GHz", "6GHz")

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
    val submitState: SubmitState                            = SubmitState.Idle,
    /** Bandes disponibles pour la vue courante (toutes si aucun appareil sélectionné). */
    val availableBands: List<String>                        = emptyList(),
    /** Bande sélectionnée, ou null = bande connectée. */
    val selectedBand: String?                               = null,
    /** deviceId sélectionné ("gateway" ou ID répéteur), ou null = vue combinée. */
    val selectedDeviceId: String?                           = null,
    /** Association deviceId → (band → bssid) calculée depuis les mesures. */
    val deviceBssidMap: Map<String, Map<String, String>>    = emptyMap()
)

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val auditRepository: AuditRepository,
    private val generateHeatmapUseCase: GenerateHeatmapUseCase,
    private val generateRecommendationsUseCase: GenerateRecommendationsUseCase,
    private val submitAuditUseCase: SubmitAuditUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private var currentAudit: Audit? = null

    private val targetAuditId: String? = savedStateHandle["auditId"]

    init {
        if (targetAuditId != null) loadAuditById(targetAuditId) else loadLatestAudit()
    }

    /** Sélectionne une bande de fréquences (null = toutes/bande connectée). */
    fun selectBand(band: String?) {
        val audit = currentAudit ?: return
        val deviceId = _uiState.value.selectedDeviceId
        _uiState.update { it.copy(selectedBand = band) }
        viewModelScope.launch(Dispatchers.Default) {
            computeAndUpdateResults(audit, band, deviceId)
        }
    }

    /**
     * Sélectionne ou désélectionne un appareil (box/répéteur).
     * Un clic sur l'appareil déjà sélectionné revient à la vue combinée.
     */
    fun selectDevice(id: String) {
        val audit = currentAudit ?: return
        val isToggleOff = id == _uiState.value.selectedDeviceId
        val newDeviceId: String? = if (isToggleOff) null else id

        val newAvailableBands = if (newDeviceId != null) {
            _uiState.value.deviceBssidMap[newDeviceId]
                ?.keys?.sortedBy { BAND_ORDER.indexOf(it) }
                ?: emptyList()
        } else {
            computeAvailableBands(audit)
        }

        val newBand = when {
            newDeviceId == null                                          -> _uiState.value.selectedBand
            newAvailableBands.contains(_uiState.value.selectedBand)     -> _uiState.value.selectedBand
            newAvailableBands.isNotEmpty()                               -> newAvailableBands.first()
            else                                                         -> null
        }

        _uiState.update {
            it.copy(
                selectedDeviceId = newDeviceId,
                availableBands   = newAvailableBands,
                selectedBand     = newBand
            )
        }
        viewModelScope.launch(Dispatchers.Default) {
            computeAndUpdateResults(audit, newBand, newDeviceId)
        }
    }

    private fun loadLatestAudit() {
        viewModelScope.launch {
            auditRepository.observeAudits().collect { audits ->
                val audit = audits.firstOrNull() ?: run {
                    Log.w(TAG, "observeAudits: liste vide")
                    return@collect
                }
                currentAudit = audit

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
                            appendLine("    [${i+1}] x=%.3f y=%.3f rssi=${m.rssi}dBm bandes=${m.rssiPerBand} aps=${m.apReadings.size} roomId=${m.roomId ?: "—"}".format(m.x, m.y))
                        }
                    }
                })

                val availableBands = computeAvailableBands(audit)
                _uiState.update { it.copy(availableBands = availableBands) }

                launch(Dispatchers.Default) {
                    val deviceBssidMap = generateHeatmapUseCase.associateDevices(
                        audit.measurements,
                        audit.gatewayPosition,
                        audit.repeaterPositions
                    )

                    Log.d(TAG, buildString {
                        appendLine("=== ASSOCIATION DEVICES/BSSID ===")
                        if (deviceBssidMap.isEmpty()) {
                            appendLine("  (aucune association — les mesures ne contiennent pas de apReadings)")
                            appendLine("  Vérifier que l'accès Wi-Fi + NEARBY_WIFI_DEVICES sont accordés")
                        } else {
                            deviceBssidMap.forEach { (deviceId, bandMap) ->
                                val label = if (deviceId == "gateway") "Box/Routeur" else "Répéteur"
                                appendLine("  $label [$deviceId]:")
                                bandMap.entries.sortedBy { it.key }.forEach { (band, bssid) ->
                                    // Retrouver le pic RSSI enregistré pour ce BSSID
                                    val peakRssi = audit.measurements
                                        .flatMap { it.apReadings }
                                        .filter { it.bssid == bssid && it.band == band }
                                        .maxOfOrNull { it.rssi }
                                    appendLine("    $band → $bssid  (pic=${peakRssi ?: "?"}dBm)")
                                }
                            }
                        }
                        // Log de tous les BSSIDs distincts vus dans l'audit (aide au debug)
                        val allBssids = audit.measurements.flatMap { it.apReadings }
                            .groupBy { it.bssid to it.band }
                            .mapValues { (_, list) -> list.maxOf { it.rssi } }
                        if (allBssids.isNotEmpty()) {
                            appendLine("  --- BSSIDs distincts dans l'audit ---")
                            allBssids.entries
                                .sortedWith(compareBy({ it.key.second }, { -it.value }))
                                .forEach { (key, peakRssi) ->
                                    val (bssid, band) = key
                                    appendLine("    $bssid  $band  pic=${peakRssi}dBm")
                                }
                        }
                    })

                    _uiState.update { it.copy(deviceBssidMap = deviceBssidMap) }
                    computeAndUpdateResults(audit, _uiState.value.selectedBand, null)
                }
            }
        }
    }

    private fun loadAuditById(id: String) {
        viewModelScope.launch {
            val audit = auditRepository.getAuditById(id) ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            currentAudit = audit
            val availableBands = computeAvailableBands(audit)
            _uiState.update {
                it.copy(
                    availableBands = availableBands,
                    submitState = if (audit.status == AuditStatus.SYNCED) SubmitState.Success else SubmitState.Idle
                )
            }
            launch(Dispatchers.Default) {
                val deviceBssidMap = generateHeatmapUseCase.associateDevices(
                    audit.measurements, audit.gatewayPosition, audit.repeaterPositions
                )
                _uiState.update { it.copy(deviceBssidMap = deviceBssidMap) }
                computeAndUpdateResults(audit, null, null)
            }
        }
    }

    private fun computeAndUpdateResults(audit: Audit, band: String?, deviceId: String? = null) {
        val bssid = if (deviceId != null) _uiState.value.deviceBssidMap[deviceId]?.get(band) else null

        val roomGrids = when {
            bssid != null -> generateHeatmapUseCase.computeForBssid(audit.rooms, audit.measurements, bssid, gridSize = 50)
            else          -> generateHeatmapUseCase(audit.rooms, audit.measurements, gridSize = 50, band = band)
        }

        val recommendations = generateRecommendationsUseCase(audit.measurements, audit.rooms)

        val roomResults = audit.rooms.map { room ->
            val grid    = roomGrids[room.id]
            val hasData = grid?.hasValues() == true
            val rssi    = if (hasData) grid!!.median().toInt() else -90
            RoomResult(name = room.label, type = room.type, quality = rssiToQuality(rssi), hasData = hasData)
        }

        val overallScore = generateHeatmapUseCase.globalScore(audit.rooms, roomGrids)

        val measurementDots = audit.measurements.map { m ->
            val rssi = if (bssid != null) m.rssiForBssid(bssid) else m.rssiForDisplay(band)
            MeasurementDotInfo(m.x, m.y, rssiToQuality(rssi))
        }

        val viewLabel = if (bssid != null) "device=$deviceId bssid=$bssid band=$band"
                        else "bande=${band ?: "toutes"}"
        Log.d(TAG, buildString {
            appendLine("=== HEATMAP ($viewLabel) ===")
            roomResults.forEach { r -> appendLine("  ${r.name}: ${if (r.hasData) r.quality else "—"}") }
            appendLine("  Score global: $overallScore")
        })

        _uiState.update {
            it.copy(
                isLoading         = false,
                planImagePath     = audit.planImagePath,
                rooms             = audit.rooms,
                roomGrids         = roomGrids,
                overallScore      = overallScore,
                roomResults       = roomResults,
                recommendations   = recommendations,
                measurementDots   = measurementDots,
                gatewayPosition   = audit.gatewayPosition,
                repeaterPositions = audit.repeaterPositions
            )
        }
    }

    /** Retourne les bandes présentes dans au moins une mesure (ordre fixe). */
    private fun computeAvailableBands(audit: Audit): List<String> =
        BAND_ORDER.filter { band ->
            audit.measurements.any { m -> m.rssiPerBand.containsKey(band) }
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
