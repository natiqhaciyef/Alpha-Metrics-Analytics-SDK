package com.natighajiyev.analytics_core.service


import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.natighajiyev.analytics_core.bridge.NativeAnalyticsGateway
import com.natighajiyev.analytics_core.config.BATCH_BACKOFF
import com.natighajiyev.analytics_core.config.BATCH_MAX
import com.natighajiyev.analytics_core.config.BATCH_MIN_TRIGGER
import com.natighajiyev.analytics_core.config.BATCH_RETRY_LIMIT
import com.natighajiyev.analytics_core.config.NET_BACKUP
import com.natighajiyev.analytics_core.config.NET_CONN_TIMEOUT
import com.natighajiyev.analytics_core.config.NET_ENDPOINT
import com.natighajiyev.analytics_core.config.NET_HEADERS
import com.natighajiyev.analytics_core.config.NET_READ_TIMEOUT
import com.natighajiyev.analytics_core.config.SDK_LOGGING
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK
import com.natighajiyev.analytics_core.service.network.HttpAnalyticsDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class BackgroundUploadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "AlphaMetrics_Egress"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentBundle = intent ?: return START_NOT_STICKY

        serviceScope.launch {
            try {
                handleEgressPipeline(intentBundle)
            } catch (e: Exception) {
                Log.e(TAG, "Fatal execution failure inside background processing thread.", e)
            } finally {
                // Ensure self-termination fires to free system RAM
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun handleEgressPipeline(intentBundle: Intent) {
        // FIX: Explicitly reference the AlphaMetricsSDK constant variables mapping structure
        val loggingActive = intentBundle.getBooleanExtra(SDK_LOGGING, false)

        // 1. Reconstruct Network properties from the IPC bridge payload channel
        val primaryUrl = intentBundle.getStringExtra(NET_ENDPOINT)
        val backupUrl = intentBundle.getStringExtra(NET_BACKUP)
        val connectTimeout = intentBundle.getLongExtra(NET_CONN_TIMEOUT, 10000)
        val readTimeout = intentBundle.getLongExtra(NET_READ_TIMEOUT, 10000)

        if (primaryUrl.isNullOrEmpty()) {
            if (loggingActive) Log.w(
                TAG,
                "Egress aborted. Core server endpoint parameter string resolved as empty."
            )
            return
        }

        @Suppress("UNCHECKED_CAST")
        val headers =
            intentBundle.getSerializableExtra(NET_HEADERS) as? Map<String, String> ?: emptyMap()

        // 2. Reconstruct Batch parameters
        val maxBatchSize = intentBundle.getIntExtra(BATCH_MAX, 50)
        val minTrigger = intentBundle.getIntExtra(BATCH_MIN_TRIGGER, 10)
        val retryLimit = intentBundle.getIntExtra(BATCH_RETRY_LIMIT, 3)
        val backoffDelay = intentBundle.getLongExtra(BATCH_BACKOFF, 2000)

        val baseDataDir = applicationInfo.dataDir
        // Match binary file name exactly
        val targetPath = File(baseDataDir, AlphaMetricsSDK.BIN_FILE_NAME).absolutePath
        if (!NativeAnalyticsGateway.nativeStartEngine(targetPath)) return

        val pendingCount = NativeAnalyticsGateway.nativeGetPendingCount()

        // Adaptive Skip Execution: If cache density doesn't meet consumer trigger thresholds, hold payload
        if (pendingCount < minTrigger) {
            if (loggingActive) {
                Log.d(
                    TAG,
                    "Buffer size ($pendingCount) below min trigger limit ($minTrigger). Stashing events for next run."
                )
            }
            NativeAnalyticsGateway.nativeStopEngine()
            return
        }

        var processLimit = if (pendingCount > maxBatchSize) maxBatchSize else pendingCount
        if (loggingActive) Log.d(
            TAG,
            "Processing started. Attempting transmission batch dump for $processLimit frames."
        )

        // Feed full networking configurations dynamically into our dispatcher
        val dispatcher = HttpAnalyticsDispatcher(
            endpointUrl = primaryUrl,
            readTimeoutMs = readTimeout.toInt(),
            connectionTimeoutMs = connectTimeout.toInt(),
            headerMap = headers
        )

        while (processLimit > 0) {
            // Ensure we respect service lifecycle cancellation requests before calling out to JNI
            if (!currentCoroutineContext().isActive) break

            val eventData = NativeAnalyticsGateway.nativePollEvent() ?: break
            var uploadSuccess = false
            var attempts = 0
            Log.d(
                TAG,
                "Processing event data: \n$eventData"
            )
            // Retry policy engine processing logic configuration loop
            while (!uploadSuccess && attempts < retryLimit) {
                if (!currentCoroutineContext().isActive) break

                uploadSuccess = dispatcher.dispatchEvent(eventData)
                if (!uploadSuccess) {
                    attempts++
                    if (loggingActive) {
                        Log.w(
                            TAG,
                            "Upload failed. Retry attempt $attempts/$retryLimit in ${backoffDelay}ms..."
                        )
                    }
                    delay(backoffDelay)
                }
            }

            if (uploadSuccess) {
                // Instantly drops event from binary storage file
                NativeAnalyticsGateway.nativePopEvent()
                processLimit--
            } else {
                // Failover path execution rule: Try shipping remaining cache chunks over to backup endpoint URL
                if (backupUrl != null && currentCoroutineContext().isActive) {
                    if (loggingActive) {
                        Log.i(
                            TAG,
                            "Primary route failed. Diverting traffic straight to fallback system: $backupUrl"
                        )
                    }
                    val backupDispatcher = HttpAnalyticsDispatcher(
                        backupUrl,
                        connectTimeout.toInt(),
                        readTimeout.toInt(),
                        headers
                    )
                    if (backupDispatcher.dispatchEvent(eventData)) {
                        NativeAnalyticsGateway.nativePopEvent()
                        processLimit--
                        continue
                    }
                }
                // Halt pipeline if both main and backup servers are completely unreachable
                break
            }
        }
        NativeAnalyticsGateway.nativeStopEngine()
        if (loggingActive) Log.d(
            TAG,
            "Batch processing run finished cleanly. Moving service instance to dead pool state."
        )
    }

    override fun onDestroy() {
        // Cancel all pending coroutines immediately if the OS kills the process prematurely
        serviceJob.cancel()
        super.onDestroy()
    }
}