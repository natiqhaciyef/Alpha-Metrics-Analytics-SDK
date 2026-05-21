package com.natighajiyev.analytics_core.model

internal data class RawTouchEvent(
    val screenId: String,
    val wallClockTime: Long,
    val timestamps: LongArray,
    val xCoords: FloatArray,
    val yCoords: FloatArray,
    val params: HashMap<String, String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawTouchEvent

        if (wallClockTime != other.wallClockTime) return false
        if (screenId != other.screenId) return false
        if (!timestamps.contentEquals(other.timestamps)) return false
        if (!xCoords.contentEquals(other.xCoords)) return false
        if (!yCoords.contentEquals(other.yCoords)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = wallClockTime.hashCode()
        result = 31 * result + screenId.hashCode()
        result = 31 * result + timestamps.contentHashCode()
        result = 31 * result + xCoords.contentHashCode()
        result = 31 * result + yCoords.contentHashCode()
        return result
    }
}