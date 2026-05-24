package com.natighajiyev.analytics_core.config

/**
 * Immutable configuration profile containing the structural behavior parameters for the SDK.
 * * This class serves as the central data blueprint directing network dispatching, memory thresholds,
 * cryptographic requirements, and background scheduling rules across process lines.
 *
 * @property networkConfig Target destination and connection timeout specifications.
 * @property batchConfig Size thresholds and backoff intervals governing background payload flushing.
 * @property securityConfig Policy restrictions covering encryption and traffic protocol validation.
 * @property storageConfig Local disk allocation limits and cache overflow eviction rules.
 * @property trapCrashes Flag enabling unhandled JVM crash interception and automated ANR tracking threads.
 * @property isLoggingEnabled Debug flag control toggling internal system log printing diagnostics.
 */
class AlphaMetricsConfig private constructor(
    val networkConfig: NetworkConfig,
    val batchConfig: BatchConfig,
    val securityConfig: SecurityConfig,
    val storageConfig: StorageConfig,
    val trapCrashes: Boolean,
    val isLoggingEnabled: Boolean,
) {
    /**
     * Fluent API construction utility implementing the Builder Pattern to assemble [AlphaMetricsConfig] properties.
     */
    class Builder {
        private var networkConfig = NetworkConfig.Builder().build()
        private var batchConfig = BatchConfig.Builder().build()
        private var securityConfig = SecurityConfig.Builder().build()
        private var storageConfig = StorageConfig.Builder().build()
        private var isLoggingEnabled = true
        private var trapCrashes: Boolean = true

        /**
         * Customizes network subsystem parameters using a functional configuration literal block.
         */
        fun network(block: NetworkConfig.Builder.() -> Unit) = apply {
            networkConfig = NetworkConfig.Builder().apply(block).build()
        }

        /**
         * Customizes transmission dispatch rules using a functional configuration literal block.
         */
        fun batch(block: BatchConfig.Builder.() -> Unit) = apply {
            batchConfig = BatchConfig.Builder().apply(block).build()
        }

        /**
         * Customizes memory footprints and overflow fallback behaviors using a functional configuration literal block.
         */
        fun storage(block: StorageConfig.Builder.() -> Unit) = apply {
            storageConfig = StorageConfig.Builder().apply(block).build()
        }

        /**
         * Customizes encryption restrictions and protocol rules using a functional configuration literal block.
         */
        fun security(block: SecurityConfig.Builder.() -> Unit) = apply {
            securityConfig = SecurityConfig.Builder().apply(block).build()
        }

        /**
         * Toggles the global diagnostic interceptor framework on or off. When disabled, the
         * background ANR Watchdog and uncaught JVM exception handlers bypass thread hook registration.
         */
        fun setCrashTrappingEnabled(enabled: Boolean) = apply { this.trapCrashes = enabled }

        /**
         * Toggles console logging outputs. Use `false` in production variants to maximize performance.
         */
        fun setLoggingEnabled(enabled: Boolean) = apply { this.isLoggingEnabled = enabled }

        /**
         * Compiles the mutable options states into a thread-safe, immutable [AlphaMetricsConfig] instance.
         */
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
