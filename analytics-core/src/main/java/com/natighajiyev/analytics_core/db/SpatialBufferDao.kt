package com.natighajiyev.analytics_core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpatialBufferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBufferItem(entity: SpatialBufferEntity)

    // Used later by your upload sync manager to grab chunks of data
    @Query("SELECT * FROM spatial_analytics_buffer ORDER BY timestamp ASC LIMIT :batchSize")
    suspend fun getOldestPayloadBatch(batchSize: Int): List<SpatialBufferEntity>

    // Used to clean up storage after network transfer successes
    @Query("DELETE FROM spatial_analytics_buffer WHERE id IN (:ids)")
    suspend fun deletePayloads(ids: List<Long>)
}