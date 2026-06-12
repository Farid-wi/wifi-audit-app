package com.wifiaudit.app.domain.usecase

import com.wifiaudit.app.domain.model.ScanMode
import com.wifiaudit.app.domain.repository.WifiRepository
import javax.inject.Inject

/** Persists the chosen scan mode and starts a fresh diagnostic session. */
class SetScanModeUseCase @Inject constructor(
    private val wifiRepository: WifiRepository
) {
    operator fun invoke(mode: ScanMode) = wifiRepository.setScanMode(mode)
}
