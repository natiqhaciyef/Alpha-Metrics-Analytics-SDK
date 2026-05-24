# AlphaMetrics Core SDK

**AlphaMetrics Core** is a high-performance, ultra-low overhead telemetry tracking engine designed for Android applications. It captures touch interactions, device context, and system stability metrics with near-zero UI main-thread impact.

By leveraging a low-level Linux kernel system architecture, AlphaMetrics guarantees zero telemetry loss during sudden process crashes or severe system hangs, outperforming standard SQLite or Room-based logging implementations.

---

## Advanced Architectural Design

Unlike standard analytics SDKs that store metrics in JVM memory pools or block the Main Thread with disk IO operations, AlphaMetrics splits execution across a **high-speed native writer** and a **completely isolated background egress process**.

### The Linux `mmap` Memory Layer

AlphaMetrics maps a binary cache file (`alpha_metrics_core.bin`) straight into the virtual address space of the application via the Linux **`mmap`** system call.

* **Zero-Copy Serialization:** Writing touch parameters takes nanoseconds. Telemetry data is written straight to raw memory bytes without passing through heavy JSON serializers or intermediate Java buffers.
* **Kernel-Backed Durability:** Because the cache memory is managed directly by the OS Kernel, any event captured right up to the exact millisecond of an app freeze or unhandled fatal crash is preserved. The OS kernel flushes the pending bytes to disk safely even if the app process is terminated.

### Dual-Process Isolation Pipeline

To insulate the host application from network fluctuations and battery drain:

1. **Main UI Process:** Logs coordinates natively to the `mmap` allocation slot without network code.
2. **`:alphametrics_egress_v1` Process:** When the app goes to the background, an isolated process wakes up, takes the file lock, chunks telemetry data arrays, and transmits payloads asynchronously to your backend servers.

---

## Features

* **Near-Zero Main-Thread Overhead:** Telemetry writes avoid the JVM heap entirely, ensuring your app maintains a smooth 60/120 FPS.
* **Controlled Access Layer via Dependency Injection:** Exposes a unified singleton architecture wrapper ensuring no UI component can accidentally breach or teardown native link mappings.
* **Crash-Resilient Session Timelines:** Captures unhandled JVM exceptions and automatically logs contextual metadata alongside the exact interactions leading up to the failure.
* **Proactive ANR Detection Watchdog:** Runs an independent ticking thread that detects Main Thread freezes ($>5\text{s}$) and drops an explicit `"app_anr"` event into the kernel layer before process death.
* **Adaptive Buffer-Full Strategies:** Let consumers pick how to manage storage boundaries when offline (`DROP_NEWEST` or `PURGE_OLDEST`) using strict fixed binary struct bounds.
* **Cleartext Restriction Enforcement:** Evaluates URL patterns against security guidelines, instantly blocking unencrypted transmission attempts over raw `http://`.

---

## Installation & Integration

### 1. Configure the Target Modules Graph (Hilt Example)

Define your SDK initialization boundaries within your application's Dependency Injection graph. This decouples setup variables cleanly from explicit class implementations.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideAlphaMetricsConfig(): AlphaMetricsConfig {
        return AlphaMetricsConfig.Builder()
            .setLoggingEnabled(true)
            .setCrashTrappingEnabled(true)
            .network {
                serverEndpoint = "https://analytics.yourdomain.com/v1/telemetry"
                backupEndpoint = "https://backup-logs.yourdomain.com/v1/telemetry"
                connectTimeoutMs = 10000
                readTimeoutMs = 15000
                customHeaders = mapOf("X-App-Variant" to "Production")
            }
            .batch {
                maxBatchSize = 50
                minBatchSizeTrigger = 10
                retryAttemptLimit = 3
                backoffDelayMs = 2000
                maxEventsPerBackgroundSession = 500
            }
            .storage {
                maxQueueCapacity = 2000
                strategyOnBufferFull = StorageConfig.FullStrategy.PURGE_OLDEST
            }
            .security {
                useEncryption = true
                allowCleartextTraffic = false
            }
            .build()
    }
}

```

### 2. Wrap via the Business Control Layer

Utilize the **`AnalyticsEngineController`** pattern to gate access to raw runtime commands. This prevents standard layout fragments or developers from executing unauthorized state changes.

```kotlin
package com.natighajiyev.analytics_core.bridge

