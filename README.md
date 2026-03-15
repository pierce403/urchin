# Urchin

Urchin is an Android SDR app for local RF reconnaissance. It captures and displays observations from twelve radio protocols — TPMS, POCSAG, ADS-B, UAT, P25, LoRaWAN, Meshtastic, Wireless M-Bus, Z-Wave, Amazon Sidewalk, DMR, and NXDN — using either:

- A USB-attached RTL-SDR dongle
- Network bridges streaming protocol-specific TCP output over the local network

All data stays on-device. Observations are stored in an encrypted SQLCipher database and can be shared between trusted devices via encrypted affinity group bundles.

The project landing page is at `https://urchin.guru/`.

## Features

### Multi-protocol SDR capture

#### RTL-SDR protocols (USB or network bridge)

| Protocol | Frequency | Tool | Default port |
| -------- | --------- | ---- | ------------ |
| TPMS | 315 / 433.92 MHz | rtl_433 | 1234 |
| POCSAG | 929.6125 MHz | rtl_433 | 1234 |
| ADS-B | 1090 MHz | dump1090 | 30003 |
| UAT | 978 MHz | dump978 | 30978 |
| P25 | 136–800 MHz | p25_scanner / OP25 | 23456 |

#### RAK2243 HAT protocols (network bridge only)

| Protocol | Frequency | Tool | Default port |
| -------- | --------- | ---- | ------------ |
| LoRaWAN | 902–928 MHz (US) / 863–870 MHz (EU) | lora_pkt_fwd | 1680 |
| Meshtastic | 902–928 MHz (US) / 863–870 MHz (EU) | lora_pkt_fwd | 1680 |
| Wireless M-Bus | 868.95 MHz (EU only) | wmbus_json_bridge | 1681 |
| Z-Wave | 908.42 MHz (US only) | zwave_json_bridge | 1682 |
| Amazon Sidewalk | 900 MHz (US) | sidewalk_json_bridge | 1683 |
| DMR | 400 / 900 MHz | dmr_json_bridge | 1684 |
| NXDN | 700 / 800 / 900 MHz | nxdn_json_bridge | 1685 |

