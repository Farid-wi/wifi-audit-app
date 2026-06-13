package com.wifiaudit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wifiaudit.app.data.local.dao.AuditDao
import com.wifiaudit.app.data.local.dao.MeasurementDao
import com.wifiaudit.app.data.local.dao.SavedPlanDao
import com.wifiaudit.app.data.local.entity.AuditEntity
import com.wifiaudit.app.data.local.entity.MeasurementEntity
import com.wifiaudit.app.data.local.entity.SavedPlanEntity

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE audits ADD COLUMN name TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [AuditEntity::class, MeasurementEntity::class, SavedPlanEntity::class],
    version = 9,
    exportSchema = false
)
abstract class WifiAuditDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun savedPlanDao(): SavedPlanDao
}
