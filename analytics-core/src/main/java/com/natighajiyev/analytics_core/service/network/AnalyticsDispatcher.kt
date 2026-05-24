package com.natighajiyev.analytics_core.service.network

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal interface AnalyticsDispatcher {
    suspend fun dispatchEvent(eventData: HashMap<String, Any>): Boolean
    suspend fun dispatchBatchEvent(batchList: List<HashMap<String, Any>>): Boolean
}

internal class HttpAnalyticsDispatcher(
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
     */
    override suspend fun dispatchEvent(eventData: HashMap<String, Any>): Boolean {
        return dispatchBatchEvent(listOf(eventData))
    }

    /**
     * Packs multiple metrics collections into a single structured HTTP JSON Array request.
     * Maps perfectly to the maxBatchSize requirements of the service.
     */
    override suspend fun dispatchBatchEvent(batchList: List<HashMap<String, Any>>): Boolean {
        if (batchList.isEmpty()) return true

        var urlConnection: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = POST_METHOD
            urlConnection.connectTimeout = connectionTimeoutMs
            urlConnection.readTimeout = readTimeoutMs
            urlConnection.doOutput = true

            // Establish basic network request properties
            urlConnection.setRequestProperty(CONTENT_TYPE, "$APPLICATION_JSON; $CHARSET=$UTF")
            urlConnection.setRequestProperty(ACCEPT, APPLICATION_JSON)

            // Inject consumer custom headers passed across process lines
            for ((key, value) in headerMap) {
                urlConnection.setRequestProperty(key, value)
            }

            // Assemble the top-level batch array payload
            val rootJsonArray = JSONArray()

            for (eventData in batchList) {
                val eventObject = JSONObject().apply {
                    put(EVENT_NAME, eventData[EVENT_NAME]?.toString() ?: SCREEN_TOUCH_EVENT)
                    put(SCREEN_ID, eventData[SCREEN_ID]?.toString() ?: "UnknownScreen")
                    put(TIMESTAMP, eventData[TS] as? Long ?: System.currentTimeMillis())
                    put(COORDINATE_X, eventData[X] as? Double ?: 0.0)
                    put(COORDINATE_Y, eventData[Y] as? Double ?: 0.0)

                    // Safely extract and transform the sub-metadata hashmap blocks
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

            // Stream the raw data layout directly over the socket wire
            urlConnection.outputStream.use { os ->
                OutputStreamWriter(os, UTF).use { writer ->
                    writer.write(rootJsonArray.toString())
                    writer.flush()
                }
            }

            // Return success if server acknowledges payload ingestion
            urlConnection.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            urlConnection?.disconnect()
        }
    }
}