package com.natighajiyev.analytics_core.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi


/**
 * Utility helper to extract window bounds and viewport orientation parameters safely across all OS versions.
 */
object WindowMetricsHelper {

    data class ScreenDimensions(
        val width: Int,
        val height: Int,
        val orientation: String
    )

    /**
     * Pulls the exact display window pixel boundaries and active rotation state layout.
     */
    fun getMetrics(context: Context): ScreenDimensions {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val width: Int
        val height: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            val display = @Suppress("DEPRECATION") windowManager.defaultDisplay
            val point = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            width = point.x
            height = point.y
        }

        val orientation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }

        return ScreenDimensions(width, height, orientation)
    }
}