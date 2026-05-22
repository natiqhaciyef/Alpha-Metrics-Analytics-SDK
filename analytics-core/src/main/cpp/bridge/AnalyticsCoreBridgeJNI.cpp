//
// Created by Natig Hajiyev on 22.05.26.
//

#include <jni.h>
#include "../core/AnalyticsEvent.h"
#include "../core/AnalyticsCore.h"

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeStartEngine(JNIEnv *env, jobject thiz, jstring filepath) {
    const char* nativePath = env->GetStringUTFChars(filepath, nullptr);
    bool result = AnalyticsCore::getInstance().startEngine(nativePath);
    env->ReleaseStringUTFChars(filepath, nativePath);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeStartEngineWithFd(
        JNIEnv *env, jobject thiz, jint fd, jint layout_size) {

    // Route the file descriptor parameter straight down to your Core initialization sequence
    bool result = AnalyticsCore::getInstance().startEngineWithFd(static_cast<int>(fd), static_cast<int>(layout_size));

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeLogEventWithMetadata(
        JNIEnv *env, jobject thiz, jstring screen_id, jlong timestamp, jdouble x, jdouble y,
        jobjectArray keys_array, jobjectArray values_array, jint pair_count) {

    const char* nativeScreenId = env->GetStringUTFChars(screen_id, nullptr);

    char c_keys[MAX_METADATA_PAIRS][MAX_STR_LEN] = {0};
    char c_values[MAX_METADATA_PAIRS][MAX_STR_LEN] = {0};

    size_t clampCount = (pair_count > MAX_METADATA_PAIRS) ? MAX_METADATA_PAIRS : pair_count;

    for (size_t i = 0; i < clampCount; ++i) {
        jstring js_key = (jstring)env->GetObjectArrayElement(keys_array, i);
        jstring js_val = (jstring)env->GetObjectArrayElement(values_array, i);

        if (js_key) {
            const char* k_chars = env->GetStringUTFChars(js_key, nullptr);
            std::strncpy(c_keys[i], k_chars, MAX_STR_LEN - 1);
            env->ReleaseStringUTFChars(js_key, k_chars);
            env->DeleteLocalRef(js_key);
        }
        if (js_val) {
            const char* v_chars = env->GetStringUTFChars(js_val, nullptr);
            std::strncpy(c_values[i], v_chars, MAX_STR_LEN - 1);
            env->ReleaseStringUTFChars(js_val, v_chars);
            env->DeleteLocalRef(js_val);
        }
    }

    AnalyticsCore::getInstance().pushEventWithMetadata(nativeScreenId, timestamp, x, y, c_keys, c_values, clampCount);
    env->ReleaseStringUTFChars(screen_id, nativeScreenId);
}

JNIEXPORT jobject JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativePollEvent(JNIEnv *env, jobject thiz) {
    AnalyticsEvent ev;
    if (!AnalyticsCore::getInstance().pollNextEvent(ev)) return nullptr;

    jclass mapClass = env->FindClass("java/util/HashMap");
    jmethodID mapInit = env->GetMethodID(mapClass, "<init>", "()V");
    jobject masterMap = env->NewObject(mapClass, mapInit);
    jmethodID mapPut = env->GetMethodID(mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    // Root parameters
    env->CallObjectMethod(masterMap, mapPut, env->NewStringUTF("screenId"), env->NewStringUTF(ev.screenId));

    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longInit = env->GetMethodID(longClass, "<init>", "(J)V");
    env->CallObjectMethod(masterMap, mapPut, env->NewStringUTF("ts"), env->NewObject(longClass, longInit, ev.timestamp));

    // Dynamic screen-specific parameters object metadata mapping
    jobject metaPayloadMap = env->NewObject(mapClass, mapInit);
    for (size_t i = 0; i < ev.metadataSize; ++i) {
        env->CallObjectMethod(metaPayloadMap, mapPut, env->NewStringUTF(ev.metadata[i].key), env->NewStringUTF(ev.metadata[i].value));
    }

    env->CallObjectMethod(masterMap, mapPut, env->NewStringUTF("params"), metaPayloadMap);
    return masterMap;
}

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativePopEvent(JNIEnv *env, jobject thiz) {
    AnalyticsCore::getInstance().popQueue();
}

JNIEXPORT jint JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeGetPendingCount(JNIEnv *env, jobject thiz) {
    return static_cast<jint>(AnalyticsCore::getInstance().getPendingCount());
}

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeStopEngine(JNIEnv *env, jobject thiz) {
    AnalyticsCore::getInstance().stopEngine();
}

}