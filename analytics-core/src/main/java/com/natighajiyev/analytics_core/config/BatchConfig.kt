package com.natighajiyev.analytics_core.config


class BatchConfig private constructor(
    val maxBatchSize: Int,
    val minBatchSizeTrigger: Int,
    val retryAttemptLimit: Int,
    val backoffDelayMs: Long,
    val maxEventsPerBackgroundSession: Int
) {
    class Builder {
        var maxBatchSize: Int = 50
        var minBatchSizeTrigger: Int = 10
        var retryAttemptLimit: Int = 3
        var backoffDelay: Long = 2000
        var maxEventsPerBackgroundSession: Int = 200

        fun build() = BatchConfig(maxBatchSize, minBatchSizeTrigger, retryAttemptLimit, backoffDelay, maxEventsPerBackgroundSession)
    }
}