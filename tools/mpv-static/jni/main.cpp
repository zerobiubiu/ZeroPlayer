#include <jni.h>
#include <cstdlib>
#include <cstdio>
#include <ctime>
#include <clocale>
#include <atomic>
#include <mutex>
#include <new>
#include <string>
#include <vector>

#include <mpv/client.h>

#include <pthread.h>

extern "C" {
    #include <libavcodec/jni.h>
}

#include "log.h"
#include "jni_utils.h"
#include "event.h"
#include "globals.h"

#define ARRAYLEN(a) (sizeof(a)/sizeof(a[0]))

// Update the JNI functions to accept a jlong parameter, which will be a
// pointer to the MPVInstance
extern "C" {
    jni_func(jlong, nativeCreate, jobject thiz, jobject appctx);
    jni_func(void, nativeInit, jlong instance);
    jni_func(void, nativeDestroy, jlong instance);
    jni_func(void, nativeCommand, jlong instance, jobjectArray jarray);
};

// FFmpeg 只保存引用指针，不能持有随单个播放器销毁的引用。
static std::mutex app_context_mutex;
static jobject process_app_context = nullptr;

static void report_initialization_failure(JNIEnv *env, const char *message) {
    if (!env->ExceptionCheck()) {
        die(env, message);
        return;
    }

    ALOGE("%s", message);
    jthrowable cause = env->ExceptionOccurred();
    if (!cause)
        return;
    env->ExceptionClear();

    // 为调用者补充初始化阶段，并保留原始 Java 异常作为原因。
    jclass exception_class = env->FindClass("java/lang/RuntimeException");
    jmethodID constructor = exception_class
        ? env->GetMethodID(exception_class, "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V")
        : nullptr;
    jstring detail = constructor ? env->NewStringUTF(message) : nullptr;
    jthrowable wrapped = detail
        ? static_cast<jthrowable>(env->NewObject(exception_class, constructor, detail, cause))
        : nullptr;
    if (wrapped) {
        env->Throw(wrapped);
    } else {
        // 内存不足时无法创建包装异常，恢复原始异常而不是丢失失败信息。
        if (env->ExceptionCheck())
            env->ExceptionClear();
        env->Throw(cause);
    }
    if (wrapped) env->DeleteLocalRef(wrapped);
    if (detail) env->DeleteLocalRef(detail);
    if (exception_class) env->DeleteLocalRef(exception_class);
    env->DeleteLocalRef(cause);
}

static bool prepare_environment(JNIEnv *env, MPVInstance* instance, jobject appctx) {
    setlocale(LC_NUMERIC, "C");

    if (env->GetJavaVM(&instance->vm) != JNI_OK || !instance->vm) {
        report_initialization_failure(env, "Failed to get the Java VM");
        return false;
    }
    if (av_jni_set_java_vm(instance->vm, nullptr) < 0) {
        report_initialization_failure(env, "Failed to register the Java VM with FFmpeg");
        return false;
    }
    if (!init_methods_cache(env)) {
        report_initialization_failure(env, "Failed to initialize the JNI method cache");
        return false;
    }

    std::lock_guard<std::mutex> lock(app_context_mutex);
    if (!process_app_context) {
        jobject context = env->NewGlobalRef(appctx);
        if (!context) {
            report_initialization_failure(env, "Failed to create the FFmpeg application context reference");
            return false;
        }
        if (av_jni_set_android_app_ctx(context, nullptr) < 0) {
            env->DeleteGlobalRef(context);
            report_initialization_failure(env, "Failed to register the application context with FFmpeg");
            return false;
        }
        // 引用归进程持有，确保所有 FFmpeg 用户的生命周期内始终有效。
        process_app_context = context;
    }
    return true;
}

