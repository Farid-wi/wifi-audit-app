package com.wifiaudit.app.presentation.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiaudit.app.domain.model.Audit
import com.wifiaudit.app.domain.model.AuditStatus
import com.wifiaudit.app.domain.model.OverallScore
import com.wifiaudit.app.domain.model.SignalQuality
import com.wifiaudit.app.domain.model.rssiToQuality
import com.wifiaudit.app.domain.repository.AuditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditListItem(
    val id: String,
    val createdAt: Long,
    val name: String,
    val ssid: String,
    val roomCount: Int,
    val measurementCount: Int,
    val status: AuditStatus,
    val score: OverallScore?
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val audits: List<AuditListItem> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val auditRepository: AuditRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auditRepository.observeAudits().collect { audits ->
                _uiState.update {
                    it.copy(isLoading = false, audits = audits.map { a -> a.toListItem() })
                }
            }
        }
    }

    private fun Audit.toListItem(): AuditListItem {
        val onPlanMeasurements = measurements.filter { it.x >= 0f }
        val score = when {
            summary?.overallScore != null -> summary.overallScore
            onPlanMeasurements.isNotEmpty() -> {
                val sorted = onPlanMeasurements.map { it.rssi }.sorted()
                val median = sorted[sorted.size / 2]
                when (rssiToQuality(median)) {
                    SignalQuality.GOOD -> OverallScore.GOOD
                    SignalQuality.FAIR -> OverallScore.FAIR
                    SignalQuality.POOR -> OverallScore.POOR
                }
            }
            else -> null
        }
        return AuditListItem(
            id               = id,
            createdAt        = createdAt,
            name             = name,
            ssid             = ssid,
            roomCount        = rooms.size,
            measurementCount = onPlanMeasurements.size,
            status           = status,
            score            = score
        )
    }
}
