package com.natighajiyev.analytics_core.config

import java.util.concurrent.TimeUnit

class NetworkConfig private constructor(
    val serverEndpoint: String,
    val backupEndpoint: String?,
    val connectTimeoutMs: Long,
    val readTimeoutMs: Long,
    val customHeaders: Map<String, String>
) {
    class Builder {
        var serverEndpoint: String = "https://your-analytics-sink.com/v1/events"
        var backupEndpoint: String? = null
        var connectTimeout: Long = 10
        var readTimeout: Long = 10
        var timeoutUnit: TimeUnit = TimeUnit.SECONDS
        var headers = mutableMapOf<String, String>()

        fun addHeader(key: String, value: String) = apply { headers[key] = value }

        fun build() = NetworkConfig(
            serverEndpoint, backupEndpoint,
            timeoutUnit.toMillis(connectTimeout),
            timeoutUnit.toMillis(readTimeout), headers
        )
    }
}