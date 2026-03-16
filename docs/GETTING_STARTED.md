# Getting Started with Urchin

This guide walks through installing, configuring, and using Urchin's RF reconnaissance
and SIGINT analysis features without consulting the source code.

## Installation

### From source

```bash
git clone https://github.com/pierce403/urchin.git
cd urchin
./scripts/setup-third-party.sh   # clones SDR deps, applies patches
./gradlew assembleDebug
./gradlew installDebug           # install to connected device or emulator
```

### From APK

Install `app/build/outputs/apk/debug/app-debug.apk` via `adb install` or sideload.

## First launch

1. Open Urchin. The main screen shows the device list (empty initially) with protocol filter chips at the top.
2. Tap the filter icon (top-left) to open the **Capture Control** drawer.
3. Choose a source:
   - **USB dongle**: plug an RTL-SDR or HackRF into your phone via USB OTG
   - **Network bridge**: enter the IP and port of a host running SDR decoders (e.g., a Raspberry Pi with [sdr-pi](https://github.com/ingmarvg/sdr-pi), or the included simulator)
4. Enable the protocols you want to capture.
5. Close the drawer, then tap **START** in the toolbar.

## Using the simulator (no hardware needed)

```bash
python scripts/sdr-simulator.py
```

This starts TCP servers on the default ports, emitting TPMS, POCSAG, ADS-B, P25, LoRaWAN,
Meshtastic, Wireless M-Bus, Z-Wave, and Amazon Sidewalk data. Use `--burst` for rapid-fire mode.

If testing with an emulator, set up port forwarding first:

```bash
adb reverse tcp:1234 tcp:1234
adb reverse tcp:30003 tcp:30003
adb reverse tcp:1680 tcp:1680
adb reverse tcp:1681 tcp:1681
adb reverse tcp:1682 tcp:1682
adb reverse tcp:1683 tcp:1683
```

Then in Urchin, set source to **Network bridge**, host `127.0.0.1`, port `1234`.

## Scanning

- **START/STOP**: tap the toolbar button to begin or end a scan session
- **Continuous scanning**: enable from the overflow menu to keep scanning in the background with auto-restart on errors and boot
- **Live sensor count**: shown in the toolbar subtitle during active scanning

## Viewing devices

The main list shows all observed devices with:

- Protocol icon and name
- Last-seen sensor data (pressure, message, callsign, etc.)
- RSSI strength
- Sighting count
- Star toggle for bookmarking

Tap a device card to open the **detail view** showing:

- Sensor identity and classification
- First/last seen timestamps
- RSSI statistics (min/max/average)
- Latest parsed reading
- Raw JSON from the decoder
- **Related emitters** — cross-protocol correlations (if any have been found)
- Sighting history

## Filtering and search

- **Protocol chips**: tap TPMS, Pager, Aircraft, etc. to filter by protocol
- **Search**: type in the filter field to search by name, model, or sensor ID
- **Live only**: toggle to show only devices seen in the current scan session
- **Starred only**: show only bookmarked devices
- **Battery low only**: show only devices reporting low battery
- **Sort**: Recent, Oldest, RSSI High, RSSI Low

## GPS geostamping

When location permission is granted, every observation is automatically tagged with the
receiver's GPS position. No manual setup needed — the location provider starts on app launch.

To verify: open a device's detail view and check the raw JSON metadata for `receiverLat`,
`receiverLon`, `receiverAltitude`, and `receiverAccuracy` fields. ADS-B targets with
known positions will also show `adsbRangeKm` and `adsbBearingDeg`.

## Exporting data

### Bulk export

Menu > **Export all data** > choose a format:

| Format | Use case |
| ------ | -------- |
| CSV | Spreadsheets, databases, Python/R analysis |
| KML | Google Earth visualization with protocol-typed placemarks |
| GeoJSON | GIS toolchains (QGIS, ArcGIS, Mapbox) |

Files are saved to the device's Downloads folder.

### EOB report

Menu > **EOB report** > choose JSON or Text. Generates an Electronic Order of Battle
inventory listing all observed RF emitters by protocol, with RSSI statistics, temporal
density, top emitters, and correlated device clusters.

### Single device export

In a device's detail view, use the toolbar icons to:

- Copy JSON to clipboard
- Save JSON to Downloads
- Share JSON via any app

## Activity timeline

Menu > **Activity timeline** opens a full-screen view with 24-hour activity histograms:

- **All Protocols** histogram at the top
- Per-protocol breakdowns below (TPMS, POCSAG, ADS-B, etc.)
- Each bar represents one hour; bar height shows relative observation count
- Labels at 00h, 06h, 12h, 18h

Use this to identify commute patterns, shift changes, and operational schedules.

## Cross-protocol correlation

Runs automatically every 15 minutes in the background. When two devices from different
protocols consistently appear within 30 seconds of each other, Urchin creates a
correlation record with a confidence score.

View correlations in any device's detail view under the **Related emitters** section.
This shows co-located or co-traveling devices — for example, a vehicle's TPMS sensors
appearing alongside a Meshtastic node carried by the driver.

## Anomaly detection

Runs alongside the correlation engine every 15 minutes. Flags:

- **New emitter surges**: more new devices than the rolling baseline predicts
- **RSSI anomalies**: a familiar device at an unexpectedly strong signal (moved closer or new transmitter)

Results are logged to the Diagnostics screen.

## Alerts

Menu > **Alerts** to manage alert rules. Supported rule types:

| Type | Matches on | Example |
| ---- | ---------- | ------- |
| Name | Device name substring | "Schrader" matches any Schrader TPMS |
| Sensor ID | Exact sensor/ICAO/CAP code | "AC1234" matches specific ICAO |
| Protocol | Protocol type | "adsb" alerts on any aircraft |
| Proximity (RSSI) | Signal strength threshold | "-50" fires when signal exceeds -50 dBm |
| New device | First-ever observation | "tpms" fires on any new TPMS sensor |
| Absence | Device not seen for N minutes | "5" fires after 5 minutes of silence |

Each rule has a configurable emoji icon and sound preset (Ping, Chime, Alarm).

## Affinity groups (team sharing)

Share observations with team members using encrypted bundles:

1. Menu > **Groups** > **Create group** — name your group and set a display name
2. Share the group key (clipboard) with team members
3. Members join by pasting the key
4. **Export bundle**: creates an encrypted `.urchin` file containing devices, sightings, and alert rules
5. **Import bundle**: team members import the file to merge observations into their database
6. All data is encrypted with AES-256-GCM; group key is wrapped with Android Keystore

Real-time streaming is also available for live observation sharing over TCP using the group key.

## OPSEC features

### Panic wipe

Destroys all data irrecoverably:

```bash
adb shell am broadcast -a guru.urchin.action.PANIC_WIPE -p guru.urchin
```

This destroys the Android Keystore encryption key, clears all database tables, overwrites
database files with random bytes, and clears SharedPreferences. **Cannot be undone.**

### Database encryption

All observations are stored in a SQLCipher-encrypted database (AES-256). The passphrase is
wrapped with the Android Keystore and never stored in plaintext.

## Diagnostics

Menu > **Diagnostics** shows:

- Current SDR state (Idle, Scanning, Error)
- Hardware label and USB inventory
- Callback counters
- Live debug log output

## Protocol details

See [PROTOCOLS.md](PROTOCOLS.md) for the full protocol reference including frequencies,
captured fields, device key formats, and retention periods.

## Emulator testing

See [EMULATOR_SETUP.md](EMULATOR_SETUP.md) for creating AVDs and connecting SDR data
via network bridges.
