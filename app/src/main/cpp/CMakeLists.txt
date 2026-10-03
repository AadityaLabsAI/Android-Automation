cmake_minimum_required(VERSION 3.22.1)
project(needle2native LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_ANDROID_STL_TYPE c++_static)

add_library(needle STATIC IMPORTED)
set_target_properties(needle PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libneedle.a"
    INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_SOURCE_DIR}/third_party")

add_library(needle2_jni SHARED needle_jni.cpp)
target_include_directories(needle2_jni PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/third_party")
target_compile_options(needle2_jni PRIVATE -O2 -ffunction-sections -fdata-sections)
target_link_options(needle2_jni PRIVATE -Wl,--gc-sections -Wl,--strip-all)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(needle2_jni needle ${log-lib} ${android-lib} z m dl)
