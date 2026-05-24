package com.natighajiyev.analytics_core.engine

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.natighajiyev.analytics_core.bridge.NativeAnalyticsGateway
import com.natighajiyev.analytics_core.config.AlphaMetricsConfig
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

    // Explicit background watchdog reference tracking handle allocation
    private var anrWatchdog: AlphaAnrWatchdog? = null

    // --- Intent Extra Constants (Maintained for seamless multi-process extractions) ---
    internal const val NET_ENDPOINT = "NET_ENDPOINT"
    internal const val NET_BACKUP = "NET_BACKUP"
    internal const val NET_CONN_TIMEOUT = "NET_CONN_TIMEOUT"
    internal const val NET_READ_TIMEOUT = "NET_READ_TIMEOUT"
    internal const val NET_HEADERS = "NET_HEADERS"

    internal const val BATCH_MAX = "BATCH_MAX"
    internal const val BATCH_MIN_TRIGGER = "BATCH_MIN_TRIGGER"
    internal const val BATCH_RETRY_LIMIT = "BATCH_RETRY_LIMIT"
    internal const val BATCH_BACKOFF = "BATCH_BACKOFF"
    internal const val BATCH_SESSION_LIMIT = "BATCH_SESSION_LIMIT"

    internal const val SEC_ENCRYPT = "SEC_ENCRYPT"
    internal const val SEC_CLEAR_TEXT = "SEC_CLEAR_TEXT"
    internal const val SDK_LOGGING = "SDK_LOGGING"

    /**
     * Entry point to configure and boot the high-performance tracking pipeline.
     */
    fun initialize(application: Application, config: AlphaMetricsConfig?) {
        if (isInitialized) return
        this.currentConfig = config

        val baseDataDir = application.applicationInfo.dataDir
        val targetPath = File(baseDataDir, BIN_FILE_NAME).absolutePath

        // Initialize the native mmap memory layer for the Main UI process
        if (NativeAnalyticsGateway.nativeStartEngine(targetPath)) {
            isInitialized = true
            application.registerActivityLifecycleCallbacks(this)

            // We safely check the config object for the trapCrashes rule state
            if (config?.trapCrashes == true) {
                val systemDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()

                if (systemDefaultHandler !is AlphaCrashTracer) {
                    Thread.setDefaultUncaughtExceptionHandler(AlphaCrashTracer(systemDefaultHandler))
                }

                if (config.isLoggingEnabled) {
                    Log.d(TAG, "Global Crash Trapping pipeline activated successfully.")
                }

                // Spawns the concurrent background monitor tracking thread
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

        // Fetch current density directly from the C++ layer
        val pendingCount = NativeAnalyticsGateway.nativeGetPendingCount()

        // Validate if local cache capacity restrictions have been breached
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

        // Clamp down parameters count to comply with static binary struct footprint limits
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

        // CRITICAL CHECK: If runningActivitiesCount reaches 0, the user has exited the UI completely!
        if (runningActivitiesCount == 0) {
            val config = currentConfig

            if (config == null) {
                Log.w(TAG, "Egress aborted. Lifecycle triggered stop state before SDK configuration was bound.")
                return
            }

            if (config.isLoggingEnabled) {
                Log.d(TAG, "UI interaction stopped entirely. Triggering background isolated single-shot batch dump...")
            }

            // Construct and pack flattened extra types across process limits via implicit Intent bundles
            val intent = Intent(activity, BackgroundUploadService::class.java).apply {
                // Flatten Network Sub-Tree Options
                putExtra(NET_ENDPOINT, config.networkConfig.serverEndpoint)
                putExtra(NET_BACKUP, config.networkConfig.backupEndpoint)
                putExtra(NET_CONN_TIMEOUT, config.networkConfig.connectTimeoutMs)
                putExtra(NET_READ_TIMEOUT, config.networkConfig.readTimeoutMs)
                putExtra(NET_HEADERS, HashMap(config.networkConfig.customHeaders))

                // Flatten Batch Policy Settings
                putExtra(BATCH_MAX, config.batchConfig.maxBatchSize)
                putExtra(BATCH_MIN_TRIGGER, config.batchConfig.minBatchSizeTrigger)
                putExtra(BATCH_RETRY_LIMIT, config.batchConfig.retryAttemptLimit)
                putExtra(BATCH_BACKOFF, config.batchConfig.backoffDelayMs)

                // STORAGE & BUDGET SYNC: Forward the background per-session transmission ceiling rule
                putExtra(BATCH_SESSION_LIMIT, config.batchConfig.maxEventsPerBackgroundSession)

                // Flatten Security Sub-Tree Profiles
                putExtra(SEC_ENCRYPT, config.securityConfig.useEncryption)
                putExtra(SEC_CLEAR_TEXT, config.securityConfig.allowCleartextTraffic)

                // Pass operational diagnostic flags
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