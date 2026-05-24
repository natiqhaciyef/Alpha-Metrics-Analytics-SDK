package com.natighajiyev.analytics_core.config.exceptionDetector

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK

internal class AlphaAnrWatchdog(
    private val timeoutMs: Long = 5000
) : Thread(TAG_THREAD) {

    companion object {
        private const val TAG_THREAD = "AlphaAnrWatchdogThread"
        private const val TAG = "AlphaMetrics_ANR"
        private const val ACTION = "action"
        private const val APP_ANR = "android_not_respond"
        private const val REASON = "reason"
        private const val FROZEN_STATUS = "frozen_status"
        private const val SCREEN_ID = "SystemANRWatchdog"
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private var isRunning = true

    @Volatile
    private var tickCounter = 0

    override fun run() {
        if (AlphaMetricsSDK.currentConfig?.isLoggingEnabled == true) {
            Log.d(TAG, "ANR Watchdog thread background monitor loop activated.")
        }

        while (isRunning) {
            val executionCheckToken = tickCounter
            
            // Post a simple increment transaction task to the Main UI Thread queue
            uiHandler.post {
                tickCounter++
            }

            try {
                // Sleep for the designated threshold duration limit (e.g., 5 seconds)
                sleep(timeoutMs)
            } catch (e: InterruptedException) {
                break
            }

            // If the tickCounter did NOT increase, the Main Thread is frozen and couldn't run our post block!
            if (tickCounter == executionCheckToken) {
                handleAnrDetected()
            }
        }
    }

    private fun handleAnrDetected() {
        Log.e(TAG, "CRITICAL: Application Not Responding (ANR) detected! Main thread is frozen.")

        val anrMetadata = hashMapOf(
            ACTION to APP_ANR,
            REASON to "Main UI thread blocked for more than ${timeoutMs}ms",
            FROZEN_STATUS to "true"
        )

        // Force write the ANR data down to your high-performance mmap binary file instantly!
        AlphaMetricsSDK.trackScreenEvent(
            screenId = SCREEN_ID,
            x = -2.0, // Special coordinate tracking marker indicating an ANR block
            y = -2.0,
            customParams = anrMetadata
        )

        // Stop our own loop to prevent logging duplicate entries for the same freeze session burst
        isRunning = false 
    }

    fun shutdown() {
        isRunning = false
        interrupt()
    }
}