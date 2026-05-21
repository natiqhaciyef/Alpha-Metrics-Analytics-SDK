//
// Created by Natig Hajiyev on 21.05.26.
//

#ifndef ALPHA_METRICS_ANALYTICS_SDK_TRACKINGENGINE_H
#define ALPHA_METRICS_ANALYTICS_SDK_TRACKINGENGINE_H

#include "ISpatialDataConsumer.h"

class TrackingEngine : public ISpatialDataConsumer {
public:
    void onSpatialDataProcessed(const SpatialPayload& payload, double distanceMagnitude) override;
};

#endif //ALPHA_METRICS_ANALYTICS_SDK_TRACKINGENGINE_H