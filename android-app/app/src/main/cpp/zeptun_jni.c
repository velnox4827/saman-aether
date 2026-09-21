#include <jni.h>
#include <pthread.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

#include "zeptun.h"

#define ZEPTUN_JNI_CLASS "com/saman/tunnel/ZeptunBridge"

typedef struct {
    Zeptun *tun;
    JavaVM *vm;
    jobject service;
    jmethodID protect;
    pthread_t thread;
    int running;
} Context;

static Context context;

static JNIEnv *attach(int *attached) {
    JNIEnv *env = NULL;
    *attached = 0;
    if ((*context.vm)->GetEnv(context.vm, (void **)&env, JNI_VERSION_1_6) == JNI_OK) return env;
    if ((*context.vm)->AttachCurrentThread(context.vm, &env, NULL) != JNI_OK) return NULL;
    *attached = 1;
    return env;
}

static bool protect_socket(void *ctx, int fd) {
    (void)ctx;
    if (context.vm == NULL || context.service == NULL || context.protect == NULL) return true;
    int attached = 0;
    JNIEnv *env = attach(&attached);
    if (env == NULL) return false;
    jboolean ok = (*env)->CallBooleanMethod(env, context.service, context.protect, (jint)fd);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        ok = JNI_FALSE;
    }
    if (attached) (*context.vm)->DetachCurrentThread(context.vm);
    return ok == JNI_TRUE;
}

static void *run_engine(void *arg) {
    (void)arg;
    zeptun_run(context.tun);
    return NULL;
}

static jint start(JNIEnv *env, jobject self, jobject service, jint fd, jstring config) {
    (void)self;
    if (context.tun != NULL) return ZEPTUN_ERR_ALREADY_RUNNING;
    memset(&context, 0, sizeof context);
    (*env)->GetJavaVM(env, &context.vm);
    if (service != NULL) {
        context.service = (*env)->NewGlobalRef(env, service);
        jclass cls = (*env)->GetObjectClass(env, service);
        context.protect = (*env)->GetMethodID(env, cls, "protect", "(I)Z");
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    }

    int rc;
    if (config != NULL) {
        const char *text = (*env)->GetStringUTFChars(env, config, NULL);
        if (text == NULL) return ZEPTUN_ERR_INVALID_ARGUMENT;
        rc = zeptun_create_from_toml(text, strlen(text), &context.tun);
        (*env)->ReleaseStringUTFChars(env, config, text);
    } else {
        ZeptunConfig cfg;
        rc = zeptun_config_init(&cfg, ZEPTUN_PRESET_MOBILE);
        if (rc == ZEPTUN_OK) {
            cfg.device_kind = ZEPTUN_DEVICE_FD;
            cfg.tun_fd = fd;
            rc = zeptun_create(&cfg, &context.tun);
        }
    }
    if (rc != ZEPTUN_OK) return rc;

    if (config != NULL) zeptun_set_device_fd(context.tun, fd);
    zeptun_set_protect_callback(context.tun, protect_socket, NULL);
    if (pthread_create(&context.thread, NULL, run_engine, NULL) != 0) {
        zeptun_destroy(context.tun);
        context.tun = NULL;
        return ZEPTUN_ERR_OUT_OF_MEMORY;
    }
    context.running = 1;
    return ZEPTUN_OK;
}

static void stop(JNIEnv *env, jobject self) {
    (void)self;
    if (context.tun == NULL) return;
    zeptun_stop(context.tun);
    if (context.running) {
        pthread_join(context.thread, NULL);
        context.running = 0;
    }
    zeptun_destroy(context.tun);
    context.tun = NULL;
    if (context.service != NULL) {
        (*env)->DeleteGlobalRef(env, context.service);
        context.service = NULL;
    }
}

static jstring version(JNIEnv *env, jobject self) {
    (void)self;
    return (*env)->NewStringUTF(env, zeptun_version_string());
}

static jlong counter(JNIEnv *env, jobject self, jint index) {
    (void)env;
    (void)self;
    if (context.tun == NULL) return -1;
    ZeptunStats stats;
    if (zeptun_stats(context.tun, &stats) != ZEPTUN_OK) return -1;
    const uint64_t *fields = &stats.rx_packets;
    size_t count = (sizeof(ZeptunStats) - offsetof(ZeptunStats, rx_packets)) / sizeof(uint64_t);
    if (index < 0 || (size_t)index >= count) return -1;
    return (jlong)fields[index];
}

static const JNINativeMethod methods[] = {
    { "nativeStart", "(Ljava/lang/Object;ILjava/lang/String;)I", (void *)start },
    { "nativeStop", "()V", (void *)stop },
    { "nativeVersion", "()Ljava/lang/String;", (void *)version },
    { "nativeCounter", "(I)J", (void *)counter },
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = (*env)->FindClass(env, ZEPTUN_JNI_CLASS);
    if (cls == NULL) return JNI_VERSION_1_6;
    if ((*env)->RegisterNatives(env, cls, methods, sizeof methods / sizeof methods[0]) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
