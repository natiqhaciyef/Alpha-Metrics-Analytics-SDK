package com.natighajiyev.analytics_core.config

class AlphaMetricsConfig private constructor(
    val networkConfig: NetworkConfig,
    val batchConfig: BatchConfig,
    val securityConfig: SecurityConfig,
    val storageConfig: StorageConfig,
    val isLoggingEnabled: Boolean
) {
    class Builder {
        private var networkConfig = NetworkConfig.Builder().build()
        private var batchConfig = BatchConfig.Builder().build()
        private var securityConfig = SecurityConfig.Builder().build()
        private var storageConfig = StorageConfig.Builder().build()
        private var isLoggingEnabled = false

        fun network(block: NetworkConfig.Builder.() -> Unit) = apply {
            networkConfig = NetworkConfig.Builder().apply(block).build()
        }

        fun batch(block: BatchConfig.Builder.() -> Unit) = apply {
            batchConfig = BatchConfig.Builder().apply(block).build()
        }

        fun storage(block: StorageConfig.Builder.() -> Unit) = apply {
            storageConfig = StorageConfig.Builder().apply(block).build()
        }

        fun security(block: SecurityConfig.Builder.() -> Unit) = apply {
            securityConfig = SecurityConfig.Builder().apply(block).build()
        }

        fun setLoggingEnabled(enabled: Boolean) = apply { this.isLoggingEnabled = enabled }

        fun build() = AlphaMetricsConfig(networkConfig, batchConfig, securityConfig, storageConfig, isLoggingEnabled)
    }
}


