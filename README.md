# Urchin

Urchin is an Android SDR app for local RF reconnaissance. It captures and displays observations from five radio protocols — TPMS, POCSAG, ADS-B, UAT, and P25 — using either:

- A USB-attached RTL-SDR dongle
- Network bridges streaming protocol-specific TCP output over the local network

All data stays on-device. Observations are stored in an encrypted SQLCipher database and can be shared between trusted devices via encrypted affinity group bundles.

The project landing page is at `https://urchin.guru/`.

## Features

### Multi-protocol SDR capture

| Protocol | Frequency | Tool | Default network port |
| -------- | --------- | ---- | -------------------- |
| TPMS | 315 / 433.92 MHz | rtl_433 | 1234 |
| POCSAG | 929.6125 MHz | rtl_433 | 1234 |
| ADS-B | 1090 MHz | dump1090 | 30003 |
| UAT | 978 MHz | dump978 | 30978 |
| P25 | 136–800 MHz | p25_scanner / OP25 | 23456 |

- USB mode auto-detects supported hardware by VID/PID (RTL2832U dongles and HackRF One)
- When multiple USB SDR devices are connected, Urchin assigns one dongle per frequency; with a single dongle, it uses frequency hopping
- Network mode connects to per-protocol bridges on configurable ports
- A Raspberry Pi running [sdr-pi](https://github.com/ingmarvg/sdr-pi) can host your SDR dongles and stream observation data to the app over TCP
- Gain is optional; leaving it blank keeps automatic gain handling

### Observation management

- Protocol-specific observation parsing with per-protocol display and retention (7–30 days)
- Protocol filter chips, text search, sort, live-only, starred, and battery-low filters
- Sensor detail view with rename, copy JSON, save, and share
- Diagnostics screen with runtime state and recent log output

### Alert system

- Configurable alert rules that trigger audio and notification alerts when matching devices are observed
- Match by device name, model, sensor ID, ICAO hex, CAP code, unit ID, or protocol

### Affinity groups

- Encrypted team-based observation sharing via `.urchin` bundle files
- AES-256-GCM encryption with shared group keys, ECDH P-256 key agreement, and HKDF-SHA256 key derivation
- Create groups, invite members, export/import bundles, revoke members with automatic key rotation
- No server required — bundles are exchanged as files

### Continuous scanning

- Background foreground service that keeps the SDR capture running when the app is not in the foreground
- Auto-restart on boot (opt-in)
- Exponential backoff retry on errors
- Persistent notification showing current SDR state

### Database encryption

- SQLCipher with AES-256 encryption
- Database key wrapped with Android Keystore
- Automatic one-time migration from plaintext to encrypted database

## Raspberry Pi setup (sdr-pi)

Instead of plugging SDR dongles directly into your phone, you can offload capture to a Raspberry Pi running [sdr-pi](https://github.com/ingmarvg/sdr-pi). The Pi creates a Wi-Fi access point, runs per-protocol decoders, and streams observations to Urchin over TCP.

### Hardware

- Raspberry Pi 3B+, 4, or 5
- One or more RTL-SDR Blog V3/V4 dongles
- Powered USB hub (recommended when using multiple dongles)

### Install

Option A — build a custom image on a Debian/Ubuntu/WSL2 host with Docker:

```bash
git clone https://github.com/ingmarvg/sdr-pi.git
cd sdr-pi
./scripts/build-image.sh   # ~30-60 min first build
```

Option B — install on an existing Raspberry Pi OS:

```bash
git clone https://github.com/ingmarvg/sdr-pi.git
cd sdr-pi
sudo ./scripts/install.sh
```

### Network defaults

| Setting | Value |
| ------- | ----- |
| SSID | `sdr-pi` |
| Password | `sdr-pi-pass` |
| Pi IP | `192.168.4.1` |
| SSH | `ssh sdr@192.168.4.1` |

### Configuration

Edit `/etc/sdr-pi/sdr-pi.conf` to choose protocols, frequencies, gain, and dongle assignment:

```bash
ENABLED_PROTOCOLS="rtl433 dump1090 op25"
RTL433_FREQUENCY="315M"
RTL433_GAIN="auto"
DUMP1090_GAIN="max"
OP25_FREQUENCY="851000000"

# Pin decoders to specific dongles by serial (run rtl_test to list)
RTL433_DEVICE_SERIAL="00000001"
DUMP1090_DEVICE_SERIAL="00000002"
```

Apply changes:

```bash
sudo sdr-pi-apply-config && sudo systemctl restart sdr-pi-*
```

### Connecting Urchin to the Pi

1. Connect your Android device to the `sdr-pi` Wi-Fi network
2. In Urchin, set the SDR source to **Network**
3. Set the host to `192.168.4.1`
4. Enable the protocols you want — each uses its own TCP port (see table below)
5. Start scanning — Urchin connects to each enabled port and displays observations in real time

| Protocol | Port |
| -------- | ---- |
| TPMS / POCSAG (rtl_433) | 1234 |
| ADS-B (dump1090) | 30003 |
| UAT (dump978) | 30978 |
| P25 (OP25) | 23456 |

### Multi-dongle setup

With multiple RTL-SDR dongles attached to the Pi, each decoder runs on its own dongle simultaneously — no frequency hopping needed. Assign dongles by serial in the config file so each decoder always gets the same hardware. Use `rtl_test` on the Pi to list connected devices and their serials.

## Build

- JDK 17
- Android SDK with API 35
- `./scripts/setup-third-party.sh` before `./gradlew assembleDebug` when you need USB/on-device SDR binaries; it clones the SDR deps and reapplies Urchin's tracked Android patches

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew testDebugUnitTest
```

If the SDK path is not already configured, this repo uses `local.properties` with:

```properties
sdk.dir=/home/pierce/Android/Sdk
```

## APK

The current debug build can be staged from:

- `app/build/outputs/apk/debug/app-debug.apk`

The site download is intended to point at:

- `downloads/urchin-v0.2.8-debug.apk`

## Emulator setup

See [docs/EMULATOR_SETUP.md](docs/EMULATOR_SETUP.md) for creating an AVD, launching the emulator, and connecting SDR dongles via network bridges.

## Testing

- USB mode auto-detects supported hardware by VID/PID. RTL-SDR dongles are supported for on-device capture; HackRF currently uses Network bridge mode in this APK.
- USB mode now packages `rtl_433`, `dump1090`, and `p25_scanner` as native executables under the APK's extracted `lib/<abi>/` directory and relays the Android UsbManager file descriptor into the subprocess so unrooted RTL-SDR USB capture can start.
- On-device RTL-SDR launch uses the wrapped Android USB fd plus a plain RTL-SDR device index; do not switch it to `driver=rtlsdr` unless SoapySDR support is added to the bundled build.
- Android closes non-stdio fds during `ProcessBuilder` spawn, so the current relay maps the granted USB descriptor onto child stdin and points `URCHIN_RTLSDR_FD` at fd `0`.
- Diagnostics includes the live USB inventory with VID/PID/permission state plus the packaged native-tool paths for `rtl_433`, `dump1090`, and `p25_scanner`.
- USB ADS-B uses bundled `dump1090` on a private loopback SBS stream (`127.0.0.1:30003`) once the local process is ready.
- TPMS and POCSAG share the `rtl_433` pool. With a single dongle, only those `rtl_433` frequencies are hopped; ADS-B and P25 each need their own USB dongle when enabled alongside other protocols.
- Network mode connects to per-protocol bridges on configurable ports. A Raspberry Pi running [sdr-pi](https://github.com/ingmarvg/sdr-pi) can host your SDR dongles and stream observation data to the app over TCP:

| Protocol | Tool | Default port |
| -------- | ---- | ------------ |
| TPMS/POCSAG | rtl_433 | 1234 |
| ADS-B | dump1090 or readsb (SBS/BaseStation) | 30003 |
| UAT | dump978 | 30978 |
| P25 | OP25 | 23456 |

- ADS-B network mode accepts either stock `dump1090`/`readsb` SBS lines on `30003` or the repo's JSON-based simulator output for development.
- Frequency presets include `315 MHz` and `433.92 MHz` (TPMS), `929.6125 MHz` (POCSAG), `1090 MHz` (ADS-B), `978 MHz` (UAT), and `851 MHz` (P25).
- Gain is optional; leaving it blank keeps automatic gain handling.

A multi-protocol simulator is included for development and testing:

```bash
python scripts/sdr-simulator.py
```

It emits simulated TPMS, POCSAG, ADS-B, UAT, and P25 data on configurable TCP ports. Use `--protocols` to select specific protocols, `--burst` for stress testing, and `--help` for all options.

## Privacy

- All observations stay on-device by default
- Database is encrypted with SQLCipher (AES-256)
- Affinity group bundles use end-to-end encryption
- No cloud sync, no telemetry
