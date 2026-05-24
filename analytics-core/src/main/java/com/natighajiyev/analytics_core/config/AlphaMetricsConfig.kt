package com.natighajiyev.analytics_core.config

class AlphaMetricsConfig private constructor(
    val networkConfig: NetworkConfig,
    val batchConfig: BatchConfig,
    val securityConfig: SecurityConfig,
    val storageConfig: StorageConfig,
    val trapCrashes: Boolean,
    val isLoggingEnabled: Boolean,
) {
    class Builder {
        private var networkConfig = NetworkConfig.Builder().build()
        private var batchConfig = BatchConfig.Builder().build()
        private var securityConfig = SecurityConfig.Builder().build()
        private var storageConfig = StorageConfig.Builder().build()
        private var isLoggingEnabled = true
        private var trapCrashes: Boolean = true

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

        fun setCrashTrappingEnabled(enabled: Boolean) = apply { this.trapCrashes = enabled }

        fun setLoggingEnabled(enabled: Boolean) = apply { this.isLoggingEnabled = enabled }


        fun build() = AlphaMetricsConfig(
            networkConfig = networkConfig,
            batchConfig = batchConfig,
            securityConfig = securityConfig,
            storageConfig = storageConfig,
            trapCrashes = trapCrashes,
            isLoggingEnabled = isLoggingEnabled,
        )
    }
}


