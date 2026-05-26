package com.natighajiyev.analytics_core.network.dispatchers

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.HashMap

/**
 * Contract defining the network serialization engine transport layer.
 *
 * This abstraction isolates raw telemetry uploading protocols from internal pipeline mechanics,
 * allowing alternative network clients (e.g., standard HTTP connections, gRPC pipelines, or Mock testing implementations)
 * to be plugged into the background worker module seamlessly.
 */
internal interface AnalyticsDispatcher {

    /**
     * Synchronizes a single standalone telemetry record packet directly with the remote metrics collection server.
     *
     * @param eventData A structural map holding event identifiers, coordinates, and contextual parameters.
     * @return `true` if the server successfully acknowledges payload ingestion.
     */
    suspend fun dispatchEvent(eventData: HashMap<String, Any>): Boolean

    /**
     * Aggregates and uploads multiple cached metrics records simultaneously within a single outbound transaction.
     *
     * This operation matches the maxBatchSize configuration restrictions to reduce system network connection
     * handshakes and optimize mobile battery endurance across background cycles.
     *
     * @param batchList A list collection containing telemetry parameter maps pulled out of the native storage layer.
     * @return `true` if the server successfully registers the entire batch payload.
     */
    suspend fun dispatchBatchEvent(batchList: List<HashMap<String, Any>>): Boolean
}


/**
 * Network synchronization engine responsible for serializing and uploading telemetry batches.
 *
 * This dispatcher runs completely decoupled from the main process UI loop. It reads multi-event
 * packets compiled by the background sync service, maps individual key components into standard
 * JSON structures, and pushes them across the network.
 *
 * It features a dual-pipeline engine: if an explicit [okHttpClient] is supplied, it processes traffic
 * through OkHttp (enforcing advanced constraints like SSL Certificate Pinning). Otherwise, it gracefully
 * falls back to a lightweight, standard native [HttpURLConnection] pipeline.
 *
 * @property okHttpClient An optional, pre-configured HTTP engine used to enforce certificate pinning or custom security policies.
 * @property endpointUrl The destination REST API endpoint URL where metrics are posted.
 * @property connectionTimeoutMs Socket connect timeout boundary constraint in milliseconds (fallback pipeline engine only).
 * @property readTimeoutMs Socket data stream read timeout boundary constraint in milliseconds (fallback pipeline engine only).
 * @property headerMap Authentication token properties and user custom headers mapped across process limits.
 */
internal class HttpAnalyticsDispatcher(
    private val okHttpClient: OkHttpClient?,
    private val endpointUrl: String,
    private val connectionTimeoutMs: Int,
    private val readTimeoutMs: Int,
    private val headerMap: Map<String, String> = emptyMap()
) : AnalyticsDispatcher {

    companion object {
        private const val EVENT_NAME = "eventName"
        private const val SCREEN_TOUCH_EVENT = "screen_touch_event"
        private const val SCREEN_ID = "screenId"
        private const val TIMESTAMP = "timestamp"
        private const val TS = "ts"
        private const val X = "x"
        private const val Y = "y"
        private const val COORDINATE_X = "coordinateX"
        private const val COORDINATE_Y = "coordinateY"
        private const val PARAMS = "params"
        private const val METADATA = "metadata"
        private const val UTF = "UTF-8"
        private const val CONTENT_TYPE = "Content-Type"
        private const val ACCEPT = "Accept"
        private const val APPLICATION_JSON = "application/json"
        private const val CHARSET = "charset"
        private const val POST_METHOD = "POST"
    }

    /**
     * Legacy single-event sender (kept for internal testing compatibility).
     * Automatically wraps the isolate record inside a single-element list envelope.
     */
    override suspend fun dispatchEvent(eventData: HashMap<String, Any>): Boolean {
        return dispatchBatchEvent(listOf(eventData))
    }

    /**
     * Packs multiple metrics collections into a single structured HTTP JSON Array request.
     * Maps perfectly to the maxBatchSize requirements of the service.
     *
     * @param batchList An aggregated collection of raw event data maps extracted from binary cache files.
     * @return `true` if the remote server acknowledges payload ingestion with an HTTP success code (2xx).
     */
    override suspend fun dispatchBatchEvent(batchList: List<HashMap<String, Any>>): Boolean {
        if (batchList.isEmpty()) return true

        // 1. Build the uniform top-level JSON payload string
        val rootJsonArray = JSONArray()
        for (eventData in batchList) {
            val eventObject = JSONObject().apply {
                put(EVENT_NAME, eventData[EVENT_NAME]?.toString() ?: SCREEN_TOUCH_EVENT)
                put(SCREEN_ID, eventData[SCREEN_ID]?.toString() ?: "UnknownScreen")
                put(TIMESTAMP, eventData[TS] as? Long ?: System.currentTimeMillis())
                put(COORDINATE_X, eventData[X] as? Double ?: 0.0)
                put(COORDINATE_Y, eventData[Y] as? Double ?: 0.0)

                val metadataMap = eventData[PARAMS] as? Map<*, *>
                val metadataJson = JSONObject()
                if (!metadataMap.isNullOrEmpty()) {
                    for ((k, v) in metadataMap) {
                        if (k != null && v != null) {
                            metadataJson.put(k.toString(), v.toString())
                        }
                    }
                }
                put(METADATA, metadataJson)
            }
            rootJsonArray.put(eventObject)
        }
        val jsonPayloadString = rootJsonArray.toString()

        // 2. Route payload via OkHttp if an explicit secure client was constructed
        return if (okHttpClient != null) {
            executeWithOkHttp(jsonPayloadString)
        } else {
            executeWithHttpUrlConnection(jsonPayloadString)
        }
    }

    /**
     * Processes payload transmission using the modern OkHttp client pipeline layer.
     */
    private fun executeWithOkHttp(payload: String): Boolean {
        return try {
            val mediaType = "$APPLICATION_JSON; charset=$UTF".toMediaType()
            val requestBody = payload.toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .addHeader(ACCEPT, APPLICATION_JSON)

            // Inject application headers onto request frame
            for ((key, value) in headerMap) {
                requestBuilder.addHeader(key, value)
            }

            // okHttpClient handles connect/read timeouts internally based on factory specs
            okHttpClient!!.newCall(requestBuilder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fallback transmission routine using standard native [HttpURLConnection].
     */
    private fun executeWithHttpUrlConnection(payload: String): Boolean {
        var urlConnection: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = POST_METHOD
            urlConnection.connectTimeout = connectionTimeoutMs
            urlConnection.readTimeout = readTimeoutMs
            urlConnection.doOutput = true

            urlConnection.setRequestProperty(CONTENT_TYPE, "$APPLICATION_JSON; $CHARSET=$UTF")
            urlConnection.setRequestProperty(ACCEPT, APPLICATION_JSON)

            for ((key, value) in headerMap) {
                urlConnection.setRequestProperty(key, value)
            }

            urlConnection.outputStream.use { os ->
                OutputStreamWriter(os, UTF).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
            }

            urlConnection.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            urlConnection?.disconnect()
        }
    }
}