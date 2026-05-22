package com.natighajiyev.analytics_core.service


import android.app.IntentService
import android.content.Intent
import android.util.Log
import com.natighajiyev.analytics_core.bridge.NativeAnalyticsGateway
import com.natighajiyev.analytics_core.service.network.HttpAnalyticsDispatcher
import kotlinx.coroutines.runBlocking
import java.io.File

class BackgroundUploadService : IntentService("BackgroundUploadService") {
    companion object {
        private const val TAG = "AlphaMetrics_Egress"
    }

    override fun onHandleIntent(intent: Intent?) {
        Log.d(TAG, "App went to background! Egress process woke up to dump analytics batch.")

        // 1. Resolve the absolute matching file path layout
        val baseDataDir = applicationInfo.dataDir
        val targetPath = File(baseDataDir, "alpha_metrics_core.bin").absolutePath

        // 2. Attach to the shared memory file
        if (!NativeAnalyticsGateway.nativeStartEngine(targetPath)) {
            Log.e(TAG, "Failed to mount shared mmap file from background process container.")
            return
        }

        val dispatcher = HttpAnalyticsDispatcher()
        var pendingCount = NativeAnalyticsGateway.nativeGetPendingCount()
        Log.d(TAG, "Found $pendingCount events stored in binary cache queue.")

        // 3. Drain the queue completely in a blocking batch scope
        runBlocking {
            while (pendingCount > 0) {
                val eventData = NativeAnalyticsGateway.nativePollEvent()
                if (eventData == null) {
                    NativeAnalyticsGateway.nativePopEvent()
                    break
                }

                Log.d(TAG, "Syncing event for screen: ${eventData["screenId"]} to server...")
                val success = dispatcher.dispatchEvent(eventData)

                if (success) {
                    NativeAnalyticsGateway.nativePopEvent() // Drop the item from binary storage
                    pendingCount--
                } else {
                    Log.e(TAG, "Server ingestion failed. Retention rules applied. Retrying on next app stop.")
                    break // Stop processing to save data payload indexes
                }
            }
        }

        // 4. Detach from memory map and let the OS kill this process
        NativeAnalyticsGateway.nativeStopEngine()
        Log.d(TAG, "Batch transmission complete. Egress service process going to sleep safely.")
    }
}