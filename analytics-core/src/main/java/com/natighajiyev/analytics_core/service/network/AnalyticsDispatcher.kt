package com.natighajiyev.analytics_core.service.network

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal interface AnalyticsDispatcher {
    suspend fun dispatchEvent(eventData: HashMap<String, Any>): Boolean
}

internal class HttpAnalyticsDispatcher(
    private val endpointUrl: String,
    private val connectionTimeoutMs: Int,
    private val readTimeoutMs: Int,
    private val headerMap: Map<String, String> = emptyMap()
) : AnalyticsDispatcher {

    override suspend fun dispatchEvent(eventData: HashMap<String, Any>): Boolean {
        var urlConnection: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "POST"
            urlConnection.connectTimeout = connectionTimeoutMs
            urlConnection.readTimeout = readTimeoutMs
            urlConnection.doOutput = true

            // Set basic payload headers
            urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

            // Inject consumer's flexible customized headers parameters mapping sets
            for ((key, value) in headerMap) {
                urlConnection.setRequestProperty(key, value)
            }

            // Serialize event data payload object structure details...
            val json = JSONObject().apply {
                put("screenId", eventData["screenId"])
                put("timestamp", eventData["ts"])
                put("coordinateX", eventData["x"])
                put("coordinateY", eventData["y"])

                // Extract the nested metadata hashmap safely
                val metadataMap = eventData["params"] as? Map<*, *>
                if (!metadataMap.isNullOrEmpty()) {
                    val metadataJson = JSONObject()
                    for ((key, value) in metadataMap) {
                        if (key != null && value != null) {
                            metadataJson.put(key.toString(), value.toString())
                        }
                    }
                    put("metadata", metadataJson)
                } else {
                    put("metadata", JSONObject())
                }
            }

            urlConnection.outputStream.use { os ->
                java.io.OutputStreamWriter(os, "UTF-8").use { it.write(json.toString()) }
            }

            urlConnection.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            urlConnection?.disconnect()
        }
    }
}