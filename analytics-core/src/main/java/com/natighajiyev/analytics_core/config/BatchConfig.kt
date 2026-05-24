package com.natighajiyev.analytics_core.config


/**
 * Immutable policy profile governing telemetry batching behavior and server delivery rules.
 *
 * This configuration sub-tree tunes transmission properties utilized exclusively by the isolated
 * background service pipeline to control bandwidth limits, payload sizing, and adaptive retry pacing.
 *
 * @property maxBatchSize The maximum number of interaction records wrapped inside a single network request.
 * @property minBatchSizeTrigger The baseline event count required to initiate an automated background delivery sweep.
 * @property retryAttemptLimit The maximum number of network transmission attempts allowed before a batch gives up.
 * @property backoffDelayMs The baseline delay interval in milliseconds applied sequentially during retry loops.
 * @property maxEventsPerBackgroundSession The maximum upload ceiling cap allowed for an isolated background sync operation.
 */
class BatchConfig private constructor(
    val maxBatchSize: Int,
    val minBatchSizeTrigger: Int,
    val retryAttemptLimit: Int,
    val backoffDelayMs: Long,
    val maxEventsPerBackgroundSession: Int
) {
    /**
     * Builder utility to set default options and assemble immutable [BatchConfig] instances.
     */
    class Builder {
        var maxBatchSize: Int = 50
        var minBatchSizeTrigger: Int = 10
        var retryAttemptLimit: Int = 3
        var backoffDelay: Long = 2000
        var maxEventsPerBackgroundSession: Int = 200

        /**
         * Compiles the properties into an immutable, thread-safe [BatchConfig] data profile snapshot.
         */
        fun build() = BatchConfig(
            maxBatchSize = maxBatchSize,
            minBatchSizeTrigger = minBatchSizeTrigger,
            retryAttemptLimit = retryAttemptLimit,
            backoffDelayMs = backoffDelay,
            maxEventsPerBackgroundSession = maxEventsPerBackgroundSession
        )
    }
}