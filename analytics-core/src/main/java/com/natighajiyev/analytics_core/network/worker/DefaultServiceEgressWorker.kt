package com.natighajiyev.analytics_core.network.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import com.natighajiyev.analytics_core.config.AlphaEgressWorker
import com.natighajiyev.analytics_core.config.AlphaMetricsConfig
import com.natighajiyev.analytics_core.config.BATCH_BACKOFF
import com.natighajiyev.analytics_core.config.BATCH_MAX
import com.natighajiyev.analytics_core.config.BATCH_MIN_TRIGGER
import com.natighajiyev.analytics_core.config.BATCH_RETRY_LIMIT
import com.natighajiyev.analytics_core.config.BATCH_SESSION_LIMIT
import com.natighajiyev.analytics_core.config.NET_BACKUP
import com.natighajiyev.analytics_core.config.NET_CONN_TIMEOUT
import com.natighajiyev.analytics_core.config.NET_ENDPOINT
import com.natighajiyev.analytics_core.config.NET_HEADERS
import com.natighajiyev.analytics_core.config.NET_PINNING_HASH
import com.natighajiyev.analytics_core.config.NET_READ_TIMEOUT
import com.natighajiyev.analytics_core.config.SDK_LOGGING
import com.natighajiyev.analytics_core.config.SEC_CLEAR_TEXT
import com.natighajiyev.analytics_core.config.SEC_ENCRYPT
import com.natighajiyev.analytics_core.service.BackgroundUploadService

/**
 * Standard, ready-made egress worker implementation that dispatches synchronization tasks 
 * straight into an explicit command-pattern [BackgroundUploadService] process loop.
 */
internal class DefaultServiceEgressWorker : AlphaEgressWorker {
    
    override fun onEgressTriggered(context: Context, config: AlphaMetricsConfig) {
        val intent = Intent(context, BackgroundUploadService::class.java).apply {
            putExtra(NET_ENDPOINT, config.networkConfig.serverEndpoint)
            putExtra(NET_BACKUP, config.networkConfig.backupEndpoint)
            putExtra(NET_CONN_TIMEOUT, config.networkConfig.connectTimeoutMs)
            putExtra(NET_READ_TIMEOUT, config.networkConfig.readTimeoutMs)
            putExtra(NET_HEADERS, HashMap(config.networkConfig.customHeaders))

            putExtra(BATCH_MAX, config.batchConfig.maxBatchSize)
            putExtra(BATCH_MIN_TRIGGER, config.batchConfig.minBatchSizeTrigger)
            putExtra(BATCH_RETRY_LIMIT, config.batchConfig.retryAttemptLimit)
            putExtra(BATCH_BACKOFF, config.batchConfig.backoffDelayMs)
            putExtra(BATCH_SESSION_LIMIT, config.batchConfig.maxEventsPerBackgroundSession)

            putExtra(SEC_ENCRYPT, config.securityConfig.useEncryption)
            putExtra(SEC_CLEAR_TEXT, config.securityConfig.allowCleartextTraffic)
            putExtra(NET_PINNING_HASH, config.securityConfig.pinPinningHash)

            putExtra(SDK_LOGGING, config.isLoggingEnabled)
        }

        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.e("AlphaMetricsSDK", "Failed launching default egress background task. OS platform state validation mismatch.", e)
        }
    }
}