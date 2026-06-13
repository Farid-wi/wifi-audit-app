package com.wifiaudit.app.domain.repository

import com.wifiaudit.app.domain.model.Audit
import kotlinx.coroutines.flow.Flow

// Interface dans le domaine — pas de types Android, pas de Room, pas de Retrofit
interface AuditRepository {
    fun observeAudits(): Flow<List<Audit>>
    suspend fun saveAudit(audit: Audit)
    suspend fun submitAudit(audit: Audit): Result<Unit>
    suspend fun getPendingAudits(): List<Audit>
    suspend fun getAuditById(id: String): Audit?
}
