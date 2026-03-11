cmake_minimum_required(VERSION 3.22.1)
project(urchin_native C)

set(CMAKE_C_STANDARD 11)

# --- Third-party SDR libraries (libusb, librtlsdr, rtl_433) ---
set(THIRD_PARTY_DIR ${CMAKE_SOURCE_DIR}/../../../../third_party)
get_filename_component(THIRD_PARTY_DIR ${THIRD_PARTY_DIR} ABSOLUTE)
add_subdirectory(${THIRD_PARTY_DIR} ${CMAKE_CURRENT_BINARY_DIR}/third_party)

# ABI-specific DSP flavors and starch mix define
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(DSP_FLAVOR_SOURCES
        dsp/generated/flavor.armv8_a.c
        dsp/generated/flavor.armv8_neon_simd.c
    )
    set(STARCH_MIX STARCH_MIX_AARCH64)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(DSP_FLAVOR_SOURCES
        dsp/generated/flavor.x86_avx2.c
    )
    set(STARCH_MIX STARCH_MIX_X86)
else()
    set(DSP_FLAVOR_SOURCES "")
    set(STARCH_MIX STARCH_MIX_GENERIC)
endif()

add_executable(dump1090
    # Core decoder
    adaptive.c
    ais_charset.c
    anet.c
    comm_b.c
    convert.c
    cpr.c
    cpu.c
    crc.c
    demod_2400.c
    dump1090.c
    fifo.c
    icao_filter.c
    interactive_stub.c
    mode_ac.c
    mode_s.c
    net_io.c
    sdr.c
    sdr_ifile.c
    stats.c
    track.c
    util.c
    # DSP lookup tables
    dsp/helpers/tables.c
    # DSP dispatcher + generic flavor (always)
    dsp/generated/dispatcher.c
    dsp/generated/flavor.generic.c
    # ABI-specific DSP flavors
    ${DSP_FLAVOR_SOURCES}
)

target_include_directories(dump1090 PRIVATE
    ${CMAKE_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/compat
    ${CMAKE_SOURCE_DIR}/dsp/generated
    ${CMAKE_SOURCE_DIR}/dsp/helpers
    ${CMAKE_SOURCE_DIR}/dsp/impl
)

target_compile_definitions(dump1090 PRIVATE
    # Starch CPU mix (defines STARCH_MIX_ALIGNMENT)
    ${STARCH_MIX}=1
    # Do NOT define ENABLE_RTLSDR/BLADERF/HACKRF/LIMESDR — leave undefined
    # so #ifdef guards in sdr.c exclude them
    _GNU_SOURCE
    ANDROID
    MODES_DUMP1090_VARIANT=\"urchin\"
)

target_link_libraries(dump1090 log m)

# --- P25 metadata scanner ---
add_executable(p25_scanner
    p25/p25_scanner.c
    p25/c4fm_demod.c
    p25/p25_frame.c
    p25/p25_fec.c
    p25/json_out.c
)

target_include_directories(p25_scanner PRIVATE
    ${CMAKE_SOURCE_DIR}/p25
    ${THIRD_PARTY_DIR}/rtl-sdr/include
    ${THIRD_PARTY_DIR}/libusb/libusb
)

target_compile_definitions(p25_scanner PRIVATE
    ANDROID
    ENABLE_RTLSDR
)

target_link_libraries(p25_scanner rtlsdr_static usb1 log m)
