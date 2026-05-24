package com.natighajiyev.analytics_core.model

import java.util.HashMap

/**
 * An intermediate, structural data container representing a batch of multi-touch interaction vectors.
 *
 * This data class aggregates raw hardware gesture arrays before structural compression or JNI serialization.
 * Since it encapsulates primitive array types ([LongArray] and [FloatArray]), it explicitly overrides
 * [equals] and [hashCode] to guarantee deep, content-based data equality evaluations instead of checking
 * raw object memory references.
 *
 * @property screenId The human-readable identifier of the UI container or activity catching the interaction sequence.
 * @property wallClockTime The system baseline timestamp in milliseconds when the gesture batch initiated.
 * @property timestamps A sequential array of delta timestamps matching individual touch point updates.
 * @property xCoords Captured horizontal relative screen coordinates mapping the touch trajectory path.
 * @property yCoords Captured vertical relative screen coordinates mapping the touch trajectory path.
 * @property params Custom key-value runtime configuration attributes accompanying this specific transaction stream.
 */
internal data class RawTouchEvent(
    val screenId: String,
    val wallClockTime: Long,
    val timestamps: LongArray,
    val xCoords: FloatArray,
    val yCoords: FloatArray,
    val params: HashMap<String, String>,
) {
    /**
     * Evaluates structural equality by comparing literal primitive values inside arrays
     * using [contentEquals] rather than assessing object instance identities.
     */
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

    /**
     * Computes a deep hash signature mapping the internal data values of the primitive arrays
     * using [contentHashCode] to satisfy hash bucket distribution lookups.
     */
    override fun hashCode(): Int {
        var result = wallClockTime.hashCode()
        result = 31 * result + screenId.hashCode()
        result = 31 * result + timestamps.contentHashCode()
        result = 31 * result + xCoords.contentHashCode()
        result = 31 * result + yCoords.contentHashCode()
        return result
    }
}