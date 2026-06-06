package com.wifiaudit.app.data.remote.api

import com.wifiaudit.app.data.remote.dto.AuditPayload
import com.wifiaudit.app.data.remote.dto.AuditSummaryDto
import com.wifiaudit.app.data.remote.dto.SubmitResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AuditApiService {

    @POST("audits")
    suspend fun submitAudit(@Body payload: AuditPayload): Response<SubmitResponse>

    @GET("audits")
    suspend fun listAudits(): Response<List<AuditSummaryDto>>

    @DELETE("audits/{audit_id}")
    suspend fun deleteAudit(@Path("audit_id") auditId: String): Response<Unit>

    @GET("health")
    suspend fun health(): Response<Map<String, String>>
}
