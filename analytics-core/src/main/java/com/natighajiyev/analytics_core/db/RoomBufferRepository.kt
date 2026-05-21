package com.natighajiyev.analytics_core.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class RoomBufferRepository @Inject constructor(
    private val spatialBufferDao: SpatialBufferDao
) {

    suspend fun insertPayload(screenId: String, timestamp: Long, payload: ByteArray, params: String) {
        withContext(Dispatchers.IO) {
            val entity = SpatialBufferEntity(
                screenId = screenId,
                timestamp = timestamp,
                payload = payload,
                params = params
            )
            spatialBufferDao.insertBufferItem(entity)
        }
    }

    suspend fun fetchUploadBatch(batchSize: Int): List<SpatialBufferEntity> {
        return withContext(Dispatchers.IO) {
            spatialBufferDao.getOldestPayloadBatch(batchSize)
        }
    }

    suspend fun clearUploadedItems(ids: List<Long>) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            spatialBufferDao.deletePayloads(ids)
        }
    }
}