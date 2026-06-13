package com.wifiaudit.app.presentation.screen.measure

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiaudit.app.domain.model.Audit
import com.wifiaudit.app.domain.model.AuditStatus
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.Measurement
import com.wifiaudit.app.domain.model.Position
import com.wifiaudit.app.domain.model.RepeaterPosition
import com.wifiaudit.app.domain.model.ScanMode
import com.wifiaudit.app.domain.model.SignalQuality
import com.wifiaudit.app.domain.repository.AuditRepository
import com.wifiaudit.app.domain.usecase.GetScanCooldownUseCase
import com.wifiaudit.app.domain.usecase.GetScanModeUseCase
import com.wifiaudit.app.domain.usecase.LogScanSessionSummaryUseCase
import com.wifiaudit.app.domain.usecase.RunPingUseCase
import com.wifiaudit.app.domain.usecase.ScanWifiUseCase
import com.wifiaudit.app.domain.usecase.SetScanModeUseCase
import com.wifiaudit.app.presentation.AuditCreationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil

private const val TAG = "WIFI_AUDIT"

data class MeasurementPoint(
    val id: String,
    val x: Float,
    val y: Float,
    val quality: SignalQuality
)

/** Représente l'appareil pour lequel l'utilisateur doit mesurer dans la phase guidée. */
data class GuidedDeviceInfo(
    val deviceId: String,   // "gateway" ou ID du répéteur
    val label: String,      // "votre box" / "votre répéteur"
    val isGateway: Boolean,
    val position: Position  // position sur le plan, pour pré-sélectionner automatiquement
)

data class MeasureUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val measurements: List<MeasurementPoint> = emptyList(),
    val pendingPosition: Pair<Float, Float>? = null,
    val planImagePath: String? = null,
    val error: String? = null,
    /** Non-null tant que la phase guidée est en cours (mesures calibrage). */
    val guidedDevice: GuidedDeviceInfo? = null,
    /** Secondes à patienter avant de pouvoir mesurer (throttle Android). 0 = prêt. */
    val scanCooldownSeconds: Int = 0,
    /** Mode de scan actuellement actif. */
    val scanMode: ScanMode = ScanMode.STANDARD,
    /** Message éphémère (toast) — ex. bascule auto en mode standard. */
    val toastMessage: String? = null,
    /** Mesures prises en phase libre uniquement (hors calibrage). */
    val freeMeasurementCount: Int = 0
) {
    val measurementCount: Int get() = measurements.size
    /** Terminer uniquement quand la calibration est terminée ET qu'au moins 1 mesure libre a été prise. */
    val canFinish: Boolean get() = guidedDevice == null && freeMeasurementCount > 0
    val canMeasure: Boolean get() = pendingPosition != null && !isLoading && scanCooldownSeconds == 0
}

// Cadence délibérée entre deux mesures (mode STANDARD) : ~10 s d'espacement des scans + ~5 s de
// déplacement. Au-delà de 4 mesures rapprochées, le throttle Android (4 scans / 2 min) peut
// prolonger l'attente : le compte à rebours affiché = max(cette cadence, délai throttle réel).
const val MEASUREMENT_INTERVAL_MS = 15_000L

// Cadence délibérée en mode RAPIDE : throttling désactivé par l'utilisateur → scans enchaînés
// (~1 scan / 5 s, le temps d'un scan complet + un court déplacement).
const val FAST_MEASUREMENT_INTERVAL_MS = 5_000L

