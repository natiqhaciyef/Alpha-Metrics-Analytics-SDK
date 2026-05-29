package com.natighajiyev.analytics_core.config

import android.content.Context

/**
 * Pluggable synchronization layout contract governing custom data egress workers.
 * * Implementations of this strategy contract handle the delivery or scheduling of cached
 * binary records once the application falls into background visibility frames.
 */
interface AlphaEgressWorker {
    /**
     * Triggered automatically by the SDK when all interactive UI containers transition out of visibility.
     *
     * @param context Host application context scope.
     * @param config The structural telemetry configuration matrix initialized with the SDK.
     */
    fun onEgressTriggered(context: Context, config: AlphaMetricsConfig)
}