- USB mode auto-detects supported hardware by VID/PID (RTL2832U dongles and HackRF One)
- When multiple USB SDR devices are connected, Urchin assigns one dongle per frequency; with a single dongle, it uses frequency hopping
- Network mode connects to per-protocol bridges on configurable ports
- A Raspberry Pi running [sdr-pi](https://github.com/ingmarvg/sdr-pi) can host your SDR dongles and stream observation data to the app over TCP
- Gain is optional; leaving it blank keeps automatic gain handling

### Receiver geolocation

- Every observation is GPS-stamped with the receiver's position (latitude, longitude, altitude, accuracy)
- Uses the platform LocationManager — no Google Play Services dependency, works on de-Googled / hardened devices
- ADS-B targets with known positions get computed range (km) and bearing (degrees) from receiver
- Location data is persisted in the sightings table for spatial analysis

### Bulk data export

- Export the full device and sighting database in CSV, KML, or GeoJSON format
- CSV includes all protocol fields for spreadsheet/database analysis
- KML places geolocated sightings as Placemarks with protocol-typed icons for Google Earth
- GeoJSON FeatureCollection for GIS toolchains
- ADS-B aircraft positions exported with altitude and ICAO annotations
- Available from the main menu under **Export all data**

### Cross-protocol correlation

- Automatically identifies emitters across different protocols that consistently co-occur (within a 30-second window)
- Detects co-traveling or co-located devices — e.g., a vehicle's TPMS sensors appearing alongside a Meshtastic node
- Confidence scores based on co-occurrence frequency relative to total sightings
- Correlated emitters displayed as "Related emitters" in the device detail view
- Runs every 15 minutes in the background

### Activity timeline

- 24-hour activity histograms per protocol for pattern-of-life analysis
- Reveals commute patterns, shift changes, operational schedules
- Global all-protocol overview plus per-protocol breakdowns
- Available from the main menu under **Activity timeline**

### Anomaly detection

- Automatically flags unusual RF activity against rolling baselines
- Detects new emitter surges (N new devices in T minutes above baseline)
- Detects RSSI anomalies (familiar device at unexpected signal strength — moved or new transmitter)
- Results logged to diagnostics

### Electronic Order of Battle (EOB)

- Generates a structured inventory of all observed RF emitters organized by protocol
- Per-protocol summaries: unique emitter counts, RSSI statistics, temporal density
- Top emitters ranked by observation count
- Correlated device clusters included
- Export as JSON or plain text from the main menu under **EOB report**

### Deep packet inspection

- Extended protocol parsing extracts operational content beyond identity:
  - **P25**: encryption algorithm/key ID, emergency flag, voice/data type
  - **LoRaWAN**: FPort (application identifier), frame counter, message type
  - **Meshtastic**: port number, unencrypted payload text
  - **Z-Wave**: command class, node role, security level

### IQ recording

- Record raw IQ samples to file for offline analysis or forensic evidence
- Pre-trigger circular buffer (2 MB, ~0.5 seconds at 2 MS/s) captures signal before manual trigger
- Auto-stop after configurable duration (default 30 seconds)
- Metadata per recording: center frequency, sample rate, gain, timestamp, trigger reason

### Spectrum waterfall display

- Real-time waterfall/spectrogram visualization with scrolling heatmap
- Magnitude-to-color mapping (dark blue = noise floor, red/white = strong signal)
- Frequency axis labels for tuned bandwidth

### Direction finding

- RSSI-based bearing estimation using phone compass/magnetometer
- Polar compass rose display correlating signal strength with receiver heading
- Weighted circular mean computes peak-signal bearing
- Best used with directional antenna (Yagi/log-periodic)

### OPSEC hardening

- **Panic wipe**: destroys the Keystore encryption key, clears all database tables, overwrites database files with random bytes, and clears SharedPreferences. Triggered via broadcast intent or widget
- **Secure key destruction**: deletes the Android Keystore master key so the database passphrase cannot be recovered

### Observation management

- Protocol-specific observation parsing with per-protocol display and retention (7–30 days)
- Protocol filter chips, text search, sort, live-only, starred, and battery-low filters
- Sensor detail view with rename, copy JSON, save, and share
- Diagnostics screen with runtime state and recent log output

### Alert system

- Configurable alert rules that trigger audio and notification alerts when matching devices are observed
- Match by device name, model, sensor ID, ICAO hex, CAP code, unit ID, or protocol
- **Proximity alerts**: fire when RSSI exceeds a threshold (signal stronger than -N dBm = target is close)
- **New device alerts**: fire on first-ever observation of a device on a given protocol
- **Absence alerts**: fire when a device has not been seen for N minutes (target departed)

### Affinity groups

- Encrypted team-based observation sharing via `.urchin` bundle files
- AES-256-GCM encryption with shared group keys, ECDH P-256 key agreement, and HKDF-SHA256 key derivation
- Create groups, invite members, export/import bundles, revoke members with automatic key rotation
- No server required — bundles are exchanged as files
- Real-time streaming mode: live encrypted observation sharing over TCP relay using the group's AES-256-GCM key

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
| LoRaWAN / Meshtastic (lora_pkt_fwd) | 1680 |
| Wireless M-Bus (wmbus_json_bridge) | 1681 |
| Z-Wave (zwave_json_bridge) | 1682 |
| Amazon Sidewalk (sidewalk_json_bridge) | 1683 |

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
| LoRaWAN/Meshtastic | lora_pkt_fwd | 1680 |
| Wireless M-Bus | wmbus_json_bridge | 1681 |
| Z-Wave | zwave_json_bridge | 1682 |
| Amazon Sidewalk | sidewalk_json_bridge | 1683 |

- ADS-B network mode accepts either stock `dump1090`/`readsb` SBS lines on `30003` or the repo's JSON-based simulator output for development.
- Frequency presets include `315 MHz` and `433.92 MHz` (TPMS), `929.6125 MHz` (POCSAG), `1090 MHz` (ADS-B), `978 MHz` (UAT), and `851 MHz` (P25).
- Gain is optional; leaving it blank keeps automatic gain handling.

A multi-protocol simulator is included for development and testing:

```bash
python scripts/sdr-simulator.py
```

It emits simulated TPMS, POCSAG, ADS-B, UAT, P25, LoRaWAN, Meshtastic, Wireless M-Bus, Z-Wave, and Amazon Sidewalk data on configurable TCP ports. Use `--protocols` to select specific protocols, `--burst` for stress testing, and `--help` for all options.

## SIGINT feature setup and verification

### Setting up receiver geolocation

Grant `ACCESS_FINE_LOCATION` permission when prompted (or via app settings). GPS starts automatically when the app launches.

Start a scan, observe a few devices, then check a device's detail view — the raw JSON metadata should include `receiverLat` and `receiverLon` fields. For ADS-B targets with known positions, `adsbRangeKm` and `adsbBearingDeg` fields will also appear.

### Using bulk export

No additional setup. Available from the overflow menu.

Menu > **Export all data** > select CSV, KML, or GeoJSON. The file is saved to the Downloads folder. Open the CSV in a spreadsheet to confirm all protocol fields are populated. Import the KML into Google Earth to see geolocated sightings as placemarks.

### Verifying cross-protocol correlation

Runs automatically every 15 minutes when the app is active. Requires observations across at least two different protocols.

Use `python scripts/sdr-simulator.py` with multiple protocols to generate co-timed observations. After 15 minutes, open a device's detail view — if correlations were found, a "Related emitters" section appears showing the correlated devices with confidence percentages.

### Using the activity timeline

Menu > **Activity timeline**. Histograms show 24-hour activity patterns per protocol. Bars represent relative observation counts per hour.

### Configuring enhanced alerts

Menu > **Alerts** > tap + to create a new rule. Select "Proximity (RSSI)" or "New device" as the match type.

- **Proximity**: Set an RSSI threshold (e.g., -50). Approach the target transmitter — alert fires when signal exceeds the threshold.
- **New device**: Set a protocol filter. Alert fires on the first observation of any previously-unseen device on that protocol.

### Generating an EOB report

Menu > **EOB report** > select JSON or Text. The report is saved to Downloads. Open it to see per-protocol emitter inventories, RSSI statistics, and correlated clusters.

### Using direction finding

The `DirectionFinderView` compass rose view is available programmatically. Feed heading-RSSI pairs from the phone's magnetometer while rotating with a directional antenna. The view shows a polar plot with the estimated peak-signal bearing.

### Triggering panic wipe

Send the broadcast intent `guru.urchin.action.PANIC_WIPE` to the app. This destroys the database encryption key, clears all tables, overwrites the database file with random data, and clears preferences. **Warning**: this is irreversible.

```bash
adb shell am broadcast -a guru.urchin.action.PANIC_WIPE -p guru.urchin
```

### Setting up DMR and NXDN

Enable DMR or NXDN in the protocol toggles. In network mode, configure the bridge host and ports (default 1684 for DMR, 1685 for NXDN). Requires a compatible decoder on the bridge side (e.g., a DMR decoder producing JSON with `type`, `radio_id`, `color_code`, `slot`, `talkgroup` fields).

Use the simulator or a real DMR/NXDN bridge. Devices should appear in the list with protocol chips "DMR" or "NXDN".

## Privacy

- All observations stay on-device by default
- Database is encrypted with SQLCipher (AES-256)
- Affinity group bundles use end-to-end encryption
- No cloud sync, no telemetry
