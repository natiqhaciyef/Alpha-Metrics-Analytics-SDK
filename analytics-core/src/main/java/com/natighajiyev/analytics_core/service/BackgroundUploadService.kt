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
import com.natighajiyev.analytics_core.config.BATCH_SESSION_LIMIT
import com.natighajiyev.analytics_core.config.NET_BACKUP
import com.natighajiyev.analytics_core.config.NET_CONN_TIMEOUT
import com.natighajiyev.analytics_core.config.NET_ENDPOINT
import com.natighajiyev.analytics_core.config.NET_HEADERS
import com.natighajiyev.analytics_core.config.NET_READ_TIMEOUT
import com.natighajiyev.analytics_core.config.SDK_LOGGING
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK
import com.natighajiyev.analytics_core.service.network.HttpAnalyticsDispatcher
import kotlinx.coroutines.*
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
                Log.e(TAG, "Fatal error inside background processing pipeline worker loop.", e)
            } finally {
                // Terminate service container to free up device system memory
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun handleEgressPipeline(intentBundle: Intent) {
        // Safe variable injection mapping using the dynamic companion object keys
        val loggingActive = intentBundle.getBooleanExtra(SDK_LOGGING, false)

        // 1. Reconstruct Network properties from the IPC bridge payload channel using your custom constants
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

        // 2. Reconstruct Batch parameters via package layout constants
        val maxBatchSize = intentBundle.getIntExtra(BATCH_MAX, 50)
        val minTrigger = intentBundle.getIntExtra(BATCH_MIN_TRIGGER, 10)
        val retryLimit = intentBundle.getIntExtra(BATCH_RETRY_LIMIT, 3)
        val backoffDelay = intentBundle.getLongExtra(BATCH_BACKOFF, 2000)
        val sessionLimit = intentBundle.getIntExtra(BATCH_SESSION_LIMIT, 200)

        val baseDataDir = applicationInfo.dataDir
        val targetPath = File(baseDataDir, AlphaMetricsSDK.BIN_FILE_NAME).absolutePath
        if (!NativeAnalyticsGateway.nativeStartEngine(targetPath)) return

        val pendingCount = NativeAnalyticsGateway.nativeGetPendingCount()

        // Adaptive Skip Execution: If cache density doesn't meet consumer trigger thresholds, hold payload
        if (pendingCount < minTrigger) {
            if (loggingActive) {
                Log.d(
                    TAG,
                    "Buffer size ($pendingCount) below min trigger limit ($minTrigger). Stashing events for next session run."
                )
            }
            NativeAnalyticsGateway.nativeStopEngine()
            return
        }

        // Calculate total footprint items allowed for this entire background session sequence loop
        var totalSessionRemaining = if (pendingCount > sessionLimit) sessionLimit else pendingCount
        if (loggingActive) Log.d(
            TAG,
            "Egress active. Total items in file: $pendingCount. Capping upload at: $totalSessionRemaining items for this session."
        )

        val dispatcher = HttpAnalyticsDispatcher(
            endpointUrl = primaryUrl,
            connectionTimeoutMs = connectTimeout.toInt(),
            readTimeoutMs = readTimeout.toInt(),
            headerMap = headers
        )

        // --- TRUE BATCH CHUNKING EXTRACTION LOGIC ENGINE ---
        while (totalSessionRemaining > 0) {
            if (!currentCoroutineContext().isActive) break

            val currentBatchList = mutableListOf<HashMap<String, Any>>()
            val itemsToPull =
                if (totalSessionRemaining > maxBatchSize) maxBatchSize else totalSessionRemaining

            // Extract a clean sub-batch slice from the memory mapped file allocation block
            for (i in 0 until itemsToPull) {
                val eventData = NativeAnalyticsGateway.nativePollEvent() ?: break
                currentBatchList.add(eventData)
                NativeAnalyticsGateway.nativePopEvent() // Step the binary pointer forward
            }

            if (currentBatchList.isEmpty()) break

            var uploadSuccess = false
            var attempts = 0

            // Retry processing execution matrix
            while (!uploadSuccess && attempts < retryLimit) {
                if (!currentCoroutineContext().isActive) break

                // Transmit the sub-list package rather than solo isolated items
                uploadSuccess = dispatcher.dispatchBatchEvent(currentBatchList)
                if (!uploadSuccess) {
                    attempts++
                    if (loggingActive) Log.w(
                        TAG,
                        "Batch transmission failed. Retrying attempt $attempts/$retryLimit in ${backoffDelay}ms..."
                    )
                    delay(backoffDelay)
                }
            }

            if (uploadSuccess) {
                totalSessionRemaining -= currentBatchList.size
                if (loggingActive) Log.d(
                    TAG,
                    "Successfully shipped batch chunk of ${currentBatchList.size} events."
                )
            } else {
                // Failover path routing logic to backup server gateway infrastructure clusters
                if (backupUrl != null && currentCoroutineContext().isActive) {
                    if (loggingActive) Log.i(
                        TAG,
                        "Primary route failed. Diverting traffic straight to fallback system: $backupUrl"
                    )
                    val backupDispatcher = HttpAnalyticsDispatcher(
                        backupUrl,
                        connectTimeout.toInt(),
                        readTimeout.toInt(),
                        headers
                    )
                    if (backupDispatcher.dispatchBatchEvent(currentBatchList)) {
                        totalSessionRemaining -= currentBatchList.size
                        continue
                    }
                }

                // Halt pipeline if network connectivity goes dead to prevent data destruction loops
                if (loggingActive) Log.e(
                    TAG,
                    "Network pipeline down. Safe retention rules applied. Retaining remaining logs on disk."
                )
                break
            }
        }

        NativeAnalyticsGateway.nativeStopEngine()
        if (loggingActive) Log.d(
            TAG,
            "Batch processing run finished cleanly. Moving service instance to idle closed state."
        )
    }

    override fun onDestroy() {
        serviceJob.cancel() // Instantly clean up working coroutine tracking handles
        super.onDestroy()
    }
}