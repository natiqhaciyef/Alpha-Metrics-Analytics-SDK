//
// Created by Natig Hajiyev on 22.05.26.
//

#ifndef ALPHA_METRICS_ANALYTICS_SDK_ANALYTICSCORE_H
#define ALPHA_METRICS_ANALYTICS_SDK_ANALYTICSCORE_H

#include <cstdint>
#include <atomic>
#include <mutex>
#include "AnalyticsEvent.h"
#include "MetadataPair.h"


class AnalyticsCore {
private:
    static const size_t MAX_QUEUE_CAPACITY = 512;

    struct PersistentLayout {
        std::atomic<size_t> headIndex;
        std::atomic<size_t> tailIndex;
        AnalyticsEvent eventQueue[MAX_QUEUE_CAPACITY];
    };

    PersistentLayout* mappedMemory = nullptr;
    size_t allocatedSize = 0;
    int fileDesc = -1;
    std::mutex writeLock;

public:
    static AnalyticsCore& getInstance() {
        static AnalyticsCore instance;
        return instance;
    }

    bool startEngine(const char* trackingFilePath);
    void pushEventWithMetadata(const char* screenId, int64_t ts, double x, double y,
                               const char keys[MAX_METADATA_PAIRS][MAX_STR_LEN],
                               const char values[MAX_METADATA_PAIRS][MAX_STR_LEN],
                               size_t paramCount);
    size_t getPendingCount();
    bool pollNextEvent(AnalyticsEvent& outEvent);
    void popQueue();
    void stopEngine();
};
#endif
