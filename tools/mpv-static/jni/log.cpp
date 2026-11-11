#include "log.h"

#include <cstdio>
#include <mpv/client.h>

#include "globals.h"
#include "jni_utils.h"

void die(JNIEnv *env, const char *msg)
{
    ALOGE("%s", msg);
    throw_java_exception(env, msg);
}

void throw_java_exception(JNIEnv *env, const char *msg)
{
    jclass runtimeClass = env->FindClass("java/lang/RuntimeException");
    if (runtimeClass == nullptr) {
        return;
    }

    env->ThrowNew(runtimeClass, msg);

    env->DeleteLocalRef(runtimeClass);
}

void throw_mpv_error(JNIEnv *env, const char *operation, int error)
{
    char message[256];
    std::snprintf(message, sizeof(message), "%s failed: %s (%d)",
                  operation, mpv_error_string(error), error);
    die(env, message);
}
