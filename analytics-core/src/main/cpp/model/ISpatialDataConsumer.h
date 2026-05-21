//
// Created by Natig Hajiyev on 21.05.26.
//

#ifndef ALPHA_METRICS_ANALYTICS_SDK_ISPATIALDATACONSUMER_H
#define ALPHA_METRICS_ANALYTICS_SDK_ISPATIALDATACONSUMER_H

#include "SpatialPayload.h"

class ISpatialDataConsumer {
public:
    virtual ~ISpatialDataConsumer() = default;
    virtual void onSpatialDataProcessed(const SpatialPayload& payload, double distanceMagnitude) = 0;
};

#endif