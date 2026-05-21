package com.natighajiyev.alphametricsanalyticssdk

import android.app.Application
import com.natighajiyev.analytics_core.engine.AnalyticsEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AnalyticsApplication : Application() {
    @Inject
    lateinit var analyticsEngine: AnalyticsEngine

    override fun onTerminate() {
        // Cleanly halts the channel queue workers and closes the native memory pipeline references
        analyticsEngine.shutdown()
        super.onTerminate()
    }
}