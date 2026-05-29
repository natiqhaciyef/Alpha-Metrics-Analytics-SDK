package com.natighajiyev.analytics_core.network.usecase

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
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
import com.natighajiyev.analytics_core.config.NET_PINNING_HASH
import com.natighajiyev.analytics_core.config.NET_READ_TIMEOUT
import com.natighajiyev.analytics_core.config.SDK_LOGGING
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK
import com.natighajiyev.analytics_core.network.dispatchers.DispatcherFactory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Domain-level business logic controller encapsulating the batch telemetry egress pipeline.
 *
 * This use case handles data parsing, file interaction boundaries, and transactional upload routines
 * without forcing dependency injection constraints on the consumer. It is designed to be fully
 * process-isolated, decoupling core transactional operations from Android lifecycle containers.
 *
 * This class uses a private constructor to restrict direct instantiation. Instantiation must be handled
 * via its static companion provider [create].
 */
class EgressPipelineUseCase private constructor() {
    companion object {
        private const val TAG = "AlphaMetrics_Egress"


        /**
         * Unlocked creational factory acting as a manual initialization hook.
         * Provides thread-safe access to this use case layout without embedding formal DI dependencies.
         *
         * @return A standalone instance of [EgressPipelineUseCase].
         */
        fun create(): EgressPipelineUseCase {
            return EgressPipelineUseCase()
        }
    }

    /**
     * Executes the sequential transactional chunking loop. Unpacks incoming configuration intent bundles,
     * opens the binary memory-mapped ring cache, tracks network attempt allocations, and safely manages
     * secondary server failover trees.
     *
     * @param intentBundle Inter-process payload channel containing network configuration strings and transmission ceilings.
     * @param applicationInfo OS package metadata descriptors utilized to resolve localized sandboxed disk paths.
     */
    suspend operator fun invoke(
        intentBundle: Intent,
        applicationInfo: ApplicationInfo
    ) {
        val loggingActive = intentBundle.getBooleanExtra(SDK_LOGGING, false)

        val primaryUrl = intentBundle.getStringExtra(NET_ENDPOINT)
        val backupUrl = intentBundle.getStringExtra(NET_BACKUP)
        val connectTimeout = intentBundle.getLongExtra(NET_CONN_TIMEOUT, 10000)
        val readTimeout = intentBundle.getLongExtra(NET_READ_TIMEOUT, 10000)
        val pinningHash = intentBundle.getStringExtra(NET_PINNING_HASH)

        if (primaryUrl.isNullOrEmpty()) {
            if (loggingActive) Log.w(
                TAG,
                "Egress aborted. Core server endpoint parameter string resolved as empty."
            )
            return
        }

        @Suppress("UNCHECKED_CAST")
        val headers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intentBundle.getSerializableExtra(
                NET_HEADERS,
                HashMap::class.java
            ) as? Map<String, String>
        } else {
            @Suppress("DEPRECATION")
            intentBundle.getSerializableExtra(NET_HEADERS) as? Map<String, String>
        } ?: emptyMap()

        val maxBatchSize = intentBundle.getIntExtra(BATCH_MAX, 50)
        val minTrigger = intentBundle.getIntExtra(BATCH_MIN_TRIGGER, 10)
        val retryLimit = intentBundle.getIntExtra(BATCH_RETRY_LIMIT, 3)
        val backoffDelay = intentBundle.getLongExtra(BATCH_BACKOFF, 2000)
        val sessionLimit = intentBundle.getIntExtra(BATCH_SESSION_LIMIT, 200)

        val baseDataDir = applicationInfo.dataDir
        val targetPath = File(baseDataDir, AlphaMetricsSDK.BIN_FILE_NAME).absolutePath

        try {
            if (!NativeAnalyticsGateway.nativeStartEngine(targetPath)) return

            val pendingCount = NativeAnalyticsGateway.nativeGetPendingCount()

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

            var totalSessionRemaining =
                if (pendingCount > sessionLimit) sessionLimit else pendingCount
            if (loggingActive) Log.d(
                TAG,
                "Egress active. Total items in file: $pendingCount. Capping upload at: $totalSessionRemaining items for this session."
            )

            val dispatcher = DispatcherFactory.createSecureDispatcher(
                endpointUrl = primaryUrl,
                connectTimeoutMs = connectTimeout,
                readTimeoutMs = readTimeout,
                headers = headers,
                pinningHash = pinningHash
            )

            while (totalSessionRemaining > 0) {
                if (!currentCoroutineContext().isActive) break

                val currentBatchList = mutableListOf<HashMap<String, Any>>()
                val itemsToPull =
                    if (totalSessionRemaining > maxBatchSize) maxBatchSize else totalSessionRemaining

                try {
                    for (i in 0 until itemsToPull) {
                        val eventData = NativeAnalyticsGateway.nativePollEvent() ?: break
                        currentBatchList.add(eventData)
                        NativeAnalyticsGateway.nativePopEvent()
                    }
                } catch (internalEx: Exception) {
                    if (loggingActive) {
                        Log.e(
                            TAG,
                            "Corrupted record parsed from native cache memory. Dropping corrupt record segment.",
                            internalEx
                        )
                    }

                    NativeAnalyticsGateway.nativePopEvent()
                    totalSessionRemaining--

                    continue
                }

                if (currentBatchList.isEmpty()) break

                var uploadSuccess = false
                var attempts = 0


                while (!uploadSuccess && attempts < retryLimit) {
                    if (!currentCoroutineContext().isActive) break

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
                    if (backupUrl != null && currentCoroutineContext().isActive) {
                        if (loggingActive) Log.i(
                            TAG,
                            "Primary route failed. Diverting traffic straight to fallback system: $backupUrl"
                        )

                        val backupDispatcher = DispatcherFactory.createSecureDispatcher(
                            endpointUrl = backupUrl,
                            connectTimeoutMs = connectTimeout,
                            readTimeoutMs = readTimeout,
                            headers = headers,
                            pinningHash = pinningHash
                        )

                        if (backupDispatcher.dispatchBatchEvent(currentBatchList)) {
                            totalSessionRemaining -= currentBatchList.size
                            continue
                        }
                    }

                    if (loggingActive)
                        Log.e(
                            TAG,
                            "Network pipeline down. Safe retention rules applied. Retaining remaining logs on disk."
                        )
                    break
                }
            }
        } catch (e: Exception) {
            if (loggingActive) {
                Log.e(
                    TAG,
                    "Fatal pipeline exception encountered during background egress serialization.",
                    e
                )
            }
        } finally {
            NativeAnalyticsGateway.nativeStopEngine()
            if (loggingActive) Log.d(
                TAG,
                "Batch processing run finished cleanly. Moving service instance to idle closed state."
            )
        }
    }
}
