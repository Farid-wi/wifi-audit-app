package com.wifiaudit.app.domain.repository

import com.wifiaudit.app.domain.model.SavedPlan
import kotlinx.coroutines.flow.Flow

interface SavedPlanRepository {
    fun observeAll(): Flow<List<SavedPlan>>
    suspend fun save(plan: SavedPlan)
    suspend fun delete(planId: String)
}
