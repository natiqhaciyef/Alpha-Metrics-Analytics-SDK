package com.natighajiyev.analytics_core.engine

import android.app.Application
import android.content.Context
import android.util.Log
import com.natighajiyev.analytics_core.bridge.NativeAnalyticsGateway
import com.natighajiyev.analytics_core.config.AlphaMetricsConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

private const val TAG = "AlphaMetrics_Debug"

@Singleton
class AnalyticsEngineController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AlphaMetricsConfig
) {
    init {
        // Resolve application context cleanly
        val application = context.applicationContext as Application

        // Initialize the SDK with the extended network and batch configuration matrices
        AlphaMetricsSDK.initialize(application, config)

        if (config.isLoggingEnabled) {
            Log.d(TAG, "AnalyticsEngineController linked to native engine lifecycle hooks.")
        }
    }

    fun logTouchStream(screenId: String, x: Double, y: Double, metadata: Map<String, String>) {
        AlphaMetricsSDK.trackScreenEvent(screenId, x, y, metadata)
        // Debug check: How many events are currently sitting in the file?
        val count = NativeAnalyticsGateway.nativeGetPendingCount()
        if (config.isLoggingEnabled)
            Log.d(TAG, "Current events in binary queue - $count: \n{screenId: $screenId, x: $x, y: $y, metadata: $metadata}")
    }

    fun shutdown() {
        AlphaMetricsSDK.tearDown()
    }
}