@HiltViewModel
class MeasureViewModel @Inject constructor(
    private val scanWifiUseCase: ScanWifiUseCase,
    private val runPingUseCase: RunPingUseCase,
    private val getScanCooldownUseCase: GetScanCooldownUseCase,
    private val getScanModeUseCase: GetScanModeUseCase,
    private val setScanModeUseCase: SetScanModeUseCase,
    private val logScanSessionSummaryUseCase: LogScanSessionSummaryUseCase,
    private val auditRepository: AuditRepository
) : ViewModel() {

    private val auditId = UUID.randomUUID().toString()
    private var targetSsid: String? = null
    private var rooms: List<CanvasRoom> = emptyList()

    private val fullMeasurements = mutableListOf<Measurement>()

    // Instant (elapsedRealtime) de la dernière mesure terminée — pour la cadence délibérée.
    private var lastMeasurementAt: Long = 0L

    // Job du scan en cours — permet d'annuler une mesure lancée par erreur.
    private var measureJob: Job? = null

    // Intervalle délibéré courant entre deux mesures — dépend du mode (standard vs rapide).
    private var intervalMs: Long = MEASUREMENT_INTERVAL_MS

    // File d'attente de la phase guidée — chaque appareil placé génère une entrée
    private val guidedQueue = ArrayDeque<GuidedDeviceInfo>()
    private var guidedInitialized = false

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    init {
        val currentMode = getScanModeUseCase()
        intervalMs = intervalForMode(currentMode)
        _uiState.update { it.copy(scanMode = currentMode) }

        // Ticker : compte à rebours « feu vert » — ignoré en mode expert (cooldown toujours 0).
        viewModelScope.launch {
            while (true) {
                val isExpert = _uiState.value.scanMode == ScanMode.FAST
                val seconds = if (isExpert) 0 else {
                    val throttleMs = getScanCooldownUseCase()
                    val deliberateMs = if (lastMeasurementAt == 0L) 0L
                        else (intervalMs - (SystemClock.elapsedRealtime() - lastMeasurementAt))
                            .coerceAtLeast(0L)
                    val remainingMs = maxOf(throttleMs, deliberateMs)
                    if (remainingMs <= 0) 0 else ceil(remainingMs / 1000.0).toInt()
                }
                if (seconds != _uiState.value.scanCooldownSeconds) {
                    _uiState.update { it.copy(scanCooldownSeconds = seconds) }
                }
                delay(500)
            }
        }
    }

    private fun intervalForMode(mode: ScanMode): Long =
        if (mode == ScanMode.FAST) FAST_MEASUREMENT_INTERVAL_MS else MEASUREMENT_INTERVAL_MS

    // ─── Choix du mode de scan ───────────────────────────────────────────────


    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun applyMode(mode: ScanMode) {
        setScanModeUseCase(mode)
        intervalMs = intervalForMode(mode)
        _uiState.update { it.copy(scanMode = mode) }
    }

    /**
     * Detects an engine-side FAST → STANDARD auto-fallback (the throttle was actually still active)
     * and re-syncs the deliberate cadence. Returns a user-facing toast message, or null if nothing
     * changed.
     */
    private fun detectModeFallback(): String? {
        val previous = _uiState.value.scanMode
        val now = getScanModeUseCase()
        if (previous == ScanMode.FAST && now == ScanMode.STANDARD) {
            intervalMs = intervalForMode(now)
            return "La recherche Wi-Fi est encore limitée par votre téléphone. " +
                   "Passage automatique en mode standard."
        }
        return null
    }

    fun setPlanImagePath(path: String) {
        _uiState.update { it.copy(planImagePath = path) }
    }

    fun setTargetSsid(ssid: String?) {
        targetSsid = ssid
    }

    fun setRooms(canvasRooms: List<CanvasRoom>) {
        rooms = canvasRooms
    }

    /**
     * Initialise la file de mesures guidées depuis les appareils placés sur le plan.
     * Appelé une seule fois au démarrage de l'écran de mesure.
     * Ordre : box en premier, puis répéteurs.
     */
    fun setDevices(gatewayPos: Position?, repeaterPositions: List<RepeaterPosition>) {
        if (guidedInitialized) return
        guidedInitialized = true

        gatewayPos?.let {
            guidedQueue.addLast(GuidedDeviceInfo("gateway", "votre box", isGateway = true, position = it))
        }
        repeaterPositions.forEachIndexed { idx, rep ->
            val label = if (repeaterPositions.size == 1) "votre répéteur"
                        else "répéteur ${idx + 1}"
            guidedQueue.addLast(GuidedDeviceInfo(rep.id, label, isGateway = false, position = rep.position))
        }

        val first = guidedQueue.firstOrNull()
        Log.d(TAG, "Phase guidée initialisée — ${guidedQueue.size} appareil(s) : ${guidedQueue.map { it.deviceId }}")

        _uiState.update { it.copy(
            guidedDevice    = first,
            // Pré-sélectionner la position du premier appareil : l'utilisateur n'a qu'à appuyer sur "Mesurer ici"
            pendingPosition = first?.position?.let { pos -> pos.x to pos.y }
        )}
    }

    fun selectPosition(x: Float, y: Float) {
        _uiState.update { it.copy(pendingPosition = x to y, error = null) }
    }

    /** Mode expert libre : tap = mesure immédiate sans attendre le bouton. */
    fun selectAndMeasure(x: Float, y: Float) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(pendingPosition = x to y, error = null) }
        launchMeasurement()
    }

    fun takeMeasurement() {
        if (!_uiState.value.canMeasure) return
        launchMeasurement()
    }

    private fun launchMeasurement() {
        val pos = _uiState.value.pendingPosition ?: return
        val currentGuided = guidedQueue.firstOrNull()
        val hint = currentGuided?.deviceId
        val isExpertFreePhase = _uiState.value.scanMode == ScanMode.FAST && currentGuided == null

        measureJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scanWifiUseCase(pos.first, pos.second, auditId, targetSsid, deviceHint = hint).fold(
                onSuccess = { measurement ->
                    val roomId = rooms.firstOrNull { it.bounds.contains(measurement.x, measurement.y) }?.id
                    val finalMeasurement = measurement.copy(roomId = roomId)
                    fullMeasurements += finalMeasurement

                    lastMeasurementAt = SystemClock.elapsedRealtime()
                    val fellBackToastMsg = detectModeFallback()

                    if (currentGuided != null) guidedQueue.removeFirst()
                    val nextDevice = guidedQueue.firstOrNull()

                    Log.d(TAG, buildString {
                        append("Mesure #${fullMeasurements.size}")
                        if (hint != null) append(" [guidée: $hint]")
                        appendLine(" — RSSI=${finalMeasurement.rssi}dBm quality=${finalMeasurement.quality}")
                        if (nextDevice != null) appendLine("  → prochaine mesure guidée : ${nextDevice.deviceId}")
                        else if (currentGuided != null) appendLine("  → phase guidée terminée, mesures libres")
                    })

                    val isFreePhase = currentGuided == null
                    _uiState.update { s ->
                        s.copy(
                            isLoading            = false,
                            guidedDevice         = nextDevice,
                            pendingPosition      = nextDevice?.position?.let { p -> p.x to p.y },
                            scanCooldownSeconds  = 0,
                            scanMode             = getScanModeUseCase(),
                            toastMessage         = fellBackToastMsg ?: s.toastMessage,
                            freeMeasurementCount = if (isFreePhase) s.freeMeasurementCount + 1 else s.freeMeasurementCount,
                            measurements         = s.measurements + MeasurementPoint(
                                id      = finalMeasurement.id,
                                x       = finalMeasurement.x,
                                y       = finalMeasurement.y,
                                quality = finalMeasurement.quality
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

    /** Supprime une mesure par son id (mode expert, phase libre uniquement). */
    fun removeMeasurement(id: String) {
        fullMeasurements.removeIf { it.id == id }
        _uiState.update { s ->
            s.copy(
                measurements         = s.measurements.filter { it.id != id },
                freeMeasurementCount = (s.freeMeasurementCount - 1).coerceAtLeast(0)
            )
        }
    }

    /** Annule la mesure en cours (tap accidentel) — le scan est interrompu, aucune donnée gardée. */
    fun cancelMeasurement() {
        if (measureJob?.isActive != true) return
        measureJob?.cancel()
        measureJob = null
        Log.d(TAG, "Mesure annulée par l'utilisateur")
        _uiState.update { it.copy(isLoading = false) }
    }

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
                    val hintStr = m.deviceHint?.let { " hint=$it" } ?: ""
                    appendLine("    [${i+1}] x=%.3f y=%.3f rssi=${m.rssi}dBm bssid=${m.bssid}$hintStr".format(m.x, m.y))
                }
            })

            // Résumé de diagnostic de la session de scan (réussis / échoués / périmés) — tag WifiDiagScan.
            logScanSessionSummaryUseCase()

            auditRepository.saveAudit(audit)
            _uiState.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
