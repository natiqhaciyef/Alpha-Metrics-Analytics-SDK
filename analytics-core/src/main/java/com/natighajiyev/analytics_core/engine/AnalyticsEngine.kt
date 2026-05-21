package com.natighajiyev.analytics_core.engine

import com.natighajiyev.analytics_core.bridge.SpatialBridge
import com.natighajiyev.analytics_core.db.RoomBufferRepository
import com.natighajiyev.analytics_core.model.RawTouchEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**

┌────────────────────────────────────────────────────────────────────────┐
│                          KOTLIN CORE SDK MODULE                        │
│                                                                        │
│   ┌─────────────────────┐       ┌───────────────────────────────┐      │
│   │  Jetpack Interceptor│ ────> │    AnalyticsEngine (Core)     │      │
│   └─────────────────────┘       └───────────────┬───────────────┘      │
│                                                 │                      │
│                    ┌────────────────────────────┼────────────────────┐ │
│                    ▼                            ▼                    ▼ │
│       ┌────────────────────────┐   ┌────────────────────────┐  ┌───────────┐
│       │ SpatialBridge (JNI)    │   │ MetadataCollector      │  │ RoomBuffer│
│       └────────────────────────┘   └────────────────────────┘  └───────────┘
└────────────────────────────────────────────────────────────────────────┘

 * */


@Singleton
class AnalyticsEngine @Inject constructor(
    private val spatialBridge: SpatialBridge,
    private val storageRepository: RoomBufferRepository
) {
    // A safe, managed Supervisor Scope tied explicitly to the application lifecycle
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventChannel = Channel<RawTouchEvent>(capacity = 1000)

    init {
        startProcessingWorker()
    }

    /**
     * Enqueues incoming touch telemetry stream events asynchronously.
     */
    fun logTouchStream(
        screenId: String,
        timestamps: LongArray,
        xCoords: FloatArray,
        yCoords: FloatArray,
        params: HashMap<String, String> // Added metadata map
    ) {
        val event = RawTouchEvent(
            screenId = screenId,
            wallClockTime = System.currentTimeMillis(),
            timestamps = timestamps,
            xCoords = xCoords,
            yCoords = yCoords,
            params = params
        )
        // thread-safe lock-free offer mechanism via channel
        eventChannel.trySend(event)
    }

    private fun startProcessingWorker() {
        engineScope.launch {
            eventChannel.consumeAsFlow().collect { rawEvent ->
                // 1. Offload complex geometry serialization handling to the native layer
                val metadataString = rawEvent.params.entries.joinToString("&") { "${it.key}=${it.value}" }

                val compressedPayload = spatialBridge.serializeBatchPayload(
                    rawEvent.xCoords,
                    rawEvent.yCoords,
                    rawEvent.timestamps,
                    metadataString
                )

                // 2. Persist the compressed blob straight down to localized Room structures
                if (compressedPayload.isNotEmpty()) {
                    storageRepository.insertPayload(
                        screenId = rawEvent.screenId,
                        timestamp = rawEvent.wallClockTime,
                        payload = compressedPayload,
                        params = metadataString
                    )
                }
            }
        }
    }

    /**
     * Teardown hook to cleanly release coroutine workers and native pipelines
     */
    fun shutdown() {
        eventChannel.close()
        engineScope.cancel() // Halts background collection workers safely
        spatialBridge.releaseNativePipeline() // Unregisters consumers inside C++ memory space
    }
}