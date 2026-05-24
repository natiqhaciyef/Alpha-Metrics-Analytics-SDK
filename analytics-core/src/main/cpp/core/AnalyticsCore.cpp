//
// Created by Natig Hajiyev on 22.05.26.
//

#include "AnalyticsCore.h"
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>

bool AnalyticsCore::startEngine(const char* trackingFilePath) {
    std::lock_guard<std::mutex> lock(writeLock);
    if (mappedMemory != nullptr) return true;

    allocatedSize = sizeof(PersistentLayout);

    // Open with 0660 so both processes can read/write the file independently
    fileDesc = open(trackingFilePath, O_RDWR | O_CREAT, 0660);
    if (fileDesc < 0) return false;

    if (ftruncate(fileDesc, allocatedSize) != 0) {
        close(fileDesc);
        return false;
    }

    void* mmapRegion = mmap(NULL, allocatedSize, PROT_READ | PROT_WRITE, MAP_SHARED, fileDesc, 0);
    if (mmapRegion == MAP_FAILED) {
        close(fileDesc);
        return false;
    }

    mappedMemory = reinterpret_cast<PersistentLayout*>(mmapRegion);
    return true;
}

void AnalyticsCore::pushEventWithMetadata(const char* screenId, int64_t ts, double x, double y,
                                          const char keys[MAX_METADATA_PAIRS][MAX_STR_LEN],
                                          const char values[MAX_METADATA_PAIRS][MAX_STR_LEN],
                                          size_t paramCount) {
    if (!mappedMemory) return;

    std::lock_guard<std::mutex> lock(writeLock);
    size_t currentHead = mappedMemory->headIndex.load(std::memory_order_relaxed);
    size_t currentTail = mappedMemory->tailIndex.load(std::memory_order_acquire);

    AnalyticsEvent& ev = mappedMemory->eventQueue[currentHead & (MAX_QUEUE_CAPACITY - 1)];
    ev.timestamp = ts;
    ev.coordinateX = x;
    ev.coordinateY = y;

    // Copy Screen Identifiers safely
    std::strncpy(ev.screenId, screenId, sizeof(ev.screenId) - 1);
    ev.screenId[sizeof(ev.screenId) - 1] = '\0';

    // Pack metadata layout rows
    ev.metadataSize = (paramCount > MAX_METADATA_PAIRS) ? MAX_METADATA_PAIRS : paramCount;
    for (size_t i = 0; i < ev.metadataSize; ++i) {
        std::strncpy(ev.metadata[i].key, keys[i], MAX_STR_LEN - 1);
        ev.metadata[i].key[MAX_STR_LEN - 1] = '\0';

        std::strncpy(ev.metadata[i].value, values[i], MAX_STR_LEN - 1);
        ev.metadata[i].value[MAX_STR_LEN - 1] = '\0';
    }

    mappedMemory->headIndex.store(currentHead + 1, std::memory_order_release);

    if ((currentHead + 1) - currentTail > MAX_QUEUE_CAPACITY) {
        mappedMemory->tailIndex.store(currentTail + 1, std::memory_order_release);
    }
}

bool AnalyticsCore::pollNextEvent(AnalyticsEvent& outEvent) {
    if (!mappedMemory || getPendingCount() == 0) return false;
    size_t currentTail = mappedMemory->tailIndex.load(std::memory_order_relaxed);
    outEvent = mappedMemory->eventQueue[currentTail & (MAX_QUEUE_CAPACITY - 1)];
    return true;
}

size_t AnalyticsCore::getPendingCount() {
    if (!mappedMemory) return 0;
    size_t currentHead = mappedMemory->headIndex.load(std::memory_order_acquire);
    size_t currentTail = mappedMemory->tailIndex.load(std::memory_order_acquire);
    return (currentHead >= currentTail) ? (currentHead - currentTail) : 0;
}

void AnalyticsCore::popQueue() {
    if (!mappedMemory) return;
    size_t currentTail = mappedMemory->tailIndex.load(std::memory_order_relaxed);
    mappedMemory->tailIndex.store(currentTail + 1, std::memory_order_release);
}

void AnalyticsCore::stopEngine() {
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