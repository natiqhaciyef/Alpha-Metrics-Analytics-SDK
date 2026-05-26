package com.natighajiyev.analytics_core.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.natighajiyev.analytics_core.network.usecase.EgressPipelineUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Isolated background execution container handling automated telemetry data egress pipelines.
 *
 * This service runs completely separated from the application's interactive UI process
 * (typically within the `:alphametrics_egress_v1` process boundary). It unbundles incoming configuration
 * properties passed via IPC intent tokens, initializes the native storage file linkage, extracts chunked
 * event batches sequentially, and handles network transport retry loops and fallback server routing.
 */
class BackgroundUploadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val egressPipelineUseCase = EgressPipelineUseCase.Companion.create()

    companion object {
        private const val TAG = "AlphaMetrics_Service"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentBundle = intent ?: return START_NOT_STICKY
        serviceScope.launch {
            try {
                egressPipelineUseCase.invoke(intentBundle, applicationInfo)
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

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}