package com.wifiaudit.app.domain.usecase

import com.wifiaudit.app.domain.model.Audit
import com.wifiaudit.app.domain.repository.AuditRepository
import javax.inject.Inject

class SubmitAuditUseCase @Inject constructor(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(audit: Audit): Result<Unit> =
        auditRepository.submitAudit(audit)
}
