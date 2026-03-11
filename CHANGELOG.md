# Changelog

## 0.2.0 - 2026-03-09

- Added POCSAG, ADS-B, and P25 protocol support alongside original TPMS capture.
- Added `SdrReading` sealed class and `ObservationBuilderRegistry` for protocol dispatch.
- Added `TcpStreamBridge` generic TCP client replacing per-protocol bridge classes.
- Added `SdrChannel` abstraction and `FrequencyHopper` for multi-dongle and single-dongle modes.
- Added `Dump1090Process`/`AdsbJsonParser` for ADS-B and `P25NetworkBridge` for P25/OP25.
- Added per-protocol network bridge ports (rtl_433: 1234, dump1090: 30003, OP25: 23456).
- Added per-protocol data retention (ADS-B 7 days, P25 14 days, TPMS/POCSAG 30 days).
- Added database migration v2 (protocolType column) and v3 (composite indices).
- Added protocol filter chips and per-protocol icons in the device list.
- Renamed `TpmsMetadata` to `SensorMetadata` with multi-protocol presentation builder.
- Consolidated `MainViewModel` flow chain into single `FilterState` + `combine`.
- Cached `DateTimeFormatter` and optimized `DeviceKey` SHA-256 hex encoding.
- Deduplicated JSON extension functions into shared `JsonExtensions.kt`.
- Integrated NDK cross-compilation of dump1090 and p25_scanner.

## 0.1.1 - 2026-03-10

- Renamed the project branding from Spider to Urchin across the Android app, docs, and website.
- Changed the Android namespace and application ID from `ninja.spider` to `guru.urchin`.
- Switched the GitHub Pages/domain metadata and staged APK naming over to `urchin.guru`.

## 0.1.0 - 2026-03-09

- Forked the Unagi Android codebase into the new Urchin project.
- Removed Bluetooth scanning, BLE enrichment, alerts, and related permissions.
- Rebuilt the app around SDR-only TPMS capture and local observation storage.
- Added USB support paths for RTL-SDR and HackRF One plus network bridge capture.
- Rethemed the Android UI and site to a yellow-on-black Urchin visual language.
