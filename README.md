# Urchin

Urchin is an Android SDR app for local RF reconnaissance. It captures and displays observations from four radio protocols — TPMS, POCSAG, ADS-B, and P25 — using either:

- A USB-attached RTL-SDR dongle
- Network bridges streaming newline-delimited JSON over TCP

The app stores observations locally, shows live and historical sensor sightings, exposes raw JSON for export, and uses a yellow-on-black UI theme.

The project landing page is intended for `https://urchin.guru/`.

## Scope

- Multi-protocol SDR capture (TPMS, POCSAG, ADS-B, P25)
- Protocol-specific observation parsing with per-protocol display and retention
- Local Room database for sensors and sightings with composite indices
- Multi-dongle assignment and single-dongle frequency hopping
- Protocol filter chips, search, sort, live-only, starred, and battery-low filters
- Sensor detail view with rename, copy, save, and share
- Diagnostics screen with runtime state and recent log output
- Static `index.html` landing page describing the project

## Build

- JDK 17
- Android SDK with API 35
- `./scripts/setup-third-party.sh` before `./gradlew assembleDebug` when you need USB/on-device SDR binaries; it clones the SDR deps and reapplies Urchin's tracked Android patches
- `./gradlew assembleDebug`
- `./gradlew installDebug`
- `./gradlew testDebugUnitTest`

If the SDK path is not already configured, this repo uses `local.properties` with:

```properties
sdk.dir=/home/pierce/Android/Sdk
```

## APK

The current debug build can be staged from:

- `app/build/outputs/apk/debug/app-debug.apk`

The site download is intended to point at:

- `downloads/urchin-v0.2.3-debug.apk`

## Emulator setup

See [docs/EMULATOR_SETUP.md](docs/EMULATOR_SETUP.md) for creating an AVD, launching the emulator, and connecting SDR dongles via network bridges.

## SDR notes

- USB mode auto-detects supported hardware by VID/PID. RTL-SDR dongles are supported for on-device capture; HackRF currently uses Network bridge mode in this APK.
- USB mode now bundles `rtl_433` as an APK asset, extracts it into app-private storage on first launch, and relays the Android UsbManager file descriptor into the subprocess so unrooted RTL-SDR USB capture can start.
- Diagnostics includes the live USB inventory with VID/PID/permission state plus the packaged native-tool paths for `rtl_433`, `dump1090`, and `p25_scanner`.
- When multiple USB SDR devices are connected, Urchin assigns one dongle per frequency. With a single dongle, it uses frequency hopping.
- Network mode connects to per-protocol bridges on configurable ports. A Raspberry Pi running [sdr-pi](https://github.com/ingmarvg/sdr-pi) can host your SDR dongles and stream observation data to the app over TCP:

| Protocol | Tool | Default port |
| -------- | ---- | ------------ |
| TPMS/POCSAG | rtl_433 | 1234 |
| ADS-B | dump1090 | 30003 |
| P25 | OP25 | 23456 |

- Frequency presets include `315 MHz` and `433.92 MHz` (TPMS), `929.6125 MHz` (POCSAG), `1090 MHz` (ADS-B), and `851 MHz` (P25).
- Gain is optional; leaving it blank keeps automatic gain handling.

## Privacy

- All observations stay on-device by default.
- No cloud sync is included.
- Different protocol types have different retention periods (7–30 days).
