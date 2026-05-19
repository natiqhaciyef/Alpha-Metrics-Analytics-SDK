package com.natighajiyev.analytics_core

object NativeLogger {
    init {
        // This must match the name of the library in your CMakeLists.txt
        // Android Studio usually names it exactly what you named the module.
        System.loadLibrary("analytics_core")
    }

    // The function mapped to the C++ code
    external fun logEvent(eventName: String, payload: String)
}