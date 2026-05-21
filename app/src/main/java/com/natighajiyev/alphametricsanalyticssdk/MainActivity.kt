package com.natighajiyev.alphametricsanalyticssdk

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.natighajiyev.analytics_core.engine.AnalyticsEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var analyticsEngine: AnalyticsEngine
    private val xCoords = mutableListOf<Float>()
    private val yCoords = mutableListOf<Float>()
    private val timestamps = mutableListOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * Intercepts ALL raw hardware window interactions globally before they hit layout targets.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                clearTelemetryBuffers()
                recordTrackingPoint(event)
            }
            MotionEvent.ACTION_MOVE -> {
                recordTrackingPoint(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                recordTrackingPoint(event)

                if (xCoords.isNotEmpty()) {
                    analyticsEngine.logTouchStream(
                        screenId = this::class.java.simpleName,
                        timestamps = timestamps.toLongArray(),
                        xCoords = xCoords.toFloatArray(),
                        yCoords = yCoords.toFloatArray(),
                        params = hashMapOf(
                            "X coordinate" to xCoords.last().toString(),
                            "Y coordinate" to yCoords.last().toString(),
                        )
                    )
                }
                clearTelemetryBuffers()
            }
        }

        return super.dispatchTouchEvent(event)
    }

    private fun recordTrackingPoint(event: MotionEvent) {
        xCoords.add(event.x)
        yCoords.add(event.y)
        timestamps.add(event.eventTime)
    }

    private fun clearTelemetryBuffers() {
        xCoords.clear()
        yCoords.clear()
        timestamps.clear()
    }
}