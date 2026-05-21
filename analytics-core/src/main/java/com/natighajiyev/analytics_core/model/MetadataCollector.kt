package com.natighajiyev.analytics_core.model

import java.util.concurrent.ConcurrentHashMap

class MetadataCollector {
    private val staticContext = ConcurrentHashMap<String, Any>()

    fun setCustomAttribute(key: String, value: String) {
        staticContext[key] = value
    }

    fun setCustomAttribute(key: String, value: Number) {
        staticContext[key] = value
    }

    fun captureCurrentSnapshot(): Map<String, String> {
        // Map everything to a safe stringified format for Protobuf packout
        return staticContext.mapValues { it.value.toString() }
    }
}