//
// Created by Natig Hajiyev on 21.05.26.
//

#ifndef ALPHA_METRICS_ANALYTICS_SDK_SPATIALRINGBUFFER_H
#define ALPHA_METRICS_ANALYTICS_SDK_SPATIALRINGBUFFER_H

#include "SpatialPayload.h"
#include "ISpatialDataConsumer.h"
#include <atomic>
#include <memory>
#include <mutex>
#include <cstddef>

class SpatialRingBuffer {
private:
    static const size_t BUFFER_SIZE = 512;
    SpatialPayload buffer[BUFFER_SIZE];

    // Use atomics for lock-free index traversal
    std::atomic<size_t> head{0};
    std::atomic<size_t> tail{0};

    // Mutex reserved strictly for consumer updates, protecting against race conditions
    std::mutex consumerMutex;
    std::shared_ptr<ISpatialDataConsumer> activeConsumer = nullptr;

public:
    void registerConsumer(std::shared_ptr<ISpatialDataConsumer> consumer);
    void unregisterConsumer();
    void push(int64_t ts, double x, double y, double z);
    size_t getAvailableCount() const;
    void flushToCore();
};

#endif //ALPHA_METRICS_ANALYTICS_SDK_SPATIALRINGBUFFER_H