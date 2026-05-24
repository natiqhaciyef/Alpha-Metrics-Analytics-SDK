# AlphaMetrics Core SDK

**AlphaMetrics Core** is a high-performance, ultra-low overhead telemetry tracking engine designed for Android applications. It captures touch interactions, device context, and system stability metrics with near-zero UI main-thread impact.

By leveraging a low-level Linux kernel system architecture, AlphaMetrics guarantees zero telemetry loss during sudden process crashes or severe system hangs, outperforming standard SQLite or Room-based logging implementations.

---

## Advanced Architectural Design

Unlike standard analytics SDKs that store metrics in JVM memory pools or block the Main Thread with disk IO operations, AlphaMetrics splits execution across a **high-speed native writer** and a **completely isolated background egress process**.

### ⚡ The Linux `mmap` Memory Layer

AlphaMetrics maps a binary cache file (`alpha_metrics_core.bin`) straight into the virtual address space of the application via the Linux **`mmap`** system call.

* **Zero-Copy Serialization:** Writing touch parameters takes nanoseconds. Telemetry data is written straight to raw memory bytes without passing through heavy JSON serializers or intermediate Java buffers.
* **Kernel-Backed Durability:** Because the cache memory is managed directly by the OS Kernel, any event captured right up to the exact millisecond of an app freeze or unhandled fatal crash is preserved. The OS kernel flushes the pending bytes to disk safely even if the app process is terminated.

### 🔄 Dual-Process Isolation Pipeline

To insulate the host application from network fluctuations and battery drain:

1. **Main UI Process:** Logs coordinates natively to the `mmap` allocation slot without network code.
2. **`:alphametrics_egress_v1` Process:** When the app goes to the background, an isolated process wakes up, takes the file lock, chunks telemetry data arrays, and transmits payloads asynchronously to your backend servers.

---

## Features

* **Near-Zero Main-Thread Overhead:** Telemetry writes avoid the JVM heap entirely, ensuring your app maintains a smooth 60/120 FPS.
* **Crash-Resilient Session Timelines:** Captures unhandled JVM exceptions and automatically logs contextual metadata alongside the exact interactions leading up to the failure.
* **Proactive ANR Detection Watchdog:** Runs an independent ticking thread that detects Main Thread freezes ($>5\text{s}$) and drops an explicit `"app_anr"` event into the kernel layer before process death.
* **Adaptive Buffer-Full Strategies:** Let consumers pick how to manage storage boundaries when offline (`DROP_NEWEST` or `PURGE_OLDEST`) using strict fixed binary struct bounds.
* **Cleartext Restriction Enforcement:** Evaluates URL patterns against security guidelines, instantly blocking unencrypted transmission attempts over raw `http://`.

---

## Installation & Integration

### 1. Initialize the Tracking Engine

Initialize the SDK within your subclassed `Application` node. This automatically hooks lifecycle callbacks and attaches thread-monitoring handlers.

```kotlin
package com.yourcompany.app

import android.app.Application
import com.natighajiyev.analytics_core.bridge.AlphaMetricsConfig
import com.natighajiyev.analytics_core.bridge.AlphaMetricsSDK

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure the granular parameters tree
        val metricsConfig = AlphaMetricsConfig.Builder()
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

        // Ignite the tracking engine
        AlphaMetricsSDK.initialize(this, metricsConfig)
    }
}

```

### 2. Stream Interaction Events

Invoke `trackScreenEvent` globally. The SDK automatically clips parameters exceeding structural thresholds to keep binary serialization uniform.

```kotlin
override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    if (ev.action == MotionEvent.ACTION_DOWN) {
        AlphaMetricsSDK.trackScreenEvent(
            screenId = this.javaClass.simpleName,
            x = ev.x.toDouble(),
            y = ev.y.toDouble(),
            customParams = mapOf(
                "action" to "screen_tap",
                "ui_element" to "submit_payment_button"
            )
        )
    }
    return super.dispatchTouchEvent(ev)
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

## esting Diagnostic Pipelines

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

---

## License

This project is licensed under the MIT License - see the [LICENSE](https://www.google.com/search?q=LICENSE) file for complete details.

---
