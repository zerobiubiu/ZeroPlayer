#define UTIL_EXTERN
#include "jni_utils.h"

#include <jni.h>
#include <cstdlib>
#include <mutex>
#include <cstdint>
#include <cstring>
#include <limits>
#include <new>
#include <stdexcept>
#include <vector>

#include "log.h"

bool acquire_jni_env(JavaVM *vm, JNIEnv **env)
{
    int ret = vm->GetEnv((void**)env, JNI_VERSION_1_6);

    if (ret == JNI_EDETACHED) {
        return vm->AttachCurrentThread(env, nullptr) == JNI_OK;
    }

    return ret == JNI_OK;
}

// 按 UTF-16 代理对读取码点，拒绝无法交给 C 字符串的零值及无配对代理。
static bool read_utf16_codepoint(const jchar *input, size_t length, size_t &pos, uint32_t &codepoint) {
    uint32_t first = input[pos++];
    if (first == 0)
        return false;
    if (first >= 0xd800 && first <= 0xdbff) {
        if (pos >= length)
            return false;
        uint32_t second = input[pos++];
        if (second < 0xdc00 || second > 0xdfff)
            return false;
        codepoint = 0x10000 + ((first - 0xd800) << 10) + (second - 0xdc00);
        return true;
    }
    if (first >= 0xdc00 && first <= 0xdfff)
        return false;
    codepoint = first;
    return true;
}

bool java_string_to_utf8(JNIEnv *env, jstring input, std::string &output) {
    if (!input) {
        die(env, "Cannot convert a null Java string to UTF-8");
        return false;
    }
    const jsize string_length = env->GetStringLength(input);
    const jchar *chars = env->GetStringChars(input, nullptr);
    if (!chars) {
        if (!env->ExceptionCheck())
            die(env, "Failed to access Java string characters");
        return false;
    }

    const size_t length = static_cast<size_t>(string_length);
    size_t byte_count = 0;
    for (size_t pos = 0; pos < length;) {
        uint32_t codepoint;
        if (!read_utf16_codepoint(chars, length, pos, codepoint)) {
            env->ReleaseStringChars(input, chars);
            die(env, "Java string contains NUL or an unpaired UTF-16 surrogate");
            return false;
        }
        const size_t count = codepoint < 0x80 ? 1 : codepoint < 0x800 ? 2 : codepoint < 0x10000 ? 3 : 4;
        if (byte_count > output.max_size() - count) {
            env->ReleaseStringChars(input, chars);
            die(env, "Java string is too long to convert to UTF-8");
            return false;
        }
        byte_count += count;
    }

    try {
        output.resize(byte_count);
    } catch (const std::bad_alloc &) {
        env->ReleaseStringChars(input, chars);
        die(env, "Failed to allocate a UTF-8 string");
        return false;
    } catch (const std::length_error &) {
        env->ReleaseStringChars(input, chars);
        die(env, "Java string is too long to convert to UTF-8");
        return false;
    }

    size_t written = 0;
    for (size_t pos = 0; pos < length;) {
        uint32_t codepoint;
        read_utf16_codepoint(chars, length, pos, codepoint);
        if (codepoint < 0x80) {
            output[written++] = static_cast<char>(codepoint);
        } else if (codepoint < 0x800) {
            output[written++] = static_cast<char>(0xc0 | (codepoint >> 6));
            output[written++] = static_cast<char>(0x80 | (codepoint & 0x3f));
        } else if (codepoint < 0x10000) {
            output[written++] = static_cast<char>(0xe0 | (codepoint >> 12));
            output[written++] = static_cast<char>(0x80 | ((codepoint >> 6) & 0x3f));
            output[written++] = static_cast<char>(0x80 | (codepoint & 0x3f));
        } else {
            output[written++] = static_cast<char>(0xf0 | (codepoint >> 18));
            output[written++] = static_cast<char>(0x80 | ((codepoint >> 12) & 0x3f));
            output[written++] = static_cast<char>(0x80 | ((codepoint >> 6) & 0x3f));
            output[written++] = static_cast<char>(0x80 | (codepoint & 0x3f));
        }
    }
    env->ReleaseStringChars(input, chars);
    return true;
}

