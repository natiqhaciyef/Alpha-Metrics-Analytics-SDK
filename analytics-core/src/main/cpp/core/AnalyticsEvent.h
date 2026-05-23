//
// Created by Natig Hajiyev on 22.05.26.
//

#ifndef ALPHA_METRICS_ANALYTICS_SDK_ANALYTICSEVENT_H
#define ALPHA_METRICS_ANALYTICS_SDK_ANALYTICSEVENT_H

#include <cstdint>
#include "MetadataPair.h"

struct AnalyticsEvent {
    int64_t timestamp;
    char eventName[64];
    double coordinateX;
    double coordinateY;
    char screenId[64];

    size_t metadataSize;
    MetadataPair metadata[MAX_METADATA_PAIRS];
};

#endif //ALPHA_METRICS_ANALYTICS_SDK_ANALYTICSEVENT_H
