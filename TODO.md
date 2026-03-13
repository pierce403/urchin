# TODO

Core multi-protocol support (TPMS, POCSAG, ADS-B, P25) is implemented. NDK
cross-compilation of libusb, librtlsdr, rtl_433, dump1090, and p25_scanner is
integrated into the Gradle build (see `third_party/CMakeLists.txt`).

SQLCipher database encryption, affinity groups (encrypted team observation
sharing via `.urchin` bundles), and a continuous scan foreground service with
boot auto-start have been ported from the upstream unagi project.

Remaining items:

- P25 USB-only mode end-to-end testing with a dedicated dongle. Native
  `p25_scanner` binary and USB dongle detection are implemented
  (`SdrController.kt:222-310`, `P25Process.kt`); needs hardware validation.
- UAT 978 MHz native binary for USB mode. The app-layer support is complete
  (protocol toggle, network bridge on port 30978, frequency routing in parser,
  `protocolType = "uat"` in observations, simulator). USB mode includes 978 MHz
  in the frequency hopping list but needs a dump978-equivalent native binary
  in `third_party/` for on-device decoding.
