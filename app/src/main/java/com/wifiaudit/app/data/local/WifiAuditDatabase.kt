package com.wifiaudit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wifiaudit.app.data.local.dao.AuditDao
import com.wifiaudit.app.data.local.dao.MeasurementDao
import com.wifiaudit.app.data.local.entity.AuditEntity
import com.wifiaudit.app.data.local.entity.MeasurementEntity

@Database(
    entities = [AuditEntity::class, MeasurementEntity::class],
    version = 6,
    exportSchema = false
)
abstract class WifiAuditDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao
    abstract fun measurementDao(): MeasurementDao
}
