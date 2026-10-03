#include <jni.h>
#include "needle.h"
#include <sys/mman.h>
#include <unistd.h>
#include <cerrno>
#include <cstdint>
#include <string>
#include <vector>

namespace {
void* g_map_base = nullptr;
size_t g_map_length = 0;
const unsigned char* g_model = nullptr;
size_t g_model_length = 0;

std::string toString(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* p = env->GetStringUTFChars(value, nullptr);
    std::string out = p ? p : "";
    if (p) env->ReleaseStringUTFChars(value, p);
    return out;
}

int mapModel(int fd, long long offset, long long length) {
    if (g_model) return 0;
    if (fd < 0 || length <= 0 || offset < 0) return -22;

    long page = sysconf(_SC_PAGESIZE);
    if (page <= 0) page = 4096;

    long long aligned = offset & ~((long long)page - 1);
    size_t delta = static_cast<size_t>(offset - aligned);
    size_t total = delta + static_cast<size_t>(length);

    void* base = mmap(nullptr, total, PROT_READ, MAP_PRIVATE, fd, static_cast<off_t>(aligned));
    if (base == MAP_FAILED) return -errno;

    const unsigned char* model = static_cast<const unsigned char*>(base) + delta;
    int rc = needle_load(model, static_cast<unsigned long long>(length));
    if (rc < 0) {
        munmap(base, total);
        return rc;
    }

    g_map_base = base;
    g_map_length = total;
    g_model = model;
    g_model_length = static_cast<size_t>(length);
    return rc;
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aadityalabs_needle2_NativeNeedle_nativeLoadModelFd(
        JNIEnv*, jclass, jint fd, jlong offset, jlong length) {
    return mapModel(fd, static_cast<long long>(offset), static_cast<long long>(length));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aadityalabs_needle2_NativeNeedle_nativeInit(
        JNIEnv* env, jclass, jstring systemPrompt, jstring toolsJson, jstring toolIndexPath) {
    std::string system = toString(env, systemPrompt);
    std::string tools = toString(env, toolsJson);
    std::string index = toString(env, toolIndexPath);
    return needle_init(system.c_str(), tools.c_str(), index.empty() ? nullptr : index.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aadityalabs_needle2_NativeNeedle_nativeComplete(
        JNIEnv* env, jclass, jstring input, jint maxTokens) {
    std::string query = toString(env, input);
    std::vector<char> output(65536, 0);
    int rc = needle_complete(query.c_str(), static_cast<int>(maxTokens),
                             output.data(), static_cast<int>(output.size()));
    if (rc < 0) {
        std::string err = std::string("{\"type\":\"error\",\"code\":") +
                std::to_string(rc) + "}";
        return env->NewStringUTF(err.c_str());
    }
    return env->NewStringUTF(output.data());
}

extern "C" JNIEXPORT void JNICALL
Java_com_aadityalabs_needle2_NativeNeedle_nativeReset(JNIEnv*, jclass) {
    needle_reset();
}
