package com.natighajiyev.analytics_core.config

class StorageConfig private constructor(
    val maxQueueCapacity: Int,
    val strategyOnBufferFull: FullStrategy
) {
    enum class FullStrategy {
        DROP_NEWEST,
        PURGE_OLDEST
    }

    class Builder {
        var maxQueueCapacity: Int = 1000
        var strategyOnBufferFull: FullStrategy = FullStrategy.PURGE_OLDEST

        fun build() = StorageConfig(maxQueueCapacity, strategyOnBufferFull)
    }
}