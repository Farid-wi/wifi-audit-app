package com.wifiaudit.app.domain.usecase

import com.wifiaudit.app.domain.model.ScanMode
import com.wifiaudit.app.domain.repository.WifiRepository
import javax.inject.Inject

/** Returns the persisted scan mode (defaults to [ScanMode.STANDARD]). */
class GetScanModeUseCase @Inject constructor(
    private val wifiRepository: WifiRepository
) {
    operator fun invoke(): ScanMode = wifiRepository.getScanMode()
}
