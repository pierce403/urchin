# SDR Setup

Urchin receives four protocols using different tools. Each protocol connects on its own
network port when in bridge mode:

| Protocol | Tool | Default port |
| -------- | ---- | ------------ |
| TPMS / POCSAG | rtl_433 | 1234 |
| ADS-B | dump1090 | 30003 |
| P25 | OP25 | 23456 |

Two capture modes are supported: **Network** (easiest) and **USB** (on-device).

---

## Mode 1: Network bridge (recommended for getting started)

Run rtl_433 on any host reachable from the Android device (laptop, Raspberry Pi, etc.):

```
rtl_433 -F json -S 0 -M level
```

Then in Urchin:

1. Open the filter panel (hamburger icon).
2. Select **Network bridge** as the source.
3. Enter the host IP and port (default `1234`).
4. Tap **Start**.

The simulator at `scripts/sdr-simulator.py` can substitute for real hardware during
development:

```
python3 scripts/sdr-simulator.py --port 1234
adb forward tcp:1234 tcp:1234   # if testing on a physical device
```

---

## Mode 2: USB on-device

Urchin launches `rtl_433` as a subprocess when USB mode is selected. The binary is expected in
the app's native library directory:

- `<nativeLibraryDir>/librtl_433.so`  ← preferred (packaged as NDK library)
- `<nativeLibraryDir>/rtl_433`         ← fallback

The native library directory is under `/data/app/guru.urchin-*/lib/<abi>/` and is not directly
user-writable; the binary must be bundled in the APK.

### Option A: NDK build (advanced)

Build rtl_433 and its dependencies for Android:

**Dependencies:**
- [libusb-android](https://github.com/libusb/libusb) — USB I/O for rtl-sdr
- [librtlsdr](https://github.com/osmocom/rtl-sdr) — RTL-SDR driver
- [rtl_433](https://github.com/merbanan/rtl_433) — protocol decoder

**Steps (arm64-v8a target):**

1. Set up NDK 27+ (already included in the Urchin build):

    ```
    export ANDROID_NDK=$ANDROID_SDK_ROOT/ndk/27.2.12479018
    export TOOLCHAIN=$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64
    export CC=$TOOLCHAIN/bin/aarch64-linux-android24-clang
    export AR=$TOOLCHAIN/bin/llvm-ar
    ```

2. Cross-compile libusb with `--host=aarch64-linux-android`.
3. Cross-compile librtlsdr against the libusb output.
4. Build rtl_433 (cmake) with `CMAKE_TOOLCHAIN_FILE` pointing to the NDK toolchain file.
5. Copy the resulting binary into `app/src/main/jniLibs/arm64-v8a/librtl_433.so`.
6. Add to `app/src/main/cpp/CMakeLists.txt` as a prebuilt shared library
   (see the existing dump1090 section as a reference).

### Option B: Termux-assisted sideload (development/testing only)

This approach works on rooted or developer devices. It does **not** make the binary available
to Urchin's subprocess launcher because Android blocks execution from user-writable paths on
non-rooted devices.

For reference only:

```
# On a device with Termux installed:
pkg install rtl-433
# Binary is at /data/data/com.termux/files/usr/bin/rtl_433
```

---

## P25 / OP25 setup

Urchin connects to an OP25 instance for P25 digital radio metadata. Two modes are supported:

**TCP stream** (default port 23456): OP25 sends newline-delimited JSON with unit IDs, NAC,
WACN, system ID, and talk group data. Configure the host and port in Urchin's settings.

**HTTP polling**: Urchin can poll OP25's JSON status endpoint for active unit lists. This is
used when the OP25 instance exposes an HTTP API instead of a raw TCP stream.

In Urchin, enable the P25 protocol toggle and set the P25 network port in the filter panel.

---

## Multi-dongle configuration

When multiple USB SDR devices are connected, Urchin assigns one dongle per frequency.
For example, with two dongles and three protocols enabled (TPMS + POCSAG + ADS-B), one
dongle handles TPMS while the other handles ADS-B. Remaining frequencies time-share via
the frequency hopper.

P25 over USB requires its own dedicated dongle when running alongside other protocols.

With a single dongle, Urchin uses frequency hopping (default 5-second dwell) to cycle
through all enabled frequencies.

---

## HackRF One notes

- Pass `-d driver=hackrf` to rtl_433 in network mode.
- HackRF One covers 1 MHz–6 GHz, so TPMS (315/433 MHz), POCSAG (929 MHz),
  and ADS-B (1090 MHz) are all in range.
- Single-dongle frequency hopping is handled automatically by Urchin when multiple
  protocols are enabled.

## RTL-SDR notes

- RTL-SDR V3/V4 covers ~500 kHz–1766 MHz (TPMS + POCSAG reachable, ADS-B borderline).
- For ADS-B with RTL-SDR: use a second dongle or enable Network bridge for ADS-B on a
  separate host running dump1090.

---

## Diagnostics

Open **Diagnostics** from the Urchin menu to see:
- Current state (idle / scanning / error)
- Hardware detected
- rtl_433 callback count and last reading timestamp
- Per-state setup guidance

The "Copy diagnostics" button copies the full report to clipboard for bug reports.
