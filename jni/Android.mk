LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# OpenCV
OPENCV_LIB_TYPE:=STATIC
OPENCV_CAMERA_MODULES:=on
OPENCV_INSTALL_MODULES:=on
include C:\Users\Acer\Projects\OpenCV\OpenCV-2.4.6-android-sdk\sdk\native\jni\OpenCV.mk

LOCAL_C_INCLUDE:= C:\Users\Acer\Projects\OpenCV\OpenCV-2.4.6-android-sdk\sdk\native\jni\include
LOCAL_LDLIBS := -llog
LOCAL_MODULE    := processImage
LOCAL_SRC_FILES := processImage.cpp

include $(BUILD_SHARED_LIBRARY)
