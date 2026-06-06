package com.wifiaudit.app.presentation.screen.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiaudit.app.domain.repository.WifiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetworkSelectionUiState(
    val networks: List<WifiRepository.VisibleNetwork> = emptyList(),
    val selectedSsid: String? = null,
    val isLoading: Boolean = false,       // false par défaut — on attend la permission d'abord
    val permissionDenied: Boolean = false
)

@HiltViewModel
class NetworkSelectionViewModel @Inject constructor(
    private val wifiRepository: WifiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkSelectionUiState())
    val uiState: StateFlow<NetworkSelectionUiState> = _uiState.asStateFlow()

    fun loadNetworks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, permissionDenied = false) }
            val networks = wifiRepository.getVisibleNetworks()
            val connectedSsid = networks.firstOrNull { it.isConnected }?.ssid
            _uiState.update {
                it.copy(
                    networks     = networks,
                    selectedSsid = connectedSsid ?: it.selectedSsid,
                    isLoading    = false
                )
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(isLoading = false, permissionDenied = true) }
    }

    fun selectNetwork(ssid: String) {
        _uiState.update { it.copy(selectedSsid = ssid) }
    }
}
