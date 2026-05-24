package com.natighajiyev.analytics_core.service.network

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.net.URL
import java.util.concurrent.TimeUnit

object DispatcherFactory {

    internal fun createSecureDispatcher(
        endpointUrl: String,
        connectTimeoutMs: Long,
        readTimeoutMs: Long,
        headers: Map<String, String>,
        pinningHash: String?
    ): HttpAnalyticsDispatcher {
        
        // If a pin hash exists, we enforce an OkHttpClient with a CertificatePinner
        val okHttpClient = if (!pinningHash.isNullOrEmpty()) {
            val hostDomain = URL(endpointUrl).host
            val pinner = CertificatePinner.Builder()
                .add(hostDomain, "sha256/$pinningHash")
                .build()

            OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .certificatePinner(pinner)
                .build()
        } else {
            null // Default to standard internal HttpURLConnection behavior if null
        }

        return HttpAnalyticsDispatcher(
            endpointUrl = endpointUrl,
            connectionTimeoutMs = connectTimeoutMs.toInt(),
            readTimeoutMs = readTimeoutMs.toInt(),
            headerMap = headers,
            okHttpClient = okHttpClient
        )
    }
}