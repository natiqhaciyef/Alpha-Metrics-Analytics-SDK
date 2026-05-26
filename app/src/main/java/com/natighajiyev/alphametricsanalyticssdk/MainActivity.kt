package com.natighajiyev.alphametricsanalyticssdk

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.WindowCallbackWrapper
import com.natighajiyev.alphametricsanalyticssdk.databinding.ActivityMainBinding
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK
import com.natighajiyev.analytics_core.engine.AnalyticsEngineController
import com.natighajiyev.analytics_core.util.WindowMetricsHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var analyticsEngineController: AnalyticsEngineController

    private lateinit var binding: ActivityMainBinding
    private var isTestFreezeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.anrButton.setOnClickListener {
            if (isTestFreezeActive) {
                Log.d("AlphaMetrics_Test", "Ignored backlogged touch queue event entry.")
                return@setOnClickListener
            }

            isTestFreezeActive = true
            Log.d(
                "AlphaMetrics_Test",
                "Deliberately freezing Main UI Thread for ANR verification..."
            )

            try {
                Thread.sleep(6000)
            } finally {
                Log.d("AlphaMetrics_Test", "Thread released.")
                isTestFreezeActive = false
            }
        }

        binding.crashButton.setOnClickListener {
            Log.d("AlphaMetrics_Test", "Deliberately forcing a fatal application crash...")
            throw RuntimeException("AlphaMetrics Manual Crash Verification Exception")
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val screenId = this.localClassName
            val tapX = ev.x.toDouble()
            val tapY = ev.y.toDouble()

            val contextMetadata = hashMapOf(
                "action" to "screen_down",
                "pointer_count" to ev.pointerCount.toString()
            )

            val dimensions = WindowMetricsHelper.getMetrics(this@MainActivity)
            analyticsEngineController.logTouchStream(
                screenId = screenId,
                x = tapX,
                y = tapY,
                screenWidth = dimensions.width,
                screenHeight = dimensions.height,
                orientation = dimensions.orientation,
                metadata = contextMetadata
            )
        }
        return super.dispatchTouchEvent(ev)
    }
}