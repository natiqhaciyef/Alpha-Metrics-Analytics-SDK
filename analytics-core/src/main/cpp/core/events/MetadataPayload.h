//
// Created by Natig Hajiyev on 22.05.26.
//

#ifndef ALPHA_METRICS_ANALYTICS_SDK_METADATAPAYLOAD_H
#define ALPHA_METRICS_ANALYTICS_SDK_METADATAPAYLOAD_H

#include <cstdint>
#include <atomic>
#include <mutex>

static const size_t MAX_METADATA_PAIRS = 5;
static const size_t MAX_STR_LEN = 32;

struct MetadataPayload {
    char keys[MAX_METADATA_PAIRS][MAX_STR_LEN];
    char values[MAX_METADATA_PAIRS][MAX_STR_LEN];
    size_t paramCount;
};

#endif //ALPHA_METRICS_ANALYTICS_SDK_METADATAPAYLOAD_H
