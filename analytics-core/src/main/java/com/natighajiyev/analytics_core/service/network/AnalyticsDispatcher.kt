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
            urlConnection.requestMethod = "POST"
            urlConnection.connectTimeout = connectionTimeoutMs
            urlConnection.readTimeout = readTimeoutMs
            urlConnection.doOutput = true

            // 1. Establish basic network request properties
            urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            urlConnection.setRequestProperty("Accept", "application/json")

            // 2. Inject consumer custom headers passed across process lines
            for ((key, value) in headerMap) {
                urlConnection.setRequestProperty(key, value)
            }

            // 3. Assemble the top-level batch array payload
            val rootJsonArray = JSONArray()

            for (eventData in batchList) {
                val eventObject = JSONObject().apply {
                    put("eventName", eventData["eventName"]?.toString() ?: "screen_touch_event")
                    put("screenId", eventData["screenId"]?.toString() ?: "UnknownScreen")
                    put("timestamp", eventData["ts"] as? Long ?: System.currentTimeMillis())
                    put("coordinateX", eventData["x"] as? Double ?: 0.0)
                    put("coordinateY", eventData["y"] as? Double ?: 0.0)

                    // Safely extract and transform the sub-metadata hashmap blocks
                    val metadataMap = eventData["params"] as? Map<*, *>
                    val metadataJson = JSONObject()
                    if (!metadataMap.isNullOrEmpty()) {
                        for ((k, v) in metadataMap) {
                            if (k != null && v != null) {
                                metadataJson.put(k.toString(), v.toString())
                            }
                        }
                    }
                    put("metadata", metadataJson)
                }
                rootJsonArray.put(eventObject)
            }

            // 4. Stream the raw data layout directly over the socket wire
            urlConnection.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { writer ->
                    writer.write(rootJsonArray.toString())
                    writer.flush()
                }
            }

            // 5. Return success if server acknowledges payload ingestion
            urlConnection.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            urlConnection?.disconnect()
        }
    }
}