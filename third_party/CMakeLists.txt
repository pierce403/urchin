cmake_minimum_required(VERSION 3.22.1)
project(urchin_third_party C)

set(CMAKE_C_STANDARD 11)

set(LIBUSB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libusb)
set(RTL_SDR_DIR ${CMAKE_CURRENT_SOURCE_DIR}/rtl-sdr)
set(RTL_433_DIR ${CMAKE_CURRENT_SOURCE_DIR}/rtl_433)

add_library(usb1 STATIC
  ${LIBUSB_DIR}/libusb/core.c
  ${LIBUSB_DIR}/libusb/descriptor.c
  ${LIBUSB_DIR}/libusb/hotplug.c
  ${LIBUSB_DIR}/libusb/io.c
  ${LIBUSB_DIR}/libusb/strerror.c
  ${LIBUSB_DIR}/libusb/sync.c
  ${LIBUSB_DIR}/libusb/os/events_posix.c
  ${LIBUSB_DIR}/libusb/os/linux_netlink.c
  ${LIBUSB_DIR}/libusb/os/linux_usbfs.c
  ${LIBUSB_DIR}/libusb/os/threads_posix.c
)

target_include_directories(usb1 PUBLIC
  ${LIBUSB_DIR}/android
  ${LIBUSB_DIR}/libusb
)

target_compile_definitions(usb1 PRIVATE
  HAVE_CONFIG_H=1
  ANDROID_OS
  _GNU_SOURCE
)

set_target_properties(usb1 PROPERTIES POSITION_INDEPENDENT_CODE ON)

target_link_libraries(usb1 PUBLIC log)

add_library(rtlsdr_static STATIC
  ${RTL_SDR_DIR}/src/librtlsdr.c
  ${RTL_SDR_DIR}/src/tuner_e4k.c
  ${RTL_SDR_DIR}/src/tuner_fc0012.c
  ${RTL_SDR_DIR}/src/tuner_fc0013.c
  ${RTL_SDR_DIR}/src/tuner_fc2580.c
  ${RTL_SDR_DIR}/src/tuner_r82xx.c
)

target_include_directories(rtlsdr_static PUBLIC
  ${RTL_SDR_DIR}/include
  ${LIBUSB_DIR}/libusb
  ${LIBUSB_DIR}/android
)

target_compile_definitions(rtlsdr_static PUBLIC rtlsdr_STATIC)

set_target_properties(rtlsdr_static PROPERTIES POSITION_INDEPENDENT_CODE ON)

target_link_libraries(rtlsdr_static PUBLIC usb1)

find_package(PkgConfig QUIET)
set(BUILD_TESTING OFF CACHE BOOL "Disable rtl_433 upstream tests for Android builds" FORCE)
set(ENABLE_OPENSSL OFF CACHE STRING "Disable OpenSSL for Android" FORCE)
set(ENABLE_SOAPYSDR OFF CACHE STRING "Disable SoapySDR for Android" FORCE)
set(ENABLE_RTLSDR ON CACHE STRING "Enable RTL-SDR for Android" FORCE)
set(ENABLE_THREADS ON CACHE STRING "Enable threads for Android" FORCE)
set(LibRTLSDR_INCLUDE_DIR "${RTL_SDR_DIR}/include" CACHE PATH "Bundled librtlsdr includes" FORCE)
set(LibRTLSDR_LIBRARY rtlsdr_static CACHE STRING "Bundled librtlsdr target" FORCE)
set(LibUSB_INCLUDE_DIR "${LIBUSB_DIR}/libusb" CACHE PATH "Bundled libusb includes" FORCE)
set(LibUSB_LIBRARY usb1 CACHE STRING "Bundled libusb target" FORCE)

add_subdirectory(${RTL_433_DIR} ${CMAKE_CURRENT_BINARY_DIR}/rtl_433)

set(rtl433_android_compat_header ${CMAKE_CURRENT_SOURCE_DIR}/rtl433_android_disable_rtltcp.h)
target_compile_options(r_433 PRIVATE -include ${rtl433_android_compat_header})
target_compile_options(rtl_433 PRIVATE -include ${rtl433_android_compat_header})
