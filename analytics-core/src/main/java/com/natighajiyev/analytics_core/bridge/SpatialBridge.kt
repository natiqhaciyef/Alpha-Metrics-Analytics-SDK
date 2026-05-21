package com.natighajiyev.analytics_core.bridge

import android.util.Log


object SpatialBridge{

    init {
        try {
            System.loadLibrary("spatial_bridge")
            initializeEnginePipeline()
            Log.i("SpatialBridge", "Native C++ binary bridge linked successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("SpatialBridge", "CRITICAL: Native binary initialization failed", e)
        }
    }

    fun streamRealTimeCoordinates(timestamp: Long, x: Double, y: Double, z: Double): Int {
        return processSpatialData(timestamp, x, y, z)
    }

    fun serializeBatchPayload(xCoords: FloatArray, yCoords: FloatArray, timestamps: LongArray, metadata: String): ByteArray {
        return normalizeAndSerialize(xCoords, yCoords, timestamps, metadata)
    }

    fun releaseNativePipeline() {
        shutdownEnginePipeline()
    }

    // --- Native JNI Core Links ---
    private external fun initializeEnginePipeline()
    private external fun processSpatialData(timestamp: Long, x: Double, y: Double, z: Double): Int
    private external fun normalizeAndSerialize(x: FloatArray, y: FloatArray, ts: LongArray, metadata: String): ByteArray
    private external fun shutdownEnginePipeline()
}