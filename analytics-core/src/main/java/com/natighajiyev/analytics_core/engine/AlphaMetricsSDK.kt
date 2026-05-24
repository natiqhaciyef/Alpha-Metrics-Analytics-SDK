package com.natighajiyev.analytics_core.engine

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.natighajiyev.analytics_core.bridge.NativeAnalyticsGateway
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
import com.natighajiyev.analytics_core.config.StorageConfig
import com.natighajiyev.analytics_core.config.exceptionDetector.AlphaAnrWatchdog
import com.natighajiyev.analytics_core.config.exceptionDetector.AlphaCrashTracer
import com.natighajiyev.analytics_core.service.BackgroundUploadService
import java.io.File

object AlphaMetricsSDK : Application.ActivityLifecycleCallbacks {
    private const val TAG = "AlphaMetricsSDK"
    private var isInitialized = false
    private var runningActivitiesCount = 0
    internal const val BIN_FILE_NAME = "alpha_metrics_core.bin"

    internal var currentConfig: AlphaMetricsConfig? = null

    private var anrWatchdog: AlphaAnrWatchdog? = null

    /**
     * Entry point to configure and boot the high-performance tracking pipeline.
     */
    fun initialize(application: Application, config: AlphaMetricsConfig?) {
        if (isInitialized) return
        this.currentConfig = config

        val baseDataDir = application.applicationInfo.dataDir
        val targetPath = File(baseDataDir, BIN_FILE_NAME).absolutePath

        if (NativeAnalyticsGateway.nativeStartEngine(targetPath)) {
            isInitialized = true
            application.registerActivityLifecycleCallbacks(this)

            if (config?.trapCrashes == true) {
                val systemDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()

                if (systemDefaultHandler !is AlphaCrashTracer) {
                    Thread.setDefaultUncaughtExceptionHandler(AlphaCrashTracer(systemDefaultHandler))
                }

                if (config.isLoggingEnabled) {
                    Log.d(TAG, "Global Crash Trapping pipeline activated successfully.")
                }

                anrWatchdog = AlphaAnrWatchdog(timeoutMs = 5000).apply { start() }
                if (config.isLoggingEnabled) {
                    Log.d(TAG, "Concurrently mapped AlphaAnrWatchdog monitor engine thread instance successfully.")
                }
            } else {
                if (config?.isLoggingEnabled == true) {
                    Log.d(TAG, "Global Crash Trapping skipped per configuration instruction rules.")
                }
            }

            if (config?.isLoggingEnabled == true) {
                Log.d(TAG, "Main UI Process memory map engine successfully initialized.")
            }
        } else {
            Log.e(TAG, "CRITICAL: Native mmap storage initialization failure on file footprint matching sequence.")
        }
    }

    /**
     * Intercepts and encodes touch metrics down to the non-blocking ring buffer cache layer.
     */
    fun trackScreenEvent(screenId: String, x: Double, y: Double, customParams: Map<String, String>) {
        if (!isInitialized) return
        val config = currentConfig ?: return

        val pendingCount = NativeAnalyticsGateway.nativeGetPendingCount()
        if (pendingCount >= config.storageConfig.maxQueueCapacity) {
            if (config.storageConfig.strategyOnBufferFull == StorageConfig.FullStrategy.DROP_NEWEST) {
                if (config.isLoggingEnabled) {
                    Log.w(TAG, "Storage saturated ($pendingCount events). Strategy is DROP_NEWEST. Discarding incoming event.")
                }
                return
            } else {
                if (config.isLoggingEnabled) {
                    Log.i(TAG, "Storage saturated ($pendingCount events). Strategy is PURGE_OLDEST. Evicting tail head item.")
                }
                NativeAnalyticsGateway.nativePopEvent()
            }
        }

        val boundParams = customParams.entries.take(5)
        val keys = boundParams.map { it.key }.toTypedArray()
        val values = boundParams.map { it.value }.toTypedArray()

        NativeAnalyticsGateway.nativeLogEventWithMetadata(
            screenId, System.currentTimeMillis(), x, y, keys, values, boundParams.size
        )
    }

    /**
     * Manually breaks the C++ virtual link mappings and halts local telemetry storage.
     */
    fun tearDown() {
        if (!isInitialized) return

        anrWatchdog?.shutdown()
        anrWatchdog = null

        NativeAnalyticsGateway.nativeStopEngine()
        currentConfig = null
        isInitialized = false
    }

    override fun onActivityStarted(activity: Activity) {
        runningActivitiesCount++
    }

    override fun onActivityStopped(activity: Activity) {
        if (runningActivitiesCount > 0) {
            runningActivitiesCount--
        }

        if (runningActivitiesCount == 0) {
            val config = currentConfig

            if (config == null) {
                Log.w(TAG, "Egress aborted. Lifecycle triggered stop state before SDK configuration was bound.")
                return
            }

            if (config.isLoggingEnabled) {
                Log.d(TAG, "UI interaction stopped entirely. Triggering background isolated single-shot batch dump...")
            }

            val intent = Intent(activity, BackgroundUploadService::class.java).apply {
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
                activity.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed launching egress batch background task. State container mismatch execution constraint.", e)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}