jni_func(jlong, nativeCreate, jobject thiz, jobject appctx) {
    if (!thiz || !appctx) {
        report_initialization_failure(env, "MPVLib and application context must not be null");
        return 0;
    }

    auto instance = new (std::nothrow) MPVInstance();
    if (!instance) {
        report_initialization_failure(env, "Failed to allocate an MPV instance");
        return 0;
    }
    instance->event_thread_id = 0;
    instance->event_thread_request_exit = false;
    instance->javaObject = env->NewGlobalRef(thiz);
    if (!instance->javaObject) {
        delete instance;
        report_initialization_failure(env, "Failed to create the MPVLib global reference");
        return 0;
    }
    if (!prepare_environment(env, instance, appctx)) {
        env->DeleteGlobalRef(instance->javaObject);
        delete instance;
        return 0;
    }

    instance->mpv = mpv_create();
    if (!instance->mpv) {
        env->DeleteGlobalRef(instance->javaObject);
        delete instance;
        report_initialization_failure(env, "Failed to create the mpv handle");
        return 0;
    }

    mpv_request_log_messages(instance->mpv, "warn");
    return reinterpret_cast<jlong>(instance);
}

jni_func(void, nativeInit, jlong instance) {
    auto mpv_instance = reinterpret_cast<MPVInstance*>(instance);
    if (!mpv_instance->mpv) {
        die(env, "mpv is not created");
        return;
    }

    int result = mpv_initialize(mpv_instance->mpv);
    if (result < 0) {
        throw_mpv_error(env, "mpv_initialize", result);
        return;
    }

    mpv_instance->event_thread_request_exit = false;
    if (pthread_create(&mpv_instance->event_thread_id, nullptr, event_thread, mpv_instance) != 0) {
        die(env, "thread create failed");
        return;
    }
    pthread_setname_np(mpv_instance->event_thread_id, "event_thread");
}

jni_func(void, nativeDestroy, jlong instance) {
    auto mpv_instance = reinterpret_cast<MPVInstance*>(instance);
    if (!mpv_instance->mpv) {
        ALOGV("Cannot destroy mpv: mpv is not initialized");
        return;
    }
    // 回调运行在事件线程上，不能由该线程等待自身或释放仍在使用的实例。
    if (mpv_instance->event_thread_id != 0 &&
        pthread_equal(mpv_instance->event_thread_id, pthread_self())) {
        die(env, "Cannot destroy mpv from its event callback thread");
        return;
    }

    mpv_instance->event_thread_request_exit = true;
    mpv_wakeup(mpv_instance->mpv);

    if (mpv_instance->event_thread_id != 0) {
        pthread_join(mpv_instance->event_thread_id, nullptr);
        mpv_instance->event_thread_id = 0;
    }

    mpv_terminate_destroy(mpv_instance->mpv);
    if (mpv_instance->surface) {
        env->DeleteGlobalRef(mpv_instance->surface);
        mpv_instance->surface = nullptr;
    }
    env->DeleteGlobalRef(mpv_instance->javaObject);
    delete mpv_instance;
}

jni_func(void, nativeCommand, jlong instance, jobjectArray jarray) {
    auto mpv_instance = reinterpret_cast<MPVInstance*>(instance);
    if (!mpv_instance->mpv) {
        die(env, "Cannot run command: mpv is not initialized");
        return;
    }
    if (!jarray) {
        die(env, "Cannot run command: argument array is null");
        return;
    }

    const char *arguments[128] = { nullptr };
    std::vector<std::string> utf8_arguments;

    int len = env->GetArrayLength(jarray);

    if (len >= ARRAYLEN(arguments)) {
        die(env, "Cannot run command: too many arguments");
        return;
    }

    try {
        utf8_arguments.reserve(static_cast<size_t>(len));
    } catch (const std::bad_alloc &) {
        die(env, "Cannot run command: argument allocation failed");
        return;
    }

    for (int i = 0; i < len; ++i) {
        jstring item = (jstring)env->GetObjectArrayElement(jarray, i);
        if (!item) {
            if (!env->ExceptionCheck())
                die(env, "Cannot run command: an argument is null");
            return;
        }
        utf8_arguments.emplace_back();
        bool converted = java_string_to_utf8(env, item, utf8_arguments.back());
        env->DeleteLocalRef(item);
        if (!converted)
            return;
        arguments[i] = utf8_arguments.back().c_str();
    }

    int result = mpv_command(mpv_instance->mpv, arguments);
    if (result < 0)
        throw_mpv_error(env, "mpv_command", result);
}
