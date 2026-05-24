package com.natighajiyev.analytics_core.config


class SecurityConfig private constructor(
    val useEncryption: Boolean,
    val pinPinningHash: String?,
    val allowCleartextTraffic: Boolean
) {
    class Builder {
        var useEncryption: Boolean = false
        var pinPinningHash: String? = null
        var allowCleartextTraffic: Boolean = false

        fun build() = SecurityConfig(useEncryption, pinPinningHash, allowCleartextTraffic)
    }
}