import android.app.Application
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsEngineController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AlphaMetricsConfig
) {
    init {
        // Resolve application context cleanly
        val application = context.applicationContext as Application

        // Initialize the SDK with the extended network and batch configuration matrices
        AlphaMetricsSDK.initialize(application, config)

        if (config.isLoggingEnabled) {
            Log.d("AlphaMetrics_Debug", "AnalyticsEngineController linked to native engine lifecycle hooks.")
        }
    }

    fun logTouchStream(screenId: String, x: Double, y: Double, metadata: Map<String, String>) {
        AlphaMetricsSDK.trackScreenEvent(screenId, x, y, metadata)
        
        // Debug check: How many events are currently sitting in the file?
        val count = NativeAnalyticsGateway.nativeGetPendingCount()
        if (config.isLoggingEnabled) {
            Log.d("AlphaMetrics_Debug", "Current events in binary queue - $count: \n{screenId: $screenId, x: $x, y: $y, metadata: $metadata}")
        }
    }

    fun shutdown() {
        AlphaMetricsSDK.tearDown()
    }
}

```

### 3. Stream Interaction Events from UI Contexts

Inject the controller instance directly into your presentation targets. The controller automatically shields your view implementations from low-level serialization checks.

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject 
    lateinit var analyticsController: AnalyticsEngineController

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            analyticsController.logTouchStream(
                screenId = this.javaClass.simpleName,
                x = ev.x.toDouble(),
                y = ev.y.toDouble(),
                metadata = mapOf(
                    "action" to "screen_tap",
                    "ui_element" to "submit_payment_button"
                )
            )
        }
        return super.dispatchTouchEvent(ev)
    }
}

```

---

## Payload Data Schema

When payloads are transferred to your backend by the `:alphametrics_egress_v1` process, they arrive structured into clean JSON arrays.

### Standard Interaction Record

```json
{
  "eventName": "screen_touch_event",
  "screenId": "DashboardActivity",
  "timestamp": 1779669140012,
  "coordinateX": 540.5,
  "coordinateY": 1120.2,
  "metadata": {
    "action": "screen_tap",
    "ui_element": "submit_payment_button"
  }
}

```

### Intercepted System Crash Record (With Trace Segmentation)

```json
{
  "eventName": "screen_touch_event",
  "screenId": "SystemCrashHandler",
  "timestamp": 1779669145220,
  "coordinateX": -1.0,
  "coordinateY": -1.0,
  "metadata": {
    "action": "app_crash",
    "exception_type": "NullPointerException",
    "message": "Attempt to invoke virtual method on a null object reference",
    "error_origin": "DashboardActivity.processPayment(DashboardActivity.kt:42)",
    "trace_chunk_1": "at com.yourcompany.app.DashboardActivity.processPayment",
    "trace_chunk_2": "(DashboardActivity.kt:42) at android.view.View.performClick"
  }
}

```

---

## Testing Diagnostic Pipelines

### Forcing a Crash Trapping Verification

To verify that your C++ allocation routines are capturing error frames safely, trigger a terminal exception:

```kotlin
throw RuntimeException("AlphaMetrics Manual Crash Verification Exception")

```

*Observe that `AlphaMetrics_Crash` confirms recording to memory right before the `AndroidRuntime` fatal stack trace terminates the process.*

### Simulating an ANR Freeze

To test the background watchdog, intentionally block the thread loop:

```kotlin
// Lock the UI loop to exceed the default 5-second system deadline limit
Thread.sleep(6000)

```

*While the UI hangs, immediately tap the screen repeatedly to generate pending inputs. The watchdog thread will flag the failure, inject an ANR descriptor map directly into the `mmap` cache, and yield smoothly to the default OS system response handlers.*

---

## Developer Guide & Security

* **GDPR Compliance:** Ensure coordinate capture points avoid user-password fields or sensitive entry components. Use localized scaling strategies if relative heatmapping isn't strictly required.
* **Process Boundaries:** Because transmission happens in a standalone service container, avoid storing transient variables inside volatile Kotlin singleton instances intended for background upload use. Rely completely on the configuration intent tokens.
* **Lifecycle Governance:** Avoid invoking `controller.shutdown()` inside typical UI destruction states. Call it exclusively during hard logout operations or integration test cleanup configurations.

---
