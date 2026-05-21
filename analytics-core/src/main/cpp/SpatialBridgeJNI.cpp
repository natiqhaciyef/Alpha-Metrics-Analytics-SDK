//
// Created by Natig Hajiyev on 21.05.26.
//

#include <jni.h>
#include <android/log.h>
#include <memory>
#include <vector>
#include "model/SpatialRingBuffer.h"
#include "model/TrackingEngine.h"

#define LOG_TAG "SpatialBridge_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static SpatialRingBuffer g_SpatialBuffer;
static const size_t FLUSH_THRESHOLD = 30;

extern "C" {

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_SpatialBridge_initializeEnginePipeline(JNIEnv *env, jobject thiz) {
    auto trackingEngine = std::make_shared<TrackingEngine>();
    g_SpatialBuffer.registerConsumer(trackingEngine);
    LOGI("Engine Pipeline initialized.");
}

JNIEXPORT jint JNICALL
Java_com_natighajiyev_analytics_1core_bridge_SpatialBridge_processSpatialData(
        JNIEnv *env, jobject thiz, jlong timestamp, jdouble x, jdouble y, jdouble z) {

    // Real-Time Path: coordinates pass through to live consumers
    g_SpatialBuffer.push(static_cast<int64_t>(timestamp), x, y, z);

    if (g_SpatialBuffer.getAvailableCount() >= FLUSH_THRESHOLD) {
        g_SpatialBuffer.flushToCore();
    }
    return 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_natighajiyev_analytics_1core_bridge_SpatialBridge_normalizeAndSerialize(
        JNIEnv *env,
        jobject thiz,
        jfloatArray x_coords,
        jfloatArray y_coords,
        jlongArray timestamps,
        jstring metadata_string // Added metadata parameter
) {
    // Validate pointers for coordinate primitives and metadata string references
    if (!x_coords || !y_coords || !timestamps || !metadata_string) {
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exClass) env->ThrowNew(exClass, "Native analytics layer received null arrays or metadata string");
        return nullptr;
    }

    jsize elementCount = env->GetArrayLength(x_coords);
    if (elementCount != env->GetArrayLength(y_coords) || elementCount != env->GetArrayLength(timestamps)) {
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exClass) env->ThrowNew(exClass, "Array sizes must be symmetrical across metrics arrays");
        return nullptr;
    }

    // Extract and format the incoming metadata string
    const char* nativeMetadataChars = env->GetStringUTFChars(metadata_string, nullptr);
    if (!nativeMetadataChars) {
        return nullptr; // OutOfMemoryError thrown automatically by the JVM
    }
    std::string metadataStr(nativeMetadataChars);
    env->ReleaseStringUTFChars(metadata_string, nativeMetadataChars);

    uint32_t metadataByteLength = static_cast<uint32_t>(metadataStr.size());

    // Handle structural early return if no elements are present, preserving metadata context
    if (elementCount == 0 && metadataByteLength == 0) {
        return env->NewByteArray(0);
    }

    // Pin primitive data arrays to bypass JVM Garbage Collection sweeps
    jfloat* rawX = env->GetFloatArrayElements(x_coords, nullptr);
    jfloat* rawY = env->GetFloatArrayElements(y_coords, nullptr);
    jlong*  rawTimestamps = env->GetLongArrayElements(timestamps, nullptr);

    // Memory footprint calculation & allocation sizing optimization
    const size_t coordinateRecordSize = sizeof(int64_t) + (sizeof(double) * 2);
    const size_t totalExpectedSize = sizeof(uint32_t) + metadataByteLength + (elementCount * coordinateRecordSize);

    std::vector<uint8_t> serializedBuffer;
    serializedBuffer.reserve(totalExpectedSize);

    auto appendBytes = [&serializedBuffer](const uint8_t* ptr, size_t size) {
        serializedBuffer.insert(serializedBuffer.end(), ptr, ptr + size);
    };

    // STEP 1: Write metadata header segment
    appendBytes(reinterpret_cast<const uint8_t*>(&metadataByteLength), sizeof(uint32_t));
    if (metadataByteLength > 0) {
        appendBytes(reinterpret_cast<const uint8_t*>(metadataStr.data()), metadataByteLength);
    }

    // STEP 2: Write normalized spatial telemetry data matrix
    for (jsize i = 0; i < elementCount; i++) {
        int64_t ts = static_cast<int64_t>(rawTimestamps[i]);
        double serializedNormX = static_cast<double>(rawX[i]) * 0.01;
        double serializedNormY = static_cast<double>(rawY[i]) * 0.01;

        appendBytes(reinterpret_cast<const uint8_t*>(&ts), sizeof(int64_t));
        appendBytes(reinterpret_cast<const uint8_t*>(&serializedNormX), sizeof(double));
        appendBytes(reinterpret_cast<const uint8_t*>(&serializedNormY), sizeof(double));
    }

    // Unpin arrays to release locks back to JVM heap spaces safely
    env->ReleaseFloatArrayElements(x_coords, rawX, JNI_ABORT);
    env->ReleaseFloatArrayElements(y_coords, rawY, JNI_ABORT);
    env->ReleaseLongArrayElements(timestamps, rawTimestamps, JNI_ABORT);

    // Construct structural java primitive byte array container to ship to Room
    jbyteArray resultByteArray = env->NewByteArray(static_cast<jsize>(serializedBuffer.size()));
    if (!resultByteArray) return nullptr;

    env->SetByteArrayRegion(
            resultByteArray, 0, static_cast<jsize>(serializedBuffer.size()),
            reinterpret_cast<const jbyte*>(serializedBuffer.data())
    );

    return resultByteArray;
}

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_SpatialBridge_shutdownEnginePipeline(JNIEnv *env, jobject thiz) {
    g_SpatialBuffer.unregisterConsumer();
    LOGI("Engine Pipeline shut down cleanly.");
}

}