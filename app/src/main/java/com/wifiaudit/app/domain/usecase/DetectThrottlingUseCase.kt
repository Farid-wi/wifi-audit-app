package com.wifiaudit.app.domain.usecase

import com.wifiaudit.app.domain.repository.WifiRepository
import javax.inject.Inject

/**
 * Empirically probes whether the Android Wi-Fi scan throttling is disabled (see
 * [WifiRepository.detectThrottlingDisabled]). Wraps the result so the UI can show a clear message
 * on failure (missing permission, location off, Wi-Fi off).
 *
 * @return Result.success(true) if throttling appears disabled (fast mode usable),
 *         Result.success(false) if it is still active, or Result.failure with a user-facing message.
 */
class DetectThrottlingUseCase @Inject constructor(
    private val wifiRepository: WifiRepository
) {
    suspend operator fun invoke(): Result<Boolean> =
        runCatching { wifiRepository.detectThrottlingDisabled() }
}
