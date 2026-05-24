package com.natighajiyev.analytics_core.config.exceptionDetector

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK

/**
 * Independent safety watchdog thread dedicated to detecting Application Not Responding (ANR) hangs.
 *
 * It operates by pushing routine tick transaction tokens to the Main UI Thread's MessageQueue.
 * If the Main Thread fails to clear the token within the designated [timeoutMs] window,
 * the watchdog intercepts the freeze and writes an ANR footprint event straight to disk.
 */
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

    /**
     * Continuous background polling loop that monitors main thread responsiveness
     * across explicit timing check windows.
     */
    override fun run() {
        if (AlphaMetricsSDK.currentConfig?.isLoggingEnabled == true) {
            Log.d(TAG, "ANR Watchdog thread background monitor loop activated.")
        }

        while (isRunning) {
            val executionCheckToken = tickCounter

            // Post an execution check token directly to the UI thread
            uiHandler.post {
                tickCounter++
            }

            try {
                sleep(timeoutMs)
            } catch (e: InterruptedException) {
                break
            }

            // If the counter hasn't changed, the main thread loop is blocked/frozen
            if (tickCounter == executionCheckToken) {
                handleAnrDetected()
            }
        }
    }

    /**
     * Packages system status metadata metrics and flushes an "android_not_respond"
     * trace down into the C++ mmap allocation region before the application terminates.
     */
    private fun handleAnrDetected() {
        Log.e(TAG, "CRITICAL: Application Not Responding (ANR) detected! Main thread is frozen.")

        val anrMetadata = hashMapOf(
            ACTION to APP_ANR,
            REASON to "Main UI thread blocked for more than ${timeoutMs}ms",
            FROZEN_STATUS to "true"
        )

        AlphaMetricsSDK.trackScreenEvent(
            screenId = SCREEN_ID,
            x = -2.0,
            y = -2.0,
            customParams = anrMetadata
        )

        isRunning = false
    }

    /**
     * Halts polling executions and safely interrupts the background looping state
     * to prevent memory leakage during SDK lifecycle teardowns.
     */
    fun shutdown() {
        isRunning = false
        interrupt()
    }
}