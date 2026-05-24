package com.natighajiyev.analytics_core.config

/**
 * Immutable memory management profile governing the behavior of the native local storage layer.
 *
 * This sub-tree defines the limits of your binary data cache (`alpha_metrics_core.bin`) and
 * sets the adaptive fallback strategies used when your application logs events faster than your
 * background service can upload them.
 *
 * @property maxQueueCapacity The maximum number of interaction records allowed to sit inside the native memory map simultaneously.
 * @property strategyOnBufferFull The structural fallback policy applied instantly if incoming telemetry data breaches the max capacity threshold.
 */
class StorageConfig private constructor(
    val maxQueueCapacity: Int,
    val strategyOnBufferFull: FullStrategy
) {
    /**
     * Sychronization strategies that dictate how the native C++ ring buffer handles data saturation
     * when the local capacity limits are reached.
     */
    enum class FullStrategy {
        /**
         * Discards any incoming event tracking requests immediately when the cache is full,
         * preserving the existing database history exactly as it is.
         */
        DROP_NEWEST,

        /**
         * Evicts the oldest records at the head of the file stream to clear bytes,
         * ensuring the most recent user interactions are successfully captured.
         */
        PURGE_OLDEST
    }

    /**
     * Builder utility designed to configure default thresholds and compile immutable [StorageConfig] properties.
     */
    class Builder {
        var maxQueueCapacity: Int = 1000
        var strategyOnBufferFull: FullStrategy = FullStrategy.PURGE_OLDEST

        /**
         * Compiles the mutable states into a thread-safe, immutable [StorageConfig] parameters profile.
         */
        fun build() = StorageConfig(
            maxQueueCapacity = maxQueueCapacity,
            strategyOnBufferFull = strategyOnBufferFull
        )
    }
}