// 返回有效序列的字节数；非法、超长、代理区和超出 Unicode 范围的序列返回零。
static size_t read_utf8_codepoint(const unsigned char *input, size_t remaining, uint32_t &codepoint) {
    const unsigned char first = input[0];
    if (first < 0x80) {
        codepoint = first;
        return 1;
    }
    size_t count;
    if (first >= 0xc2 && first <= 0xdf) {
        count = 2;
        codepoint = first & 0x1f;
    } else if (first >= 0xe0 && first <= 0xef) {
        count = 3;
        codepoint = first & 0x0f;
    } else if (first >= 0xf0 && first <= 0xf4) {
        count = 4;
        codepoint = first & 0x07;
    } else {
        return 0;
    }
    if (remaining < count)
        return 0;
    for (size_t i = 1; i < count; ++i) {
        if ((input[i] & 0xc0) != 0x80)
            return 0;
        codepoint = (codepoint << 6) | (input[i] & 0x3f);
    }
    if ((count == 3 && codepoint < 0x800) ||
        (count == 4 && codepoint < 0x10000) ||
        (codepoint >= 0xd800 && codepoint <= 0xdfff) || codepoint > 0x10ffff)
        return 0;
    return count;
}

jstring java_string_from_utf8(JNIEnv *env, const char *input) {
    if (!input) {
        die(env, "Cannot convert a null native string from UTF-8");
        return nullptr;
    }
    const auto *bytes = reinterpret_cast<const unsigned char*>(input);
    const size_t length = std::strlen(input);
    constexpr char invalid_prefix[] = "<invalid-utf8:0x";
    constexpr size_t invalid_length = sizeof(invalid_prefix) - 1 + 3;
    const size_t max_units = static_cast<size_t>(std::numeric_limits<jsize>::max());
    size_t units = 0;

    for (size_t pos = 0; pos < length;) {
        uint32_t codepoint = 0;
        size_t count = read_utf8_codepoint(bytes + pos, length - pos, codepoint);
        size_t added = count ? (codepoint > 0xffff ? 2 : 1) : invalid_length;
        if (units > max_units - added) {
            die(env, "Native UTF-8 string is too long for Java");
            return nullptr;
        }
        units += added;
        pos += count ? count : 1;
    }

    jchar local_chars[256];
    jchar *chars = local_chars;
    std::vector<jchar> heap_chars;
    if (units > sizeof(local_chars) / sizeof(local_chars[0])) {
        try {
            heap_chars.resize(units);
        } catch (const std::bad_alloc &) {
            die(env, "Failed to allocate a UTF-16 string");
            return nullptr;
        } catch (const std::length_error &) {
            die(env, "Native UTF-8 string is too long for Java");
            return nullptr;
        }
        chars = heap_chars.data();
    }

    constexpr char hex[] = "0123456789ABCDEF";
    size_t written = 0;
    for (size_t pos = 0; pos < length;) {
        uint32_t codepoint = 0;
        size_t count = read_utf8_codepoint(bytes + pos, length - pos, codepoint);
        if (!count) {
            // 每个无效字节都有独立标记，避免丢失原始错误信息。
            for (size_t i = 0; i < sizeof(invalid_prefix) - 1; ++i)
                chars[written++] = static_cast<jchar>(invalid_prefix[i]);
            chars[written++] = static_cast<jchar>(hex[bytes[pos] >> 4]);
            chars[written++] = static_cast<jchar>(hex[bytes[pos] & 0x0f]);
            chars[written++] = static_cast<jchar>('>');
            ++pos;
        } else {
            if (codepoint <= 0xffff) {
                chars[written++] = static_cast<jchar>(codepoint);
            } else {
                codepoint -= 0x10000;
                chars[written++] = static_cast<jchar>(0xd800 | (codepoint >> 10));
                chars[written++] = static_cast<jchar>(0xdc00 | (codepoint & 0x3ff));
            }
            pos += count;
        }
    }
    jstring result = env->NewString(chars, static_cast<jsize>(units));
    if (!result && !env->ExceptionCheck())
        die(env, "Failed to create a Java string from UTF-8");
    return result;
}

