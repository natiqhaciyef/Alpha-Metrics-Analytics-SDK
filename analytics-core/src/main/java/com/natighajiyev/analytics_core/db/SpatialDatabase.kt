package com.natighajiyev.analytics_core.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SpatialBufferEntity::class], version = 1, exportSchema = false)
abstract class SpatialDatabase : RoomDatabase() {
    
    abstract fun spatialBufferDao(): SpatialBufferDao
}