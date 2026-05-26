//
// Created by Natig Hajiyev on 22.05.26.
//

#ifndef ALPHA_METRICS_ANALYTICS_SDK_ANALYTICSEVENT_H
#define ALPHA_METRICS_ANALYTICS_SDK_ANALYTICSEVENT_H

#include <cstdint>
#include "events/MetadataPayload.h"
#include "events/LayoutPayload.h"

#pragma once

enum class EventType : uint8_t {
    STANDARD = 0,
    LAYOUT = 1
};


#pragma pack(push, 1)
struct AnalyticsEvent {
    EventType type;
    char screenId[64];
    int64_t timestamp;
    double x;
    double y;

    MetadataPayload metadata;

    union {
        LayoutPayload layout;
    } payload;
};
#pragma pack(pop)
#endif //ALPHA_METRICS_ANALYTICS_SDK_ANALYTICSEVENT_H
