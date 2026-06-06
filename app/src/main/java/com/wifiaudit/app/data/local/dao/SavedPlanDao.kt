package com.wifiaudit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wifiaudit.app.data.local.entity.SavedPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlanDao {
    @Query("SELECT * FROM saved_plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: SavedPlanEntity)

    @Query("DELETE FROM saved_plans WHERE id = :planId")
    suspend fun delete(planId: String)
}
