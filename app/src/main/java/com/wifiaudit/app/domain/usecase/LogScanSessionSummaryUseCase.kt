package com.wifiaudit.app.domain.usecase

import com.wifiaudit.app.domain.repository.WifiRepository
import javax.inject.Inject

/** Writes the end-of-audit scan diagnostic summary (ok / failed / stale counters) to the logs. */
class LogScanSessionSummaryUseCase @Inject constructor(
    private val wifiRepository: WifiRepository
) {
    operator fun invoke() = wifiRepository.logScanSessionSummary()
}
