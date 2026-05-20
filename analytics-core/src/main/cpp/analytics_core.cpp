#include <jni.h>
#include <string>
#include <android/log.h>

extern "C" JNIEXPORT void JNICALL
Java_com_natighajiyev_analytics_1core_NativeLogger_logEvent(
        JNIEnv* env,
jobject /* this */,
jstring eventName,
        jstring payload) {

    // Convert Kotlin Strings to C++ Strings
    const char* cEvent = env->GetStringUTFChars(eventName, nullptr);
    const char* cPayload = env->GetStringUTFChars(payload, nullptr);

    // Log it
    __android_log_print(ANDROID_LOG_INFO, "AnalyticsNative", "C++ received event: %s | Payload: %s", cEvent, cPayload);

    // Clean up memory
    env->ReleaseStringUTFChars(eventName, cEvent);
    env->ReleaseStringUTFChars(payload, cPayload);
}