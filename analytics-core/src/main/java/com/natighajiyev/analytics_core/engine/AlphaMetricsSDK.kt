package com.natighajiyev.analytics_core.engine

import android.app.Activity
import android.app.Application
import android.content.Context
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
import com.natighajiyev.analytics_core.network.worker.DefaultServiceEgressWorker
import com.natighajiyev.analytics_core.service.BackgroundUploadService
import java.io.File

/**
 * Centrally managed, event-driven tracking pipeline coordinating low-latency data telemetry ingestion.
 *
 * This core engine acts as an integrated [Application.ActivityLifecycleCallbacks] observer to automatically
 * track application visibility states. It binds direct structural interactions down into a raw C++
 * memory-mapped file layer (`alpha_metrics_core.bin`), maps active thread pools for automated crash
 * and ANR diagnostics, and handles off-process batch transfers upon application background termination
 * routines.
 *
 *
 *                                ┌───────────────────────────┐
 *                                │     AlphaMetricsSDK       │
 *                                │  (Main App Process JVM)   │
 *                                └─────────────┬─────────────┘
 *                                              │
 *                        Tracks Event Taps     │  On App Background
 *                        via JNI Wrappers      │  (or Manual flush)
 *                                              ▼
 *                                ┌───────────────────────────┐
 *                                │  alpha_metrics_core.bin   │
 *                                │ (Native mmap File Space)  │
 *                                └─────────────┬─────────────┘
 *                                              │
 *                                              ▼
 *                                ┌───────────────────────────┐
 *                                │   AlphaEgressWorker       │
 *                                │      (Interface)          │
 *                                └──────┬─────────────┬──────┘
 *                                       │             │
 *                  If custom worker     │             │ If default worker
 *                  is supplied          │             │ is configured
 *                  ▼                    │             │ ▼
 *    ┌──────────────────────────────────┐             ┌──────────────────────────────────┐
 *    │    Custom Consumer Worker        │             │   DefaultServiceEgressWorker     │
 *    │ (Runs in Main Application Space) │             │  (Triggers IPC Intent Commands)  │
 *    └────────────────┬─────────────────┘             └────────────────┬─────────────────┘
 *                     │                                                │
 *                     │ Launches Custom Logic                          │ Starts Isolated Process
 *                     ▼                                                ▼
 *    ┌──────────────────────────────────┐             ┌──────────────────────────────────┐
 *    │     Developer Pipeline           │             │     BackgroundUploadService      │
 *    │ (WorkManager, Workoutines, etc.) │             │   (Runs in :alphametrics_egress) │
 *    └────────────────┬─────────────────┘             └────────────────┬─────────────────┘
 *                     │                                                │
 *                     │ Extracts flat data                             │ Invokes sequential Use Case
 *                     ▼                                                ▼
 *    ┌──────────────────────────────────┐             ┌──────────────────────────────────┐
 *    │       Target Server Gate         │             │      EgressPipelineUseCase       │
 *    │ (Custom Infrastructure Endpoints)│             │ (Chunked Processing & Failovers) │
 *    └──────────────────────────────────┘             └────────────────┬─────────────────┘
 *                                                                      │
 *                                                                      │ Assembles Transport Client
 *                                                                      ▼
 *                                                     ┌──────────────────────────────────┐
 *                                                     │    HttpAnalyticsDispatcher       │
 *                                                     │   (Dual-Engine: OkHttp/UrlConn)  │
 *                                                     └────────────────┬─────────────────┘
 *                                                                      │
 *                                                                      │ Direct Network Post
 *                                                                      ▼
 *                                                     ┌──────────────────────────────────┐
 *                                                     │       Primary/Backup Server      │
 *                                                     │    (REST Ingestion Aggregators)  │
 *                                                     └──────────────────────────────────┘
 *
 *
 */
object AlphaMetricsSDK : Application.ActivityLifecycleCallbacks {
    private const val TAG = "AlphaMetricsSDK"
    private var isInitialized = false
    private var runningActivitiesCount = 0
    internal const val BIN_FILE_NAME = "alpha_metrics_core.bin"

    internal var currentConfig: AlphaMetricsConfig? = null
    private var anrWatchdog: AlphaAnrWatchdog? = null

    /** Fallback pipeline execution worker instantiated locally to avoid forced DI containers. */
    private val defaultServiceWorker = DefaultServiceEgressWorker()

