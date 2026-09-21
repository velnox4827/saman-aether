LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := zeptun-prebuilt
LOCAL_SRC_FILES := ../../../../third_party/zeptun/zig-out/android/prebuilt/$(TARGET_ARCH_ABI)/libzeptun.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../../third_party/zeptun/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := zeptun-jni
LOCAL_SRC_FILES := zeptun_jni.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../../../third_party/zeptun/include
LOCAL_STATIC_LIBRARIES := zeptun-prebuilt
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
