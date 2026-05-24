package com.natighajiyev.analytics_core.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.natighajiyev.analytics_core.bridge.NativeAnalyticsGateway
import com.natighajiyev.analytics_core.config.*
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK
import com.natighajiyev.analytics_core.service.network.HttpAnalyticsDispatcher
import kotlinx.coroutines.*
import java.io.File
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Isolated background execution container handling automated telemetry data egress pipelines.
 *
 * This service runs completely separated from the application's interactive UI process
 * (typically within the `:alphametrics_egress_v1` process boundary). It unbundles incoming configuration
 * properties passed via IPC intent tokens, initializes the native storage file linkage, extracts chunked
 * event batches sequentially, and handles network transport retry loops and fallback server routing.
 *
 * It dynamically configures an underlying [OkHttpClient] equipped with a [CertificatePinner] if an
 * SSL public key pin hash is present, ensuring protection against Man-in-the-Middle (MITM) attacks.
 */
class BackgroundUploadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "AlphaMetrics_Egress"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Intercepts incoming trigger intent execution requests. Launches an independent coroutine
     * block to unpack parameters and fire the off-thread memory egress upload sequence.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentBundle = intent ?: return START_NOT_STICKY

        serviceScope.launch {
            try {
                handleEgressPipeline(intentBundle)
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error inside background processing pipeline worker loop.", e)
            } catch (f: CancellationException) {
                throw f
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    /**
     * The true batch chunking extraction logic engine. Unpacks configuration parameters, queries
     * the native binary memory map cache state, evaluates cryptographic pinning layers, and coordinates
     * network upload passes.
     *
     * @param intentBundle The incoming intent command holding network parameters, batch constraints, and security hashes.
     */
    private suspend fun handleEgressPipeline(intentBundle: Intent) {
        val loggingActive = intentBundle.getBooleanExtra(SDK_LOGGING, false)

        val primaryUrl = intentBundle.getStringExtra(NET_ENDPOINT)
        val backupUrl = intentBundle.getStringExtra(NET_BACKUP)
        val connectTimeout = intentBundle.getLongExtra(NET_CONN_TIMEOUT, 10000)
        val readTimeout = intentBundle.getLongExtra(NET_READ_TIMEOUT, 10000)
        val pinningHash = intentBundle.getStringExtra(NET_PINNING_HASH)

        if (primaryUrl.isNullOrEmpty()) {
            if (loggingActive) Log.w(TAG, "Egress aborted. Core server endpoint parameter string resolved as empty.")
            return
        }

        val headers = intentBundle.getSerializableExtra(NET_HEADERS) as? Map<String, String> ?: emptyMap()

        val maxBatchSize = intentBundle.getIntExtra(BATCH_MAX, 50)
        val minTrigger = intentBundle.getIntExtra(BATCH_MIN_TRIGGER, 10)
        val retryLimit = intentBundle.getIntExtra(BATCH_RETRY_LIMIT, 3)
        val backoffDelay = intentBundle.getLongExtra(BATCH_BACKOFF, 2000)
        val sessionLimit = intentBundle.getIntExtra(BATCH_SESSION_LIMIT, 200)

        val baseDataDir = applicationInfo.dataDir
        val targetPath = File(baseDataDir, AlphaMetricsSDK.BIN_FILE_NAME).absolutePath
        if (!NativeAnalyticsGateway.nativeStartEngine(targetPath)) return

        val pendingCount = NativeAnalyticsGateway.nativeGetPendingCount()

        if (pendingCount < minTrigger) {
            if (loggingActive) {
                Log.d(TAG, "Buffer size ($pendingCount) below min trigger limit ($minTrigger). Stashing events for next session run.")
            }
            NativeAnalyticsGateway.nativeStopEngine()
            return
        }

        var totalSessionRemaining = if (pendingCount > sessionLimit) sessionLimit else pendingCount
        if (loggingActive) Log.d(TAG, "Egress active. Total items in file: $pendingCount. Capping upload at: $totalSessionRemaining items for this session.")

        val okHttpClient = if (!pinningHash.isNullOrEmpty()) {
            try {
                val hostDomain = URL(primaryUrl).host
                val pinner = CertificatePinner.Builder()
                    .add(hostDomain, "sha256/$pinningHash")
                    .build()

                OkHttpClient.Builder()
                    .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                    .certificatePinner(pinner)
                    .build()
            } catch (e: Exception) {
                if (loggingActive) Log.e(TAG, "Failed assembling SSL Certificate Pinning engine. Falling back to default transport pipeline.", e)
                null
            }
        } else {
            null
        }

        val dispatcher = HttpAnalyticsDispatcher(
            okHttpClient = okHttpClient,
            endpointUrl = primaryUrl,
            connectionTimeoutMs = connectTimeout.toInt(),
            readTimeoutMs = readTimeout.toInt(),
            headerMap = headers
        )

        while (totalSessionRemaining > 0) {
            if (!currentCoroutineContext().isActive) break

            val currentBatchList = mutableListOf<HashMap<String, Any>>()
            val itemsToPull = if (totalSessionRemaining > maxBatchSize) maxBatchSize else totalSessionRemaining

            for (i in 0 until itemsToPull) {
                val eventData = NativeAnalyticsGateway.nativePollEvent() ?: break
                currentBatchList.add(eventData)
                NativeAnalyticsGateway.nativePopEvent() // Step the binary pointer forward
            }

            if (currentBatchList.isEmpty()) break

            var uploadSuccess = false
            var attempts = 0

            while (!uploadSuccess && attempts < retryLimit) {
                if (!currentCoroutineContext().isActive) break

                uploadSuccess = dispatcher.dispatchBatchEvent(currentBatchList)
                if (!uploadSuccess) {
                    attempts++
                    if (loggingActive) Log.w(TAG, "Batch transmission failed. Retrying attempt $attempts/$retryLimit in ${backoffDelay}ms...")
                    delay(backoffDelay)
                }
            }

            if (uploadSuccess) {
                totalSessionRemaining -= currentBatchList.size
                if (loggingActive) Log.d(TAG, "Successfully shipped batch chunk of ${currentBatchList.size} events.")
            } else {
                if (backupUrl != null && currentCoroutineContext().isActive) {
                    if (loggingActive) Log.i(TAG, "Primary route failed. Diverting traffic straight to fallback system: $backupUrl")

                    val backupClient = if (!pinningHash.isNullOrEmpty()) {
                        try {
                            val backupHost = URL(backupUrl).host
                            val pinner = CertificatePinner.Builder().add(backupHost, "sha256/$pinningHash").build()
                            OkHttpClient.Builder()
                                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                                .certificatePinner(pinner)
                                .build()
                        } catch (e: Exception) { null }
                    } else {
                        null
                    }

                    val backupDispatcher = HttpAnalyticsDispatcher(
                        okHttpClient = backupClient,
                        endpointUrl = backupUrl,
                        connectionTimeoutMs = connectTimeout.toInt(),
                        readTimeoutMs = readTimeout.toInt(),
                        headerMap = headers
                    )

                    if (backupDispatcher.dispatchBatchEvent(currentBatchList)) {
                        totalSessionRemaining -= currentBatchList.size
                        continue
                    }
                }

                if (loggingActive) Log.e(TAG, "Network pipeline down. Safe retention rules applied. Retaining remaining logs on disk.")
                break
            }
        }

        NativeAnalyticsGateway.nativeStopEngine()
        if (loggingActive) Log.d(TAG, "Batch processing run finished cleanly. Moving service instance to idle closed state.")
    }

    /**
     * Instantly clean up working coroutine tracking handles to protect against background
     * memory leakage and clear context task hooks completely.
     */
    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}