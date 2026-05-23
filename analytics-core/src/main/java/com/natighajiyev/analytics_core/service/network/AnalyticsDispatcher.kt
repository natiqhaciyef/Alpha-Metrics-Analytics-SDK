package com.natighajiyev.analytics_core.service.network

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal interface AnalyticsDispatcher {
    suspend fun dispatchEvent(eventData: HashMap<String, Any>): Boolean
}

internal class HttpAnalyticsDispatcher(
    private val endpointUrl: String = "https://your-analytics-sink.com/v1/events",
    private val connectionTimeoutMs: Int = 5000
) : AnalyticsDispatcher {

    override suspend fun dispatchEvent(eventData: HashMap<String, Any>): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            connection = url.openConnection() as HttpURLConnection
            
            // Configure corporate HTTP standards
            connection.requestMethod = "POST"
            connection.connectTimeout = connectionTimeoutMs
            connection.readTimeout = connectionTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")

            // Map the JNI event hashmap dynamically to a flat JSON structure
            val jsonPayload = serializeMapToJson(eventData)

            // Write payload stream out over the open socket connection
            connection.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun serializeMapToJson(map: HashMap<String, Any>): String {
        val rootJson = JSONObject()
        rootJson.put("screenId", map["screenId"])
        rootJson.put("timestamp", map["ts"])
        rootJson.put("coordinateX", map["x"])
        rootJson.put("coordinateY", map["y"])

        // Safely extract the dynamic metadata sub-map packed via JNI
        val params = map["params"]
        if (params is Map<*, *>) {
            val paramsJson = JSONObject()
            for ((key, value) in params) {
                paramsJson.put(key.toString(), value)
            }
            rootJson.put("metadata", paramsJson)
        }
        
        return rootJson.toString()
    }
}