cmake_minimum_required(VERSION 3.22.1)
project(urchin_third_party C)

set(CMAKE_C_STANDARD 11)

set(LIBUSB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libusb)
set(RTL_SDR_DIR ${CMAKE_CURRENT_SOURCE_DIR}/rtl-sdr)

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
