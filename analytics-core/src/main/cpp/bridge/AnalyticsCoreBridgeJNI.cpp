//
// Created by Natig Hajiyev on 22.05.26.
//

#include <jni.h>
#include "../core/AnalyticsEvent.h"
#include "../core/AnalyticsCoreEngine.h"
#include <cstring>

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeStartEngine(
        JNIEnv *env,
        jobject thiz,
        jstring filepath
) {
    if (!filepath) return JNI_FALSE;
    const char *nativePath = env->GetStringUTFChars(filepath, nullptr);
    bool result = AnalyticsCoreEngine::getInstance().startEngine(nativePath);
    env->ReleaseStringUTFChars(filepath, nativePath);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeLogEventWithMetadata(
        JNIEnv *env, jobject thiz,
        jstring screen_id, jlong timestamp, jdouble x, jdouble y,
        jobjectArray keys_array,
        jobjectArray values_array,
        jint pair_count
) {

    if (!screen_id || !keys_array || !values_array) return;
    const char *nativeScreenId = env->GetStringUTFChars(screen_id, nullptr);

    char c_keys[MAX_METADATA_PAIRS][MAX_STR_LEN] = {0};
    char c_values[MAX_METADATA_PAIRS][MAX_STR_LEN] = {0};
    size_t clampCount = (pair_count > MAX_METADATA_PAIRS) ? MAX_METADATA_PAIRS : pair_count;

    for (size_t i = 0; i < clampCount; ++i) {
        jstring js_key = static_cast<jstring>(env->GetObjectArrayElement(keys_array, i));
        jstring js_val = static_cast<jstring>(env->GetObjectArrayElement(values_array, i));

        if (js_key) {
            const char *k_chars = env->GetStringUTFChars(js_key, nullptr);
            std::strncpy(c_keys[i], k_chars, MAX_STR_LEN - 1);
            env->ReleaseStringUTFChars(js_key, k_chars);
            env->DeleteLocalRef(js_key);
        }
        if (js_val) {
            const char *v_chars = env->GetStringUTFChars(js_val, nullptr);
            std::strncpy(c_values[i], v_chars, MAX_STR_LEN - 1);
            env->ReleaseStringUTFChars(js_val, v_chars);
            env->DeleteLocalRef(js_val);
        }
    }

    AnalyticsCoreEngine::getInstance().pushEventWithMetadata(nativeScreenId, timestamp, x, y, c_keys,
                                                             c_values, clampCount);
    env->ReleaseStringUTFChars(screen_id, nativeScreenId);
}

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeLogEventWithLayout(
        JNIEnv *env,
        jobject thiz,
        jstring screen_id,
        jlong timestamp,
        jdouble x,
        jdouble y,
        jint screen_width,
        jint screen_height,
        jstring orientation,
        jobjectArray keys_array,
        jobjectArray values_array,
        jint pair_count
) {

    const char *screen_id_chars = env->GetStringUTFChars(screen_id, nullptr);
    std::string cpp_screen_id(screen_id_chars);
    env->ReleaseStringUTFChars(screen_id, screen_id_chars);

    const char *orientation_chars = env->GetStringUTFChars(orientation, nullptr);
    std::string cpp_orientation(orientation_chars);
    env->ReleaseStringUTFChars(orientation, orientation_chars);

    char c_keys[MAX_METADATA_PAIRS][MAX_STR_LEN] = {0};
    char c_values[MAX_METADATA_PAIRS][MAX_STR_LEN] = {0};
    size_t clampCount = (pair_count > MAX_METADATA_PAIRS) ? MAX_METADATA_PAIRS : pair_count;

    for (size_t i = 0; i < clampCount; ++i) {
        jstring js_key = static_cast<jstring>(env->GetObjectArrayElement(keys_array, i));
        jstring js_val = static_cast<jstring>(env->GetObjectArrayElement(values_array, i));

        if (js_key) {
            const char *k_chars = env->GetStringUTFChars(js_key, nullptr);
            std::strncpy(c_keys[i], k_chars, MAX_STR_LEN - 1);
            env->ReleaseStringUTFChars(js_key, k_chars);
            env->DeleteLocalRef(js_key);
        }
        if (js_val) {
            const char *v_chars = env->GetStringUTFChars(js_val, nullptr);
            std::strncpy(c_values[i], v_chars, MAX_STR_LEN - 1);
            env->ReleaseStringUTFChars(js_val, v_chars);
            env->DeleteLocalRef(js_val);
        }
    }

    AnalyticsCoreEngine::getInstance().pushEventWithLayout(
            cpp_screen_id,
            static_cast<long long>(timestamp),
            static_cast<double>(x),
            static_cast<double>(y),
            static_cast<int>(screen_width),
            static_cast<int>(screen_height),
            cpp_orientation,
            c_keys,
            c_values, clampCount
    );
}

JNIEXPORT jobject JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativePollEvent(
        JNIEnv *env,
        jobject thiz) {
    AnalyticsEvent ev;
    if (!AnalyticsCoreEngine::getInstance().pollNextEvent(ev)) return nullptr;

    jclass mapClass = env->FindClass("java/util/HashMap");
    jmethodID mapInit = env->GetMethodID(mapClass, "<init>", "()V");
    jobject masterMap = env->NewObject(mapClass, mapInit);
    jmethodID mapPut = env->GetMethodID(mapClass, "put",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jstring jEventNameKey = env->NewStringUTF("eventName");
    jstring jEventNameVal = env->NewStringUTF(
            ev.type == EventType::LAYOUT ? "layout_heatmap_event" : "screen_touch_event");
    env->CallObjectMethod(masterMap, mapPut, jEventNameKey, jEventNameVal);
    env->DeleteLocalRef(jEventNameKey);
    env->DeleteLocalRef(jEventNameVal);

    jstring jScreenKey = env->NewStringUTF("screenId");
    jstring jScreenVal = env->NewStringUTF(ev.screenId);
    env->CallObjectMethod(masterMap, mapPut, jScreenKey, jScreenVal);
    env->DeleteLocalRef(jScreenKey);
    env->DeleteLocalRef(jScreenVal);

    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longInit = env->GetMethodID(longClass, "<init>", "(J)V");
    jstring jTsKey = env->NewStringUTF("ts");
    jobject jTsVal = env->NewObject(longClass, longInit, ev.timestamp);
    env->CallObjectMethod(masterMap, mapPut, jTsKey, jTsVal);
    env->DeleteLocalRef(jTsKey);
    env->DeleteLocalRef(jTsVal);

    jclass doubleClass = env->FindClass("java/lang/Double");
    jmethodID doubleInit = env->GetMethodID(doubleClass, "<init>", "(D)V");

    jstring jXKey = env->NewStringUTF("x");
    jobject jXVal = env->NewObject(doubleClass, doubleInit, ev.x);
    env->CallObjectMethod(masterMap, mapPut, jXKey, jXVal);
    env->DeleteLocalRef(jXKey);
    env->DeleteLocalRef(jXVal);

    jstring jYKey = env->NewStringUTF("y");
    jobject jYVal = env->NewObject(doubleClass, doubleInit, ev.y);
    env->CallObjectMethod(masterMap, mapPut, jYKey, jYVal);
    env->DeleteLocalRef(jYKey);
    env->DeleteLocalRef(jYVal);

    jobject paramsSubMap = env->NewObject(mapClass, mapInit);

    if (ev.type == EventType::LAYOUT) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID integerInit = env->GetMethodID(integerClass, "<init>", "(I)V");

        jstring jWKey = env->NewStringUTF("screenWidth");
        jobject jWVal = env->NewObject(integerClass, integerInit, ev.payload.layout.screenWidth);
        env->CallObjectMethod(paramsSubMap, mapPut, jWKey, jWVal);
        env->DeleteLocalRef(jWKey);
        env->DeleteLocalRef(jWVal);

        jstring jHKey = env->NewStringUTF("screenHeight");
        jobject jHVal = env->NewObject(integerClass, integerInit, ev.payload.layout.screenHeight);
        env->CallObjectMethod(paramsSubMap, mapPut, jHKey, jHVal);
        env->DeleteLocalRef(jHKey);
        env->DeleteLocalRef(jHVal);

        jstring jOKey = env->NewStringUTF("deviceOrientation");
        jstring jOVal = env->NewStringUTF(ev.payload.layout.orientation);
        env->CallObjectMethod(paramsSubMap, mapPut, jOKey, jOVal);
        env->DeleteLocalRef(jOKey);
        env->DeleteLocalRef(jOVal);
    }

    for (size_t i = 0; i < ev.metadata.paramCount; ++i) {
        jstring jK = env->NewStringUTF(ev.metadata.keys[i]);
        jstring jV = env->NewStringUTF(ev.metadata.values[i]);
        env->CallObjectMethod(paramsSubMap, mapPut, jK, jV);
        env->DeleteLocalRef(jK);
        env->DeleteLocalRef(jV);
    }

    jstring jParamsKey = env->NewStringUTF("params");
    env->CallObjectMethod(masterMap, mapPut, jParamsKey, paramsSubMap);
    env->DeleteLocalRef(jParamsKey);
    env->DeleteLocalRef(paramsSubMap);

    return masterMap;
}

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativePopEvent(
        JNIEnv *env,
        jobject thiz) {
    AnalyticsCoreEngine::getInstance().popQueue();
}

JNIEXPORT jint JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeGetPendingCount(
        JNIEnv *env, jobject thiz) {
    return static_cast<jint>(AnalyticsCoreEngine::getInstance().getPendingCount());
}

JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_bridge_NativeAnalyticsGateway_nativeStopEngine(
        JNIEnv *env,
        jobject thiz) {
    AnalyticsCoreEngine::getInstance().stopEngine();
}

}