static std::mutex init_mutex;
static bool methods_initialized = false;

// 只有所有类和方法都准备完成才发布缓存；失败时释放临时引用并允许重试。
bool init_methods_cache(JNIEnv *env) {
    std::lock_guard<std::mutex> lock(init_mutex);
    if (methods_initialized)
        return true;

    jclass integer_class = nullptr;
    jclass double_class = nullptr;
    jclass boolean_class = nullptr;
    jclass mpv_class = nullptr;

    auto fail = [&]() {
        if (integer_class) env->DeleteGlobalRef(integer_class);
        if (double_class) env->DeleteGlobalRef(double_class);
        if (boolean_class) env->DeleteGlobalRef(boolean_class);
        if (mpv_class) env->DeleteGlobalRef(mpv_class);
        return false;
    };
    auto find_and_ref = [&](const char *name) -> jclass {
        jclass local_class = env->FindClass(name);
        if (!local_class)
            return nullptr;
        auto global_ref = reinterpret_cast<jclass>(env->NewGlobalRef(local_class));
        env->DeleteLocalRef(local_class);
        return global_ref;
    };

    integer_class = find_and_ref("java/lang/Integer");
    if (!integer_class) return fail();
    jmethodID integer_init = env->GetMethodID(integer_class, "<init>", "(I)V");
    if (!integer_init) return fail();

    double_class = find_and_ref("java/lang/Double");
    if (!double_class) return fail();
    jmethodID double_init = env->GetMethodID(double_class, "<init>", "(D)V");
    if (!double_init) return fail();

    boolean_class = find_and_ref("java/lang/Boolean");
    if (!boolean_class) return fail();
    jmethodID boolean_init = env->GetMethodID(boolean_class, "<init>", "(Z)V");
    if (!boolean_init) return fail();

    mpv_class = find_and_ref("dev/jdtech/mpv/MPVLib");
    if (!mpv_class) return fail();
    jmethodID property_s = env->GetMethodID(mpv_class, "eventProperty", "(Ljava/lang/String;)V");
    if (!property_s) return fail();
    jmethodID property_sb = env->GetMethodID(mpv_class, "eventProperty", "(Ljava/lang/String;Z)V");
    if (!property_sb) return fail();
    jmethodID property_sl = env->GetMethodID(mpv_class, "eventProperty", "(Ljava/lang/String;J)V");
    if (!property_sl) return fail();
    jmethodID property_sd = env->GetMethodID(mpv_class, "eventProperty", "(Ljava/lang/String;D)V");
    if (!property_sd) return fail();
    jmethodID property_ss = env->GetMethodID(mpv_class, "eventProperty", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (!property_ss) return fail();
    jmethodID event = env->GetMethodID(mpv_class, "event", "(I)V");
    if (!event) return fail();
    jmethodID end_file = env->GetMethodID(mpv_class, "eventEndFile", "(IILjava/lang/String;)V");
    if (!end_file) return fail();
    jmethodID log_message = env->GetMethodID(mpv_class, "logMessage", "(Ljava/lang/String;ILjava/lang/String;)V");
    if (!log_message) return fail();

    java_Integer = integer_class;
    java_Integer_init = integer_init;
    java_Double = double_class;
    java_Double_init = double_init;
    java_Boolean = boolean_class;
    java_Boolean_init = boolean_init;
    mpv_MPVLib = mpv_class;
    mpv_MPVLib_eventProperty_S = property_s;
    mpv_MPVLib_eventProperty_Sb = property_sb;
    mpv_MPVLib_eventProperty_Sl = property_sl;
    mpv_MPVLib_eventProperty_Sd = property_sd;
    mpv_MPVLib_eventProperty_SS = property_ss;
    mpv_MPVLib_event = event;
    mpv_MPVLib_eventEndFile_IIS = end_file;
    mpv_MPVLib_logMessage_SiS = log_message;
    methods_initialized = true;
    return true;
}
