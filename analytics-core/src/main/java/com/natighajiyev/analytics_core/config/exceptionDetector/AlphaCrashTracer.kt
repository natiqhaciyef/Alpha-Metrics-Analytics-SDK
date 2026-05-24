package com.natighajiyev.analytics_core.config.exceptionDetector

import android.util.Log
import com.natighajiyev.analytics_core.engine.AlphaMetricsSDK
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Custom uncaught exception interceptor chained into the JVM thread failure pipeline.
 *
 * This tracer catches fatal uncaught exceptions before application termination. It isolates
 * the root cause origin frame, extracts core diagnostic descriptors, segments the stack trace,
 * and writes the payload directly into native `mmap` persistent cache before yielding control
 * back to Android's default system crash handler.
 */
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

        private const val ERROR_ORIGIN = "error_origin"
        private const val TRACE_CHUNK_PREFIX = "trace_chunk_"

        private const val PACKAGE_NAME_ANDROID = "android."
        private const val PACKAGE_NAME_COM_ANDROID = "com.android."
        private const val PACKAGE_NAME_JAVA = "java."
        private const val PACKAGE_NAME_KOTLIN = "kotlin."
    }

    /**
     * Intercepts terminal exceptions thrown by any active thread. Parses, sanitizes, and records
     * metadata properties securely before app process death.
     */
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val stackTraceString = stringWriter.toString()

            val rootCauseElement = throwable.stackTrace.firstOrNull { element ->
                !element.className.startsWith(PACKAGE_NAME_ANDROID) &&
                        !element.className.startsWith(PACKAGE_NAME_COM_ANDROID) &&
                        !element.className.startsWith(PACKAGE_NAME_JAVA) &&
                        !element.className.startsWith(PACKAGE_NAME_KOTLIN)
            } ?: throwable.stackTrace.firstOrNull()

            val errorOrigin = rootCauseElement?.let {
                "${it.className.substringAfterLast(".")}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            } ?: "UnknownSource"

            val exceptionName = throwable.javaClass.simpleName ?: "UnknownException"
            val crashMessage = throwable.localizedMessage ?: "No message provided"
            val crashMetadata = hashMapOf<String, String>()

            crashMetadata[ACTION] = APP_CRASH
            crashMetadata[EXCEPTION_TYPE] = exceptionName
            crashMetadata[MESSAGE] = crashMessage.take(60)
            crashMetadata[THREAD_NAME] = thread.name.take(30)
            crashMetadata[ERROR_ORIGIN] = errorOrigin.take(60)

            val cleanTraceData = stackTraceString.replace("\n", " ").replace("\t", " ")
            val maxChunkLength = 60

            var sliceIndex = 0
            var chunkCounter = 1

            while (sliceIndex < cleanTraceData.length && chunkCounter <= 2) {
                val endSelection = minOf(sliceIndex + maxChunkLength, cleanTraceData.length)
                val dynamicChunkText = cleanTraceData.substring(sliceIndex, endSelection)

                crashMetadata["$TRACE_CHUNK_PREFIX$chunkCounter"] = dynamicChunkText

                sliceIndex += maxChunkLength
                chunkCounter++
            }

            Log.e(TAG, "CRITICAL: Uncaught exception intercepted. Recording stack trace data segments cleanly to mmap memory.")

            AlphaMetricsSDK.trackScreenEvent(
                screenId = SCREEN_ID,
                x = -1.0,
                y = -1.0,
                customParams = crashMetadata
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed recording crash diagnostics matrix to binary ring buffer.", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}