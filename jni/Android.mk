LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS := -llog
LOCAL_MODULE    := processImage
LOCAL_SRC_FILES := processImage.cpp

include $(BUILD_SHARED_LIBRARY)
