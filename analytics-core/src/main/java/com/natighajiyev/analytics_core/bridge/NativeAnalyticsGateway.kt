package com.natighajiyev.analytics_core.bridge

import java.util.HashMap

/**
 * JNI Boundary Hub bridging the Kotlin application layer to the native C++ telemetry engine.
 *
 * This component manages raw virtual memory structures using Linux `mmap` calls, allowing
 * high-frequency interaction metrics to be recorded asynchronously with nanosecond latency.
 */
internal object NativeAnalyticsGateway {
    init { System.loadLibrary("analytics_core") }

    /**
     * Initializes the native storage ring buffer by mapping a physical binary file
     * footprint directly into virtual memory pages via an underlying `mmap` allocation descriptor.
     * @return `true` if memory allocation mappings were safely constructed.
     */
    external fun nativeStartEngine(filepath: String): Boolean

    /**
     * Serializes an interaction frame directly down into raw memory bytes. Clamps metadata
     * arrays to prevent violating fixed binary struct boundary alignments.
     */
    external fun nativeLogEventWithMetadata(
        screenId: String, timestamp: Long, x: Double, y: Double,
        keysArray: Array<String>, valuesArray: Array<String>, pairCount: Int
    )

    /**
     * Logs a screen touch event along with the current screen size and orientation
     * directly into the native C++ storage layer.
     *
     * Storing the screen width and height at the exact moment of the tap allows the
     * backend to convert raw pixel coordinates into relative percentages ($X\%, Y\%$).
     * This normalization enables the web dashboard to render accurate visual touch heatmaps
     * regardless of the user's device size or screen rotation
     */
    external fun nativeLogEventWithLayout(
        screenId: String,
        timestamp: Long,
        x: Double,
        y: Double,
        screenWidth: Int,
        screenHeight: Int,
        orientation: String,
        keyArray: Array<String>,
        valuesArray: Array<String>,
        pairCount: Int
    )

    /**
     * Polls the next oldest telemetry sequence currently buffered in the native `mmap` storage file.
     * @return A flattened key-value structure containing the structured metric snapshot, or `null` if empty.
     */
    external fun nativePollEvent(): HashMap<String, Any>?

    /**
     * Erases the head element block from the internal ring buffer queue layout, updating memory pointer structures.
     */
    external fun nativePopEvent()

    /**
     * @return The absolute number of records currently sitting in the local binary cache queue.
     */
    external fun nativeGetPendingCount(): Int

    /**
     * Unmaps virtual memory pages, synchronization locks, and releases raw system file descriptors cleanly.
     */
    external fun nativeStopEngine()
}