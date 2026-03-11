# Changelog

## 0.2.6 - 2026-03-11

- Fixed USB RTL-SDR startup to use a plain RTL-SDR device selector (`-d 0`) instead of the invalid Soapy-style `driver=rtlsdr` string that caused `rtl_433` to exit immediately on-device.
- Mirrored bundled `rtl_433` logger output back to stderr when no explicit log sink is configured, so Diagnostics captures the real startup error instead of only the generic `Use "-F log"` hint.
- Added a regression test for the bundled `rtl_433` command-line builder used by USB and HackRF launch paths.

## 0.2.5 - 2026-03-11

- Moved `rtl_433` out of app-private storage and into packaged native executables under `lib/<abi>/...` so Android no longer rejects USB launches with `error=13, Permission denied`.
- Switched `dump1090` and `p25_scanner` to the same native-executable packaging path so all on-device SDR tools resolve from `nativeLibraryDir` consistently.
- Updated diagnostics and setup docs to report the real native executable paths instead of the old extracted-asset location.

## 0.2.4 - 2026-03-11

- Replaced the remaining ninja-style branding with a new gold-on-black urchin mark across the Android launcher icon, notification/status icon, website favicon, and site header.
- Regenerated the website social preview image so link previews now match the Urchin branding instead of the stale `SPIDER` card.
- Kept the web and app icon family aligned around the same spined urchin silhouette.

## 0.2.3 - 2026-03-11

- Routed Android UsbManager-granted RTL-SDR access into bundled `rtl_433` and `p25_scanner` subprocesses by relaying an inherited USB file descriptor into `librtlsdr`.
- Surface subprocess exits back into Urchin's error state instead of leaving USB scans stuck at `0 live sensors` with only recent log lines.
- Clarified that this APK's on-device USB path is RTL-SDR-only for now; HackRF capture still uses Network bridge mode.

## 0.2.2 - 2026-03-11

- Bundled `rtl_433` into the APK as an ABI-specific asset and extract it into app-private storage before launch in USB mode.
- Added Gradle asset-staging tasks so `assembleDebug` packages `rtl_433` automatically for both supported ABIs.
- Disabled rtl_433's upstream test targets in Android builds and shimmed its Android-incompatible `pthread_cancel()` usage so the executable builds cleanly with the NDK.

## 0.2.1 - 2026-03-11

- Added SDR runtime diagnostics that show live USB inventory, permission state, and packaged native-tool paths in-app.
- Logged unsupported USB attach/detach events and replaced generic missing-binary errors with exact APK path checks.
- Added a tracked `third_party/CMakeLists.txt` so `bash scripts/setup-third-party.sh` plus `./gradlew assembleDebug` produces a fresh debug APK again.

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
