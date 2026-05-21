//
// Created by Natig Hajiyev on 21.05.26.
//

#ifndef ALPHA_METRICS_ANALYTICS_SDK_SPATIALPAYLOAD_H
#define ALPHA_METRICS_ANALYTICS_SDK_SPATIALPAYLOAD_H

#include <cstdint>

struct SpatialPayload {
    int64_t timestamp;
    double x;
    double y;
    double z;
};

#endif