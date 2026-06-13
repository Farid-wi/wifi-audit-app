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

    @Query("SELECT * FROM saved_plans WHERE id = :id")
    suspend fun getById(id: String): SavedPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: SavedPlanEntity)

    @Query("UPDATE saved_plans SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM saved_plans WHERE id = :planId")
    suspend fun delete(planId: String)
}
