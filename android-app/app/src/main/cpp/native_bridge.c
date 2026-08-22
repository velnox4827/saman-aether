#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <limits.h>
#include <stdio.h>

extern char *aether_core_start(const char *arguments);
extern char *aether_job_poll(uint64_t id);
extern char *aether_job_cancel(uint64_t id);
extern char *aether_job_free(uint64_t id);
extern void aether_string_free(char *raw);

static jstring take_reply(JNIEnv *env, char *raw) {
    if (raw == NULL) {
        return (*env)->NewStringUTF(env, "{\"ok\":false,\"error\":\"native core returned null\"}");
    }
    jstring out = (*env)->NewStringUTF(env, raw);
    aether_string_free(raw);
    return out;
}

JNIEXPORT jstring JNICALL
Java_com_saman_tunnel_NativeBridge_startCore(
        JNIEnv *env,
        jobject thiz,
        jstring arguments_json,
        jstring work_dir) {
    (void) thiz;

    const char *args = (*env)->GetStringUTFChars(env, arguments_json, NULL);
    const char *dir = (*env)->GetStringUTFChars(env, work_dir, NULL);

    if (args == NULL || dir == NULL) {
        if (args != NULL) (*env)->ReleaseStringUTFChars(env, arguments_json, args);
        if (dir != NULL) (*env)->ReleaseStringUTFChars(env, work_dir, dir);
        return (*env)->NewStringUTF(env, "{\"ok\":false,\"error\":\"could not read JNI strings\"}");
    }

    chdir(dir);
    setenv("HOME", dir, 1);
    setenv("AETHER_SOCKS", "127.0.0.1:1819", 1);

    char config_path[PATH_MAX];
    snprintf(config_path, sizeof(config_path), "%s/aether.toml", dir);
    setenv("AETHER_CONFIG", config_path, 1);

    char *reply = aether_core_start(args);

    (*env)->ReleaseStringUTFChars(env, arguments_json, args);
    (*env)->ReleaseStringUTFChars(env, work_dir, dir);

    return take_reply(env, reply);
}

JNIEXPORT jstring JNICALL
Java_com_saman_tunnel_NativeBridge_pollJob(JNIEnv *env, jobject thiz, jlong job_id) {
    (void) thiz;
    return take_reply(env, aether_job_poll((uint64_t) job_id));
}

JNIEXPORT jstring JNICALL
Java_com_saman_tunnel_NativeBridge_cancelJob(JNIEnv *env, jobject thiz, jlong job_id) {
    (void) thiz;
    return take_reply(env, aether_job_cancel((uint64_t) job_id));
}

JNIEXPORT jstring JNICALL
Java_com_saman_tunnel_NativeBridge_freeJob(JNIEnv *env, jobject thiz, jlong job_id) {
    (void) thiz;
    return take_reply(env, aether_job_free((uint64_t) job_id));
}
