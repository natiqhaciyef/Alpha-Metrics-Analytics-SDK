package com.natighajiyev.analytics_core.config


class BatchConfig private constructor(
    val maxBatchSize: Int,
    val minBatchSizeTrigger: Int,
    val retryAttemptLimit: Int,
    val backoffDelayMs: Long
) {
    class Builder {
        var maxBatchSize: Int = 50
        var minBatchSizeTrigger: Int = 10
        var retryAttemptLimit: Int = 3
        var backoffDelay: Long = 2000

        fun build() = BatchConfig(maxBatchSize, minBatchSizeTrigger, retryAttemptLimit, backoffDelay)
    }
}