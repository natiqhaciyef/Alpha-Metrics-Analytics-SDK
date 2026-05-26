package com.natighajiyev.analytics_core.network.dispatchers

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Creational factory pattern utility responsible for assembling isolated network delivery engines.
 *
 * This component acts as a bridge between high-level security configuration footprints and the underlying
 * data transport mechanisms. It isolates complex client construction requirements out of background service
 * operational loops, abstracting away conditional protocol routing rules and platform time conversions.
 */
object DispatcherFactory {

    /**
     * Dynamically builds a production-ready [HttpAnalyticsDispatcher] equipped with conditional security mechanics.
     *
     * If an explicit [pinningHash] signature is available, this method spins up an internal, fully intercept-hardened
     * [OkHttpClient] instance bound to an active [CertificatePinner] rule. If the string resolves to null or empty,
     * it skips client initialization, allowing the downstream engine to fallback cleanly to standard native system connections.
     *
     * @param endpointUrl The destination analytics ingest target server URL string structure.
     * @param connectTimeoutMs Socket allocation handshake time threshold expressed in raw milliseconds.
     * @param readTimeoutMs Inbound data stream response waiting window threshold expressed in raw milliseconds.
     * @param headers Runtime authentication tokens and metadata descriptors mapped across system process partitions.
     * @param pinningHash Optional SHA-256 public key cryptographic signature used to enforce strict SSL/TLS verification boundaries.
     * @return A configured, thread-safe [HttpAnalyticsDispatcher] transport envelope.
     */
    internal fun createSecureDispatcher(
        endpointUrl: String,
        connectTimeoutMs: Long,
        readTimeoutMs: Long,
        headers: Map<String, String>,
        pinningHash: String?
    ): HttpAnalyticsDispatcher {

        val okHttpClient = if (!pinningHash.isNullOrEmpty()) {
            try {
                val hostDomain = URL(endpointUrl).host
                val certificatePinner = CertificatePinner.Builder()
                    .add(hostDomain, "sha256/$pinningHash")
                    .build()

                OkHttpClient.Builder()
                    .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                    .certificatePinner(certificatePinner)
                    .build()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        return HttpAnalyticsDispatcher(
            okHttpClient = okHttpClient,
            endpointUrl = endpointUrl,
            connectionTimeoutMs = connectTimeoutMs.toInt(),
            readTimeoutMs = readTimeoutMs.toInt(),
            headerMap = headers
        )
    }
}