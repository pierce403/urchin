# TODO

Core multi-protocol support (TPMS, POCSAG, ADS-B, P25) is implemented. NDK
cross-compilation of libusb, librtlsdr, rtl_433, dump1090, and p25_scanner is
integrated into the Gradle build (see `third_party/CMakeLists.txt`).

Remaining items:

- Extend `scripts/sdr-simulator.py` to emit multi-protocol test data (ADS-B, POCSAG, P25 alongside TPMS).
- P25 USB-only mode end-to-end testing with a dedicated dongle.
- UAT 978 MHz ADS-B support (frequency preset exists but no dedicated parser).
- Replace remaining inherited artwork with Urchin-specific assets.
