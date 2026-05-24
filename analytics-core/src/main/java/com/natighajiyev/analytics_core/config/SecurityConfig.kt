package com.natighajiyev.analytics_core.config

/**
 * Immutable security topology profile governing data transport protection rules.
 *
 * This sub-tree configures payload encryption states, protocol validation gates,
 * and certificate verification constraints enforced across network transaction operations.
 *
 * @property useEncryption Flag enabling payload body symmetry block encryption transformations before upload.
 * @property pinPinningHash The expected SHA-256 public key cryptographic signature hash utilized to enforce strict SSL/TLS Certificate Pinning verification rules.
 * @property allowCleartextTraffic Safety flag determining whether unencrypted HTTP raw traffic protocols are blocked or permitted.
 */
class SecurityConfig private constructor(
    val useEncryption: Boolean,
    val pinPinningHash: String?,
    val allowCleartextTraffic: Boolean
) {
    /**
     * Builder utility designed to set default security profile properties and assemble immutable [SecurityConfig] instances.
     */
    class Builder {
        var useEncryption: Boolean = false
        var pinPinningHash: String? = null
        var allowCleartextTraffic: Boolean = false

        /**
         * Compiles the properties into an immutable, thread-safe [SecurityConfig] profile snapshot.
         */
        fun build() = SecurityConfig(
            useEncryption = useEncryption,
            pinPinningHash = pinPinningHash,
            allowCleartextTraffic = allowCleartextTraffic
        )
    }
}