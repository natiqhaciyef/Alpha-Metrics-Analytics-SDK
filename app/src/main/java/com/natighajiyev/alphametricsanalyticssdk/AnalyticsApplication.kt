package com.natighajiyev.alphametricsanalyticssdk

import android.app.Application
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AnalyticsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AlphaMetricsSDK.initialize(this)
    }
}