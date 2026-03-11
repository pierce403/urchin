# Features

- Multi-protocol SDR capture: TPMS, POCSAG, ADS-B, and P25
- USB support for RTL-SDR and HackRF One with automatic VID/PID detection
- Multi-dongle mode: one dongle per frequency when multiple USB SDRs are connected
- Single-dongle frequency hopping with configurable dwell time
- TCP/HTTP bridge support for remote rtl_433, dump1090, and OP25 streams
- Per-protocol parsing: TPMS (pressure, temperature, battery, vendor), POCSAG (CAP code, function, message), ADS-B (ICAO, callsign, altitude, speed, position), P25 (unit ID, NAC, WACN, talk group)
- Local sensor history with sighting rollups and per-protocol retention (ADS-B 7 days, P25 14 days, TPMS/POCSAG 30 days)
- Search, sort, live-only, starred-only, battery-low, and protocol filter chips
- Per-protocol UI icons and presentation formatting
- Raw JSON export from the detail screen (copy, save, share)
- Diagnostics screen with state, counters, and recent logs
