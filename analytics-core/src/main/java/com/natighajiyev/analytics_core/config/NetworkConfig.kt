package com.natighajiyev.analytics_core.config

import java.util.concurrent.TimeUnit

/**
 * Immutable network topology profile detailing connection policies and routing routes.
 *
 * This sub-tree configures endpoint properties utilized by the background delivery system
 * to handle secure server handshakes, connection limits, and request headers.
 *
 * @property serverEndpoint The primary HTTP REST gateway URL used for receiving data payloads.
 * @property backupEndpoint An optional fallback url route used if the primary destination is unreachable.
 * @property connectTimeoutMs The maximum duration limit in milliseconds allocated to establish a socket connection.
 * @property readTimeoutMs The maximum waiting window in milliseconds allowed for incoming backend responses.
 * @property customHeaders Optional key-value header authentication structures appended to every outbound payload.
 */
class NetworkConfig private constructor(
    val serverEndpoint: String,
    val backupEndpoint: String?,
    val connectTimeoutMs: Long,
    val readTimeoutMs: Long,
    val customHeaders: Map<String, String>
) {
    /**
     * Builder utility designed to set configuration properties and convert timing properties
     * seamlessly into an immutable [NetworkConfig] block.
     */
    class Builder {
        var serverEndpoint: String = "https://your-analytics-sink.com/v1/events"
        var backupEndpoint: String? = null
        var connectTimeout: Long = 10
        var readTimeout: Long = 10
        var timeoutUnit: TimeUnit = TimeUnit.SECONDS
        var headers = mutableMapOf<String, String>()

        /**
         * Appends an intentional metadata or authentication property to the target transmission header matrix.
         */
        fun addHeader(key: String, value: String) = apply { headers[key] = value }

        /**
         * Compiles the mutable parameters tree into a thread-safe, immutable [NetworkConfig] instance,
         * automatically standardizing timeouts down to milliseconds.
         */
        fun build() = NetworkConfig(
            serverEndpoint = serverEndpoint,
            backupEndpoint = backupEndpoint,
            connectTimeoutMs = timeoutUnit.toMillis(connectTimeout),
            readTimeoutMs = timeoutUnit.toMillis(readTimeout),
            customHeaders = headers
        )
    }
}