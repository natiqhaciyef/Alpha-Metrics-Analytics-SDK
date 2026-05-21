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
        analyticsEngine.shutdown()
        super.onTerminate()
    }
}