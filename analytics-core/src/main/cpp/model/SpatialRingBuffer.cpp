//
// Created by Natig Hajiyev on 21.05.26.
//

#include "SpatialRingBuffer.h"
#include <cmath>
#include <vector>

void SpatialRingBuffer::registerConsumer(std::shared_ptr<ISpatialDataConsumer> consumer) {
    std::lock_guard<std::mutex> lock(consumerMutex);
    activeConsumer = consumer;
}

void SpatialRingBuffer::unregisterConsumer() {
    std::lock_guard<std::mutex> lock(consumerMutex);
    activeConsumer = nullptr;
}

void SpatialRingBuffer::push(int64_t ts, double x, double y, double z) {
    size_t currentHead = head.load(std::memory_order_relaxed);
    size_t currentTail = tail.load(std::memory_order_acquire);

    // Apply scaling normalization uniformly exactly once upon ingest
    double normalizedX = x * 0.01;
    double normalizedY = y * 0.01;
    double normalizedZ = z * 0.01;

    buffer[currentHead & (BUFFER_SIZE - 1)] = {ts, normalizedX, normalizedY, normalizedZ};

    // Release semantics ensure the writes to buffer are visible before head increments
    head.store(currentHead + 1, std::memory_order_release);

    // Overwrite safety mitigation
    if ((currentHead + 1) - currentTail > BUFFER_SIZE) {
        tail.store(currentTail + 1, std::memory_order_release);
    }
}

size_t SpatialRingBuffer::getAvailableCount() const {
    size_t currentHead = head.load(std::memory_order_acquire);
    size_t currentTail = tail.load(std::memory_order_acquire);
    return (currentHead >= currentTail) ? (currentHead - currentTail) : 0;
}

void SpatialRingBuffer::flushToCore() {
    std::shared_ptr<ISpatialDataConsumer> consumer;
    {
        std::lock_guard<std::mutex> lock(consumerMutex);
        consumer = activeConsumer;
    }

    // Return early if there's no processing target hooked up
    if (!consumer) return;

    size_t currentHead = head.load(std::memory_order_acquire);
    size_t currentTail = tail.load(std::memory_order_relaxed);

    // Batch invoke callbacks safely out-of-scope from core state modifications
    while (currentTail < currentHead) {
        SpatialPayload item = buffer[currentTail & (BUFFER_SIZE - 1)];
        double distanceMagnitude = std::sqrt(item.x * item.x + item.y * item.y + item.z * item.z);

        consumer->onSpatialDataProcessed(item, distanceMagnitude);
        currentTail++;
    }

    tail.store(currentTail, std::memory_order_release);
}