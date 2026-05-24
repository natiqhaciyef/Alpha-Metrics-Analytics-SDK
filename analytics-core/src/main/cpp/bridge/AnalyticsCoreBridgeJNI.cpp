//
// Created by Natig Hajiyev on 22.05.26.
//

#include <jni.h>
#include "../core/AnalyticsEvent.h"
#include "../core/AnalyticsCore.h"
#include <cstring>

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeStartEngine(JNIEnv *env, jobject thiz, jstring filepath) {
    if (!filepath) return JNI_FALSE;
    const char* nativePath = env->GetStringUTFChars(filepath, nullptr);
    bool result = AnalyticsCore::getInstance().startEngine(nativePath);
    env->ReleaseStringUTFChars(filepath, nativePath);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeLogEventWithMetadata(
        JNIEnv *env, jobject thiz, jstring screen_id, jlong timestamp, jdouble x, jdouble y,
        jobjectArray keys_array, jobjectArray values_array, jint pair_count) {

    if (!screen_id || !keys_array || !values_array) return;
    const char* nativeScreenId = env->GetStringUTFChars(screen_id, nullptr);

    char c_keys[MAX_METADATA_PAIRS][MAX_STR_LEN] = {0};
    char c_values[MAX_METADATA_PAIRS][MAX_STR_LEN] = {0};
    size_t clampCount = (pair_count > MAX_METADATA_PAIRS) ? MAX_METADATA_PAIRS : pair_count;

    for (size_t i = 0; i < clampCount; ++i) {
        jstring js_key = static_cast<jstring>(env->GetObjectArrayElement(keys_array, i));
        jstring js_val = static_cast<jstring>(env->GetObjectArrayElement(values_array, i));

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

    // 1. Map eventName
    jstring jEventNameKey = env->NewStringUTF("eventName");
    jstring jEventNameVal = env->NewStringUTF(ev.eventName[0] != '\0' ? ev.eventName : "screen_touch_event");
    env->CallObjectMethod(masterMap, mapPut, jEventNameKey, jEventNameVal);
    env->DeleteLocalRef(jEventNameKey);
    env->DeleteLocalRef(jEventNameVal);

    // 2. Map screenId
    jstring jScreenKey = env->NewStringUTF("screenId");
    jstring jScreenVal = env->NewStringUTF(ev.screenId);
    env->CallObjectMethod(masterMap, mapPut, jScreenKey, jScreenVal);
    env->DeleteLocalRef(jScreenKey);
    env->DeleteLocalRef(jScreenVal);

    // 3. Map long timestamp
    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longInit = env->GetMethodID(longClass, "<init>", "(J)V");
    jstring jTsKey = env->NewStringUTF("ts");
    jobject jTsVal = env->NewObject(longClass, longInit, ev.timestamp);
    env->CallObjectMethod(masterMap, mapPut, jTsKey, jTsVal);
    env->DeleteLocalRef(jTsKey);
    env->DeleteLocalRef(jTsVal);

    // 4. Map coordinates metrics
    jclass doubleClass = env->FindClass("java/lang/Double");
    jmethodID doubleInit = env->GetMethodID(doubleClass, "<init>", "(D)V");

    jstring jXKey = env->NewStringUTF("x");
    jobject jXVal = env->NewObject(doubleClass, doubleInit, ev.coordinateX);
    env->CallObjectMethod(masterMap, mapPut, jXKey, jXVal);
    env->DeleteLocalRef(jXKey);
    env->DeleteLocalRef(jXVal);

    jstring jYKey = env->NewStringUTF("y");
    jobject jYVal = env->NewObject(doubleClass, doubleInit, ev.coordinateY);
    env->CallObjectMethod(masterMap, mapPut, jYKey, jYVal);
    env->DeleteLocalRef(jYKey); // FIX: Successfully updated to clear jYKey cleanly instead of jXKey duplicate!
    env->DeleteLocalRef(jYVal);

    // 5. Map custom metadata params sub-object map
    jobject metaMap = env->NewObject(mapClass, mapInit);
    for (size_t i = 0; i < ev.metadataSize; ++i) {
        jstring jK = env->NewStringUTF(ev.metadata[i].key);
        jstring jV = env->NewStringUTF(ev.metadata[i].value);
        env->CallObjectMethod(metaMap, mapPut, jK, jV);
        env->DeleteLocalRef(jK);
        env->DeleteLocalRef(jV);
    }

    jstring jParamsKey = env->NewStringUTF("params");
    env->CallObjectMethod(masterMap, mapPut, jParamsKey, metaMap);
    env->DeleteLocalRef(jParamsKey);
    env->DeleteLocalRef(metaMap);

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