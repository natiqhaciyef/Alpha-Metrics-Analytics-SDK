package com.natighajiyev.analytics_core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpatialBufferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBufferItem(entity: SpatialBufferEntity)

    @Query("SELECT * FROM spatial_analytics_buffer ORDER BY timestamp ASC LIMIT :batchSize")
    suspend fun getOldestPayloadBatch(batchSize: Int): List<SpatialBufferEntity>

    @Query("DELETE FROM spatial_analytics_buffer WHERE id IN (:ids)")
    suspend fun deletePayloads(ids: List<Long>)
}