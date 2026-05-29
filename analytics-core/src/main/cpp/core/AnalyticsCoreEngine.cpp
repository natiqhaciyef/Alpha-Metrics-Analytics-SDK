//
// Created by Natig Hajiyev on 22.05.26.
//

#include "AnalyticsCoreEngine.h"
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>

bool AnalyticsCoreEngine::startEngine(const char *trackingFilePath) {
    std::lock_guard<std::mutex> lock(writeLock);
    if (mappedMemory != nullptr) return true;

    allocatedSize = sizeof(PersistentLayout);

    fileDesc = open(trackingFilePath, O_RDWR | O_CREAT, 0660);
    if (fileDesc < 0) return false;

    if (ftruncate(fileDesc, allocatedSize) != 0) {
        close(fileDesc);
        return false;
    }

    void *mmapRegion = mmap(NULL, allocatedSize, PROT_READ | PROT_WRITE, MAP_SHARED, fileDesc, 0);
    if (mmapRegion == MAP_FAILED) {
        close(fileDesc);
        return false;
    }

    mappedMemory = reinterpret_cast<PersistentLayout *>(mmapRegion);
    return true;
}

void AnalyticsCoreEngine::pushEventWithMetadata(const char *screenId, int64_t ts, double x, double y,
                                          const char keys[MAX_METADATA_PAIRS][MAX_STR_LEN],
                                          const char values[MAX_METADATA_PAIRS][MAX_STR_LEN],
                                          size_t paramCount) {
    if (mappedMemory == nullptr) return;
    std::lock_guard<std::mutex> lock(writeLock);

    size_t currentTail = mappedMemory->tailIndex.load(std::memory_order_relaxed);
    size_t currentHead = mappedMemory->headIndex.load(std::memory_order_relaxed);

    if ((currentTail + 1) % MAX_QUEUE_CAPACITY == currentHead) return; // Buffer full

    AnalyticsEvent &targetEvent = mappedMemory->eventQueue[currentTail];

    targetEvent.type = EventType::STANDARD;
    std::snprintf(targetEvent.screenId, sizeof(targetEvent.screenId), "%s", screenId);

    targetEvent.timestamp = ts;
    targetEvent.x = x;
    targetEvent.y = y;
    targetEvent.metadata.paramCount = (paramCount > MAX_METADATA_PAIRS) ? MAX_METADATA_PAIRS : paramCount;

    for (size_t i = 0; i < targetEvent.metadata.paramCount; ++i) {
        std::snprintf(targetEvent.metadata.keys[i], MAX_STR_LEN, "%s", keys[i]);
        std::snprintf(targetEvent.metadata.values[i], MAX_STR_LEN, "%s", values[i]);
    }

    size_t nextTail = (currentTail + 1) % MAX_QUEUE_CAPACITY;
    mappedMemory->tailIndex.store(nextTail, std::memory_order_release);
}


void AnalyticsCoreEngine::pushEventWithLayout(const std::string &screen_id,
                                        long long timestamp, double x,
                                        double y, int screen_width, int screen_height,
                                        const std::string &orientation, const char (*keys)[32],
                                        const char (*values)[32], size_t paramCount) {

    if (mappedMemory == nullptr) return;
    std::lock_guard<std::mutex> lock(writeLock);

    size_t currentTail = mappedMemory->tailIndex.load(std::memory_order_relaxed);
    size_t currentHead = mappedMemory->headIndex.load(std::memory_order_relaxed);

    if ((currentTail + 1) % MAX_QUEUE_CAPACITY == currentHead) return; // Full

    AnalyticsEvent &targetEvent = mappedMemory->eventQueue[currentTail];

    targetEvent.type = EventType::LAYOUT;

    std::snprintf(targetEvent.screenId, sizeof(targetEvent.screenId), "%s", screen_id.c_str());
    targetEvent.screenId[sizeof(targetEvent.screenId) - 1] = '\0';

    targetEvent.timestamp = timestamp;
    targetEvent.x = x;
    targetEvent.y = y;

    targetEvent.payload.layout.screenWidth = screen_width;
    targetEvent.payload.layout.screenHeight = screen_height;

    std::snprintf(targetEvent.payload.layout.orientation,
                  sizeof(targetEvent.payload.layout.orientation), "%s", orientation.c_str());
    targetEvent.payload.layout.orientation[sizeof(targetEvent.payload.layout.orientation) -
                                           1] = '\0';

    targetEvent.metadata.paramCount = std::min(paramCount, MAX_METADATA_PAIRS);
    for (size_t i = 0; i < targetEvent.metadata.paramCount; ++i) {
        std::strncpy(targetEvent.metadata.keys[i], keys[i], MAX_STR_LEN - 1);
        targetEvent.metadata.keys[i][MAX_STR_LEN - 1] = '\0';
        std::strncpy(targetEvent.metadata.values[i], values[i], MAX_STR_LEN - 1);
        targetEvent.metadata.values[i][MAX_STR_LEN - 1] = '\0';
    }

    size_t nextTail = (currentTail + 1) % MAX_QUEUE_CAPACITY;
    mappedMemory->tailIndex.store(nextTail, std::memory_order_release
    );
}

bool AnalyticsCoreEngine::pollNextEvent(AnalyticsEvent &outEvent) {
    if (!mappedMemory || getPendingCount() == 0) return false;
    size_t currentTail = mappedMemory->tailIndex.load(std::memory_order_relaxed);
    outEvent = mappedMemory->eventQueue[currentTail & (MAX_QUEUE_CAPACITY - 1)];
    return true;
}

size_t AnalyticsCoreEngine::getPendingCount() {
    if (!mappedMemory) return 0;
    size_t currentHead = mappedMemory->headIndex.load(std::memory_order_acquire);
    size_t currentTail = mappedMemory->tailIndex.load(std::memory_order_acquire);

    return (currentTail - currentHead + MAX_QUEUE_CAPACITY) % MAX_QUEUE_CAPACITY;
}

void AnalyticsCoreEngine::popQueue() {
    if (!mappedMemory) return;
    size_t currentTail = mappedMemory->tailIndex.load(std::memory_order_relaxed);
    mappedMemory->tailIndex.store(currentTail + 1, std::memory_order_release);
}

void AnalyticsCoreEngine::stopEngine() {
    std::lock_guard<std::mutex> lock(writeLock);
    if (mappedMemory && mappedMemory != MAP_FAILED) {
        munmap(mappedMemory, allocatedSize);
        mappedMemory = nullptr;
    }
    if (fileDesc >= 0) {
        close(fileDesc);
        fileDesc = -1;
    }
}