package com.natighajiyev.analytics_core.engine

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.natighajiyev.analytics_core.bridge.NativeAnalyticsGateway
import com.natighajiyev.analytics_core.service.BackgroundUploadService
import java.io.File

object AlphaMetricsSDK : Application.ActivityLifecycleCallbacks {
    private const val TAG = "AlphaMetricsSDK"
    private var isInitialized = false
    private var runningActivitiesCount = 0

    fun initialize(application: Application) {
        if (isInitialized) return

        val baseDataDir = application.applicationInfo.dataDir
        val targetPath = File(baseDataDir, "alpha_metrics_core.bin").absolutePath

        // Initialize the native mmap memory layer for the Main UI process
        if (NativeAnalyticsGateway.nativeStartEngine(targetPath)) {
            isInitialized = true
            application.registerActivityLifecycleCallbacks(this)
            Log.d(TAG, "Main UI Process memory map engine successfully initialized.")
        }
    }

    fun trackScreenEvent(screenId: String, x: Double, y: Double, customParams: Map<String, String>) {
        if (!isInitialized) return
        val boundParams = customParams.entries.take(5)
        val keys = boundParams.map { it.key }.toTypedArray()
        val values = boundParams.map { it.value }.toTypedArray()

        NativeAnalyticsGateway.nativeLogEventWithMetadata(
            screenId, System.currentTimeMillis(), x, y, keys, values, boundParams.size
        )
    }

    fun tearDown() {
        NativeAnalyticsGateway.nativeStopEngine()
    }

    // --- ACTIVITY LIFECYCLE CALLBACK TRACERS ---
    override fun onActivityStarted(activity: Activity) {
        runningActivitiesCount++
    }

    override fun onActivityStopped(activity: Activity) {
        runningActivitiesCount--

        // CRITICAL CHECK: If runningActivitiesCount reaches 0, the user has exited the UI completely!
        if (runningActivitiesCount == 0) {
            Log.d(TAG, "UI interaction stopped entirely. Triggering background isolated single-shot batch dump...")

            val intent = Intent(activity, BackgroundUploadService::class.java)
            activity.startService(intent)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}