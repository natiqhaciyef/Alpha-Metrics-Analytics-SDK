//
// Created by Natig Hajiyev on 21.05.26.
//

#include "TrackingEngine.h"
#include <android/log.h>

#define LOG_TAG "TrackingEngine"

void TrackingEngine::onSpatialDataProcessed(const SpatialPayload& payload, double distanceMagnitude) {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "[TrackingEngine] Frame processed. Timestamp: %lld, Magnitude: %.4f m",
                        static_cast<long long>(payload.timestamp), distanceMagnitude);
}