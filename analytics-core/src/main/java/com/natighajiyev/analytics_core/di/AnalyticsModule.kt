package com.natighajiyev.analytics_core.di

import android.content.Context
import com.natighajiyev.analytics_core.config.AlphaMetricsConfig
import com.natighajiyev.analytics_core.engine.AnalyticsEngineController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideAlphaMetricsConfig(): AlphaMetricsConfig {
        return AlphaMetricsConfig.Builder().apply {
            network {
                serverEndpoint = "https://analytics.backend.com/api/v2/metrics"
                connectTimeout = 15
                readTimeout = 15
                timeoutUnit = TimeUnit.SECONDS
            }
            batch {
                maxBatchSize = 50
                minBatchSizeTrigger = 1
                retryAttemptLimit = 3
                backoffDelay = 2000
            }
            security {
                useEncryption = false
                allowCleartextTraffic = true
            }
            setLoggingEnabled(true)
        }.build()
    }

    @Provides
    @Singleton
    fun provideAnalyticsEngineController(
        @ApplicationContext context: Context,
        config: AlphaMetricsConfig
    ): AnalyticsEngineController = AnalyticsEngineController(context, config)
}