//
// Created by Natig Hajiyev on 22.05.26.
//

#ifndef ALPHA_METRICS_ANALYTICS_SDK_METADATAPAIR_H
#define ALPHA_METRICS_ANALYTICS_SDK_METADATAPAIR_H

#include <cstdint>
#include <atomic>
#include <mutex>

// Define fixed constraints for metadata pairs
static const size_t MAX_METADATA_PAIRS = 5;
static const size_t MAX_STR_LEN = 32;

struct MetadataPair {
    char key[MAX_STR_LEN];
    char value[MAX_STR_LEN];
};

#endif //ALPHA_METRICS_ANALYTICS_SDK_METADATAPAIR_H
