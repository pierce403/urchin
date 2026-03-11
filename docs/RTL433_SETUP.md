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
rtl_433 -f 433.92M -F json:tcp:0.0.0.0:1234 -S 0 -M level
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

Urchin launches `rtl_433` as a subprocess when USB mode is selected. The APK now bundles an
ABI-specific executable asset at:

- `assets/sdr-bin/<abi>/rtl_433`

On first launch in USB mode, `Rtl433BinaryInstaller` extracts that asset into app-private
storage and marks it executable:

- `<noBackupFilesDir>/sdr-bin/rtl_433-v<versionCode>-<abi>`

Diagnostics reports both the packaged asset path and the extracted on-device path when present.

Before building an APK that should support USB mode locally, populate the SDR sources with:

```
./scripts/setup-third-party.sh
```

### Option A: Built into the app (recommended)

Build `rtl_433` and its dependencies for Android through the repo's pinned Gradle/CMake path:

**Dependencies:**
- [libusb-android](https://github.com/libusb/libusb) — USB I/O for rtl-sdr
- [librtlsdr](https://github.com/osmocom/rtl-sdr) — RTL-SDR driver
- [rtl_433](https://github.com/merbanan/rtl_433) — protocol decoder

**Steps:**

1. Set up NDK 27+ (already pinned in the Urchin build):

    ```
    export ANDROID_NDK=$ANDROID_SDK_ROOT/ndk/27.2.12479018
    export TOOLCHAIN=$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64
    export CC=$TOOLCHAIN/bin/aarch64-linux-android24-clang
    export AR=$TOOLCHAIN/bin/llvm-ar
    ```

2. Populate the upstream sources:

    ```
    ./scripts/setup-third-party.sh
    ```

3. Build the app:

    ```
    ./gradlew assembleDebug
    ```

4. Gradle will:
   - build `rtl_433` for `arm64-v8a` and `x86_64`
   - copy the executables into `build/generated/rtl433Assets/<variant>/sdr-bin/<abi>/rtl_433`
   - package them into the APK under `assets/sdr-bin/<abi>/rtl_433`

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
- Live USB inventory with VID/PID and permission state, including unsupported devices
- Native-tool packaging status for `rtl_433`, `dump1090`, and `p25_scanner`, including packaged asset paths for `rtl_433`
- rtl_433 callback count and last reading timestamp
- Per-state setup guidance

The "Copy diagnostics" button copies the full report to clipboard for bug reports.
