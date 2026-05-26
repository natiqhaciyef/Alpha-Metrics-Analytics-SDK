package com.natighajiyev.analytics_core.engine

import android.app.Application
import android.content.Context
import android.util.Log
import com.natighajiyev.analytics_core.bridge.NativeAnalyticsGateway
import com.natighajiyev.analytics_core.config.AlphaMetricsConfig

/**

┌──────────────────────────────────────────────────────────────────────────┐
│                          KOTLIN CORE SDK MODULE                          │
│                                                                          │
│   ┌─────────────────────┐       ┌───────────────────────────────┐        │
│   │  Jetpack Interceptor│ ────> │    AnalyticsEngine (Core)     │        │
│   └─────────────────────┘       └───────────────┬───────────────┘        │
│                                                 │                        │
│                    ┌────────────────────────────┼──────────────────────┐ │
│                    ▼                            ▼                      ▼ │
│       ┌───────────────────────────┐   ┌────────────────────────┐  ┌───────────┐
│       │ AnalyticsCoreBridge (JNI) │   │     AnalyticsEvent     │  │  Network  │
│       └───────────────────────────┘   └────────────────────────┘  └───────────┘
└──────────────────────────────────────────────────────────────────────────┘

 * */

/**
 * Unified, dependency-injected abstraction layer acting as the sole public gateway for client UI tracking.
 *
 * This controller is managed as a application-scoped thread-safe [Singleton]. It completely decouples
 * your presentation layers from the underlying low-level JNI linkages, preventing view components
 * from altering configuration footprints or accidentally executing unauthorized teardown routines.
 *
 * @property context The application context utilized to bind system lifecycle registration frames.
 * @property config The central immutable profile governing operational, cryptographic, and storage thresholds.
 */

private const val TAG = "AlphaMetrics_Debug"

class AnalyticsEngineController(
    private val context: Context,
    private val config: AlphaMetricsConfig
) {
    init {
        // Resolve application context cleanly to extract the root Application link
        val application = context.applicationContext as Application

        // Initialize the SDK with the extended network and batch configuration matrices
        AlphaMetricsSDK.initialize(application, config)

        if (config.isLoggingEnabled) {
            Log.d(TAG, "AnalyticsEngineController linked to native engine lifecycle hooks.")
        }
    }

    /**
     * Intercepts and dispatches raw interaction touch vectors down into the high-speed native memory map pipeline.
     *
     * @param screenId The human-readable identifier of the originating view container or activity node.
     * @param x The exact horizontal relative pixel density value mapped during the tap event window.
     * @param y The exact vertical relative pixel density value mapped during the tap event window.
     * @param metadata A map of dynamic context descriptors (e.g., component names or workflow identifiers).
     */
    fun logTouchStream(screenId: String, x: Double, y: Double, metadata: Map<String, String>) {
        AlphaMetricsSDK.trackScreenEvent(screenId, x, y, metadata)

        // Debug check: Evaluate current queue accumulation metrics sitting inside the binary file structure
        val count = NativeAnalyticsGateway.nativeGetPendingCount()
        if (config.isLoggingEnabled) {
            Log.d(TAG, "Current events in binary queue - $count: \n{screenId: $screenId, x: $x, y: $y, metadata: $metadata}")
        }
    }

    /**
     * Terminate the underlying native engine connections and disconnect memory map page bridges.
     * * *CRITICAL WARNING:* Because this instance is scoped as a application-wide Singleton, this operation
     * should only be executed during administrative cleanup routines, integration test teardowns, or hard application logout states.
     */
    fun shutdown() {
        AlphaMetricsSDK.tearDown()
    }
}