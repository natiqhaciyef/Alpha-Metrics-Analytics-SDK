package com.natighajiyev.alphametricsanalyticssdk

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val screenId = this.localClassName
            val tapX = ev.x.toDouble()
            val tapY = ev.y.toDouble()

            val contextMetadata = hashMapOf(
                "action" to "screen_down",
                "pointer_count" to ev.pointerCount.toString(),
                "address of X" to ev.x.toString(),
                "address of Y" to ev.y.toString()
            )

            // Pass this to your injected controller
            analyticsEngineController.logTouchStream(screenId, tapX, tapY, contextMetadata)
        }
        return super.dispatchTouchEvent(ev)
    }
}