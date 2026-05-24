package com.natighajiyev.analytics_core.config.exceptionDetector

import android.util.Log
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK
import java.io.PrintWriter
import java.io.StringWriter

internal class AlphaCrashTracer(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "AlphaMetrics_Crash"
        private const val ACTION = "action"
        private const val EXCEPTION_TYPE = "exception_type"
        private const val MESSAGE = "message"
        private const val THREAD_NAME = "thread_name"
        private const val APP_CRASH = "app_crash"
        private const val SCREEN_ID = "SystemCrashHandler"

        // Final metadata key allocations for the raw stack trace payload distribution
        private const val ERROR_ORIGIN = "error_origin"
        private const val TRACE_CHUNK_PREFIX = "trace_chunk_"

        private const val PACKAGE_NAME_ANDROID = "android."
        private const val PACKAGE_NAME_COM_ANDROID = "com.android."
        private const val PACKAGE_NAME_JAVA = "java."
        private const val PACKAGE_NAME_KOTLIN = "kotlin."
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Extract the full stack trace payload cleanly into a raw String block
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val stackTraceString = stringWriter.toString()

            // Locate the root cause origin frame within the active trace matrix
            val rootCauseElement = throwable.stackTrace.firstOrNull { element ->
                !element.className.startsWith(PACKAGE_NAME_ANDROID) &&
                        !element.className.startsWith(PACKAGE_NAME_COM_ANDROID) &&
                        !element.className.startsWith(PACKAGE_NAME_JAVA) &&
                        !element.className.startsWith(PACKAGE_NAME_KOTLIN)
            } ?: throwable.stackTrace.firstOrNull()

            val errorOrigin = rootCauseElement?.let {
                "${it.className.substringAfterLast(".")}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            } ?: "UnknownSource"

            // Extract core exception details
            val exceptionName = throwable.javaClass.simpleName ?: "UnknownException"
            val crashMessage = throwable.localizedMessage ?: "No message provided"

            // Build the core metadata configuration mapping map
            val crashMetadata = hashMapOf<String, String>()

            crashMetadata[ACTION] = APP_CRASH
            crashMetadata[EXCEPTION_TYPE] = exceptionName
            crashMetadata[MESSAGE] = crashMessage.take(60) // Safe clamping parameter matching native limits
            crashMetadata[THREAD_NAME] = thread.name.take(30)
            crashMetadata[ERROR_ORIGIN] = errorOrigin.take(60)

            // CHUNK PROCESSING STAGE: Process the entire 'stackTraceString' sequentially
            // We strip newlines/tabs to save bytes, then chunk it to fit the JNI struct boundaries perfectly
            val cleanTraceData = stackTraceString.replace("\n", " ").replace("\t", " ")
            val maxChunkLength = 60 // Keeps strings within safe native limits

            // Your C++ struct supports up to 5 pairs total. We have used 5 keys above,
            // but we can slice out 2 explicit trace chunks into remaining payload arrays safely
            var sliceIndex = 0
            var chunkCounter = 1

            while (sliceIndex < cleanTraceData.length && chunkCounter <= 2) {
                val endSelection = minOf(sliceIndex + maxChunkLength, cleanTraceData.length)
                val dynamicChunkText = cleanTraceData.substring(sliceIndex, endSelection)

                // Creates keys dynamically: "trace_chunk_1", "trace_chunk_2"
                crashMetadata["$TRACE_CHUNK_PREFIX$chunkCounter"] = dynamicChunkText

                sliceIndex += maxChunkLength
                chunkCounter++
            }

            Log.e(TAG, "CRITICAL: Uncaught exception intercepted. Recording stack trace data segments cleanly to mmap memory.")

            // Force write this straight into the C++ binary file block synchronously via the runtime engine
            AlphaMetricsSDK.trackScreenEvent(
                screenId = SCREEN_ID,
                x = -1.0,
                y = -1.0,
                customParams = crashMetadata
            )
        } catch (e: Exception) {
            // Defend loop from crashing inside the crash tracking routine
            Log.e(TAG, "Failed recording crash diagnostics matrix to binary ring buffer.", e)
        } finally {
            // Return structural execution context flow back to Android UI layer to finish app termination natively
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}