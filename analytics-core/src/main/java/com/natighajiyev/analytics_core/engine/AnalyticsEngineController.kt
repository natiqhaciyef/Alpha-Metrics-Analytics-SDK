package com.natighajiyev.analytics_core.engine

import android.app.Application
import android.content.Context
import android.util.Log
import com.natighajiyev.analytics_core.bridge.NativeAnalyticsGateway
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


@Singleton
class AnalyticsEngineController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    init {
        val application = context as Application
        AlphaMetricsSDK.initialize(application)
    }

    fun logTouchStream(screenId: String, x: Double, y: Double, metadata: Map<String, String>) {
        AlphaMetricsSDK.trackScreenEvent(screenId, x, y, metadata)

        // Debug check: How many events are currently sitting in the file?
        val count = NativeAnalyticsGateway.nativeGetPendingCount()
        Log.d("AlphaMetrics_Debug", "Current events in binary queue: $count")
    }

    fun shutdown() {
        AlphaMetricsSDK.tearDown()
    }
}