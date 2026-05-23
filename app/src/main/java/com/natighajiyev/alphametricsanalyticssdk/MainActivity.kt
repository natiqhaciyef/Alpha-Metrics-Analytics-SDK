package com.natighajiyev.alphametricsanalyticssdk

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.natighajiyev.analytics_core.engine.AnalyticsEngineController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var analyticsEngineController: AnalyticsEngineController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // No manual buffer clearing needed anymore!
        // The engine clears the queue once the batch-upload is successful.
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

            // Pass this to your injected controller
            analyticsEngineController.logTouchStream(screenId, tapX, tapY, contextMetadata)
        }
        return super.dispatchTouchEvent(ev)
    }

    // You no longer need recordTrackingPoint() or clearTelemetryBuffers() here.
    // The C++ layer is the "source of truth" now.
}