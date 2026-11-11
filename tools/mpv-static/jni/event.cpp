#include <jni.h>

#include <mpv/client.h>

#include "globals.h"
#include "jni_utils.h"
#include "log.h"


static void sendPropertyUpdateToJava(JNIEnv *env, MPVInstance* instance, mpv_event_property *prop) {
    jstring jprop = java_string_from_utf8(env, prop->name);
    if (!jprop)
        return;
    jstring jvalue = nullptr;
    switch (prop->format) {
    case MPV_FORMAT_NONE:
        env->CallVoidMethod(instance->javaObject, mpv_MPVLib_eventProperty_S, jprop);
        break;
    case MPV_FORMAT_FLAG:
        env->CallVoidMethod(instance->javaObject, mpv_MPVLib_eventProperty_Sb, jprop, *(int*)prop->data);
        break;
    case MPV_FORMAT_INT64:
        env->CallVoidMethod(instance->javaObject, mpv_MPVLib_eventProperty_Sl, jprop, *(int64_t*)prop->data);
        break;
    case MPV_FORMAT_DOUBLE:
        env->CallVoidMethod(instance->javaObject, mpv_MPVLib_eventProperty_Sd, jprop, *(double*)prop->data);
        break;
    case MPV_FORMAT_STRING:
        jvalue = java_string_from_utf8(env, *(const char**)prop->data);
        if (jvalue)
            env->CallVoidMethod(instance->javaObject, mpv_MPVLib_eventProperty_SS, jprop, jvalue);
        break;
    default:
        ALOGV("sendPropertyUpdateToJava: Unknown property update format received in callback: %d!", prop->format);
        break;
    }
    if (jprop)
        env->DeleteLocalRef(jprop);
    if (jvalue)
        env->DeleteLocalRef(jvalue);
}

static void sendEventToJava(JNIEnv *env, MPVInstance* instance, int event) {
    env->CallVoidMethod(instance->javaObject, mpv_MPVLib_event, event);
}

static void sendEndFileToJava(JNIEnv *env, MPVInstance* instance, mpv_event_end_file *end) {
    int reason = end ? static_cast<int>(end->reason) : -1;
    int error = end ? end->error : 0;
    jstring text = nullptr;
    if (error < 0) {
        text = java_string_from_utf8(env, mpv_error_string(error));
        if (!text)
            return;
    }
    env->CallVoidMethod(instance->javaObject, mpv_MPVLib_eventEndFile_IIS,
                        static_cast<jint>(reason), static_cast<jint>(error), text);
    if (text)
        env->DeleteLocalRef(text);
}

static void sendLogMessageToJava(JNIEnv *env, MPVInstance* instance, mpv_event_log_message *msg) {
    jstring jprefix = java_string_from_utf8(env, msg->prefix);
    if (!jprefix)
        return;
    jstring jtext = java_string_from_utf8(env, msg->text);
    if (!jtext) {
        env->DeleteLocalRef(jprefix);
        return;
    }

    env->CallVoidMethod(instance->javaObject, mpv_MPVLib_logMessage_SiS,
        jprefix, (jint) msg->log_level, jtext);

    if (jprefix)
        env->DeleteLocalRef(jprefix);
    if (jtext)
        env->DeleteLocalRef(jtext);
}

void *event_thread(void *arg) {
    auto instance = static_cast<MPVInstance*>(arg);
    JNIEnv *env = nullptr;
    acquire_jni_env(instance->vm, &env);
    if (!env) {
        ALOGE("failed to acquire java env");
        return nullptr;
    }

    while (true) {
        mpv_event *mp_event;
        mpv_event_property *mp_property;
        mpv_event_log_message *msg;

        mp_event = mpv_wait_event(instance->mpv, -1.0);

        if (instance->event_thread_request_exit)
            break;

        if (mp_event->event_id == MPV_EVENT_NONE)
            continue;

        switch (mp_event->event_id) {
        case MPV_EVENT_LOG_MESSAGE:
            msg = (mpv_event_log_message*)mp_event->data;
            ALOGV("[%s:%s] %s", msg->prefix, msg->level, msg->text);
            sendLogMessageToJava(env, instance, msg);
            break;
        case MPV_EVENT_PROPERTY_CHANGE:
            mp_property = (mpv_event_property*)mp_event->data;
            sendPropertyUpdateToJava(env, instance, mp_property);
            break;
        case MPV_EVENT_END_FILE:
            sendEndFileToJava(env, instance, static_cast<mpv_event_end_file*>(mp_event->data));
            break;
        default:
            ALOGV("event: %s\n", mpv_event_name(mp_event->event_id));
            sendEventToJava(env, instance, mp_event->event_id);
            break;
        }
    }

    instance->vm->DetachCurrentThread();

    return nullptr;
}
