# Copyright (C) 2011 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

ifneq ($(TARGET_BUILD_PDK), true)

# Make HAL stub library
# ============================================================

include $(CLEAR_VARS)

LOCAL_REQUIRED_MODULES :=

LOCAL_CFLAGS += -Wno-unused-parameter -Wno-int-to-pointer-cast
LOCAL_CFLAGS += -Wno-maybe-uninitialized -Wno-parentheses
LOCAL_CPPFLAGS += -Wno-conversion-null

LOCAL_C_INCLUDES += \
	external/libnl-headers \
	$(call include-path-for, libhardware_legacy)/hardware_legacy

LOCAL_SRC_FILES := \
	lib/wifi_hal.cpp

LOCAL_MODULE := libwifi-hal

include $(BUILD_STATIC_LIBRARY)

# set correct hal library path
# ============================================================
LIB_WIFI_HAL := libwifi-hal

ifeq ($(BOARD_WLAN_DEVICE), bcmdhd)
  LIB_WIFI_HAL := libwifi-hal-bcm
else ifeq ($(BOARD_WLAN_DEVICE), qcwcn)
  # this is commented because none of the nexus devices
  # that sport Qualcomm's wifi have support for HAL
  # LIB_WIFI_HAL := libwifi-hal-qcom
else ifeq ($(BOARD_WLAN_DEVICE), mrvl)
  # this is commented because none of the nexus devices
  # that sport Marvell's wifi have support for HAL
  # LIB_WIFI_HAL := libwifi-hal-mrvl
endif

ifeq ($(MTK_WLAN_SUPPORT), yes)
#  LIB_WIFI_HAL := libwifi-hal-mt66xx
endif

# Build the HalUtil
# ============================================================

include $(CLEAR_VARS)

LOCAL_REQUIRED_MODULES := libandroid_runtime libhardware_legacy

LOCAL_CFLAGS += -Wno-unused-parameter -Wno-int-to-pointer-cast
LOCAL_CFLAGS += -Wno-maybe-uninitialized -Wno-parentheses
LOCAL_CPPFLAGS += -Wno-conversion-null

LOCAL_C_INCLUDES += \
	$(call include-path-for, libhardware)/hardware \
	$(call include-path-for, libhardware_legacy)/hardware_legacy \
	libcore/include

LOCAL_SHARED_LIBRARIES += \
	libcutils \
	libnl \
	libandroid_runtime \
	libutils

LOCAL_STATIC_LIBRARIES += $(LIB_WIFI_HAL)

LOCAL_SRC_FILES := \
	tools/halutil/halutil.cpp

LOCAL_MODULE := halutil

include $(BUILD_EXECUTABLE)

# Make the JNI part
# ============================================================
include $(CLEAR_VARS)

LOCAL_REQUIRED_MODULES := libandroid_runtime libhardware_legacy

LOCAL_CFLAGS += -Wno-unused-parameter -Wno-int-to-pointer-cast
LOCAL_CFLAGS += -Wno-maybe-uninitialized -Wno-parentheses
LOCAL_CPPFLAGS += -Wno-conversion-null

#added by MTK
LOCAL_CFLAGS += -DCONFIG_MEDIATEK_WIFI_BEAM

LOCAL_C_INCLUDES += \
	$(JNI_H_INCLUDE) \
	$(call include-path-for, libhardware)/hardware \
	$(call include-path-for, libhardware_legacy)/hardware_legacy \
	libcore/include \
    external/icu/icu4c/source/common

LOCAL_SHARED_LIBRARIES += \
	libnativehelper \
	libcutils \
	libutils \
	libhardware \
	libhardware_legacy \
	libandroid_runtime \
    libnl \
    libicuuc

LOCAL_STATIC_LIBRARIES += $(LIB_WIFI_HAL)

LOCAL_SRC_FILES := \
	jni/com_android_server_wifi_WifiNative.cpp \
	jni/jni_helper.cpp

LOCAL_MODULE := libwifi-service

include $(BUILD_SHARED_LIBRARY)

# Build the java code
# ============================================================

include $(CLEAR_VARS)

LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/java
LOCAL_SRC_FILES := $(call all-java-files-under, java) \
	$(call all-Iaidl-files-under, java) \
	$(call all-logtags-files-under, java)

LOCAL_JNI_SHARED_LIBRARIES := libandroid_runtime
LOCAL_JAVA_LIBRARIES := bouncycastle conscrypt services
LOCAL_STATIC_JAVA_LIBRARIES := ksoap2
LOCAL_REQUIRED_MODULES := services
LOCAL_MODULE_TAGS :=
LOCAL_MODULE := wifi-service

LOCAL_JAVA_LIBRARIES += mediatek-framework

include $(BUILD_JAVA_LIBRARY)

ifeq ($(strip $(BUILD_MTK_API_DEP)), yes)
# wifi-service API table.
# ============================================================
LOCAL_MODULE := wifi-service-api

LOCAL_JAVA_LIBRARIES += $(LOCAL_STATIC_JAVA_LIBRARIES) okhttp
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_DROIDDOC_OPTIONS:= \
		-api $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/wifi-service-api.txt \
		-nodocs \
		-hidden

include $(BUILD_DROIDDOC)
endif

endif
