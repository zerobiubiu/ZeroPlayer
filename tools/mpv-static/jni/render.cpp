#include <jni.h>

#include <mpv/client.h>

#include "jni_utils.h"
#include "log.h"
#include "globals.h"

extern "C" {
    jni_func(void, nativeAttachSurface, jlong instance, jobject surface);
    jni_func(void, nativeDetachSurface, jlong instance);
}

jni_func(void, nativeAttachSurface, jlong instance, jobject surface) {
    auto mpv_instance = reinterpret_cast<MPVInstance*>(instance);

    if (!surface) {
        die(env, "Cannot attach a null surface");
        return;
    }
    jobject new_surface = env->NewGlobalRef(surface);
    if (!new_surface) {
        if (env->ExceptionCheck())
            ALOGE("Failed to create a surface global reference");
        else
            die(env, "Failed to create a surface global reference");
        return;
    }

    int64_t wid = reinterpret_cast<intptr_t>(new_surface);
    int result = mpv_set_option(mpv_instance->mpv, "wid", MPV_FORMAT_INT64, &wid);
    if (result < 0) {
        env->DeleteGlobalRef(new_surface);
        throw_mpv_error(env, "mpv_set_option(wid)", result);
        return;
    }

    // 新引用只有设置成功后才接管；旧引用在成功替换后释放。
    jobject previous_surface = mpv_instance->surface;
    mpv_instance->surface = new_surface;
    if (previous_surface)
        env->DeleteGlobalRef(previous_surface);
}

jni_func(void, nativeDetachSurface, jlong instance) {
    auto mpv_instance = reinterpret_cast<MPVInstance*>(instance);
    int64_t wid = 0;
    int result = mpv_set_option(mpv_instance->mpv, "wid", MPV_FORMAT_INT64, &wid);
    if (result < 0) {
        throw_mpv_error(env, "mpv_set_option(wid)", result);
        return;
    }

    if (mpv_instance->surface) {
        env->DeleteGlobalRef(mpv_instance->surface);
        mpv_instance->surface = nullptr;
    }
}
