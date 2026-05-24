package com.natighajiyev.analytics_core.bridge

internal object NativeAnalyticsGateway {
    init { System.loadLibrary("analytics_core") }

    external fun nativeStartEngine(filepath: String): Boolean
    external fun nativeLogEventWithMetadata(
        screenId: String, timestamp: Long, x: Double, y: Double,
        keysArray: Array<String>, valuesArray: Array<String>, pairCount: Int
    )
    external fun nativePollEvent(): HashMap<String, Any>?
    external fun nativePopEvent()
    external fun nativeGetPendingCount(): Int
    external fun nativeStopEngine()
}