    /**
     * Bootstraps the non-blocking persistence storage layer, registers active lifecycle hooks, and spins up
     * secondary automated thread-safety monitoring guardrails.
     *
     * @param application Host context instance used to hook structural activity framework components.
     * @param config Targeted parameters mapping memory capacity limits, network endpoints, and diagnostic levels.
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
                    Log.d(
                        TAG,
                        "Concurrently mapped AlphaAnrWatchdog monitor engine thread instance successfully."
                    )
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
            Log.e(
                TAG,
                "CRITICAL: Native mmap storage initialization failure on file footprint matching sequence."
            )
        }
    }

    /**
     * Intercepts and transforms visual layout interactions into raw telemetry segments, routing them cleanly
     * straight to the JNI binary abstraction layer.
     *
     * It enforces memory-allocation safety thresholds based on the configured [StorageConfig.FullStrategy] profiles
     * to protect local system storage footprints from buffer overflow conditions.
     *
     * @param screenId Human-readable descriptor string tagging the origin view node or controller layout frame.
     * @param x Normalized relative horizontal component representing viewport tap density.
     * @param y Normalized relative vertical component representing viewport tap density.
     * @param customParams Contextual key-value descriptors attached to the targeted action workflow.
     */
    fun trackScreenEvent(
        screenId: String,
        x: Double,
        y: Double,
        customParams: Map<String, String> = mutableMapOf()
    ) {
        if (!isInitialized) return
        val config = currentConfig ?: return

        val pendingCount = NativeAnalyticsGateway.nativeGetPendingCount()
        if (pendingCount >= config.storageConfig.maxQueueCapacity) {
            if (config.storageConfig.strategyOnBufferFull == StorageConfig.FullStrategy.DROP_NEWEST) {
                if (config.isLoggingEnabled) {
                    Log.w(
                        TAG,
                        "Storage saturated ($pendingCount events). Strategy is DROP_NEWEST. Discarding incoming event."
                    )
                }
                return
            } else {
                if (config.isLoggingEnabled) {
                    Log.i(
                        TAG,
                        "Storage saturated ($pendingCount events). Strategy is PURGE_OLDEST. Evicting tail head item."
                    )
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
     * Logs a screen touch event along with absolute tap coordinates and current viewport metrics.
     *
     * This function hands over the raw tap points and screen dimensions directly to the native
     * C++ storage engine via JNI. It checks queue limits beforehand to prevent buffer overflows.
     * Providing screen sizes directly enables backend layout normalization for visual touch heatmaps.
     *
     * @param screenId The name or identifier of the visible screen layer.
     * @param x The raw horizontal pixel coordinate of the touch input.
     * @param y The raw vertical pixel coordinate of the touch input.
     * @param screenWidth The current width of the application window in pixels.
     * @param screenHeight The current height of the application window in pixels.
     * @param orientation The structural rotation state of the viewport (`"portrait"` or `"landscape"`).
     */
    fun trackScreenEvent(
        screenId: String,
        x: Double,
        y: Double,
        screenWidth: Int,
        screenHeight: Int,
        orientation: String,
        customParams: Map<String, String> = mutableMapOf()
    ) {
        if (!isInitialized) return
        val config = currentConfig ?: return

        val pendingCount = NativeAnalyticsGateway.nativeGetPendingCount()
        if (pendingCount >= config.storageConfig.maxQueueCapacity) {
            if (config.storageConfig.strategyOnBufferFull == StorageConfig.FullStrategy.DROP_NEWEST) {
                if (config.isLoggingEnabled) {
                    Log.w(
                        TAG,
                        "Storage saturated ($pendingCount events). Strategy is DROP_NEWEST. Discarding incoming event."
                    )
                }
                return
            } else {
                if (config.isLoggingEnabled) {
                    Log.i(
                        TAG,
                        "Storage saturated ($pendingCount events). Strategy is PURGE_OLDEST. Evicting tail head item."
                    )
                }
                NativeAnalyticsGateway.nativePopEvent()
            }
        }

        val boundParams = customParams.entries.take(5)
        val keys = boundParams.map { it.key }.toTypedArray()
        val values = boundParams.map { it.value }.toTypedArray()

        NativeAnalyticsGateway.nativeLogEventWithLayout(
            screenId = screenId,
            timestamp = System.currentTimeMillis(),
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            orientation = orientation,
            keyArray = keys,
            valuesArray = values,
            pairCount = boundParams.size
        )
    }

    /**
     * Explicit public flushing interface mechanism.
     *
     * Allows custom integration layers or host applications to manually force network extraction sweeps
     * on demand without waiting for automatic background lifecycle triggers.
     *
     * @param context Host application context scope.
     */
    fun flushLoggedEvents(context: Context) {
        val config = currentConfig
        if (!isInitialized || config == null) return

        val targetWorker = config.customEgressWorker ?: defaultServiceWorker
        targetWorker.onEgressTriggered(context.applicationContext, config)
    }

    /**
     * Unbinds native virtual layer mappings, terminates active background threads, and detaches system
     * error handler hooks safely.
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

    /**
     * Intercepts application termination visibility boundaries. Once the visible UI component tracking context
     * hits zero, it determines whether to evaluate automatic background egress processing runs based on the active
     * configuration strategies.
     */
    override fun onActivityStopped(activity: Activity) {
        if (runningActivitiesCount > 0) {
            runningActivitiesCount--
        }

        if (runningActivitiesCount == 0) {
            val config = currentConfig ?: return

            if (!config.automaticEgressEnabled) {
                if (config.isLoggingEnabled) {
                    Log.w(
                        TAG,
                        "UI halted. Automated egress disabled. Relying entirely on custom flushing routines."
                    )
                }
                return
            }

            if (config.isLoggingEnabled) {
                Log.d(
                    TAG,
                    "UI interaction stopped entirely. Routing to active egress pipeline workers..."
                )
            }

            val worker = config.customEgressWorker ?: defaultServiceWorker
            worker.onEgressTriggered(activity.applicationContext, config)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}