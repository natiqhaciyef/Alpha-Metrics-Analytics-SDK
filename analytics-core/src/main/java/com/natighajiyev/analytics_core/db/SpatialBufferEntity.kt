package com.natighajiyev.analytics_core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spatial_analytics_buffer")
data class SpatialBufferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val screenId: String,
    val timestamp: Long,
    val payload: ByteArray,
    val params: String
) {
    // Standard overrides for safe array handling in data classes
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SpatialBufferEntity
        if (id != other.id) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}