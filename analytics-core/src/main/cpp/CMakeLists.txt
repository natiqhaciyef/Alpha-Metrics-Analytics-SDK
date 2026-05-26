cmake_minimum_required(VERSION 3.22.1)

project("analytics_core")

# We only compile the persistent memory-mapped engine and its JNI gateway layer
add_library(
        analytics_core
        SHARED

        # Assumes these files are sitting right next to this CMakeLists.txt file
        "${CMAKE_CURRENT_SOURCE_DIR}/core/AnalyticsCoreEngine.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/bridge/AnalyticsCoreBridgeJNI.cpp"
)

find_library(
        log-lib
        log
)

target_link_libraries(
        analytics_core
        android
        ${log-lib}
)