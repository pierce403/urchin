# Architecture

## Data flow

```text
SDR hardware / network
  → SdrChannel / TcpStreamBridge
    → SdrReading (sealed: Tpms, Pocsag, Adsb, P25)
      → ObservationBuilderRegistry
        → ObservationInput
          → ObservationRecorder
            → DeviceRepository → Room DB
```

## Packages

### `app/src/main/java/guru/urchin/sdr`

SDR ingestion layer. Handles USB dongles, network bridges, and protocol parsing.

- `SdrController` — top-level orchestrator. Manages channels, frequency hopper, and per-protocol network bridges. Decides between USB and network mode based on `SdrPreferences`.
- `SdrChannel` / `SdrChannelConfig` — abstraction for a single SDR stream (USB subprocess or TCP bridge).
- `FrequencyHopper` — time-division frequency rotation for single-dongle multi-protocol capture. Configurable dwell time (default 5 s).
- `TcpStreamBridge<T>` — generic TCP line-stream client parameterized by a parser function. Replaces the former per-protocol bridge classes.
- `Rtl433Process` / `Rtl433JsonParser` — rtl_433 subprocess and JSON parser (TPMS + POCSAG).
- `Dump1090Process` / `Dump1090JsonPoller` / `AdsbJsonParser` — dump1090 subprocess, HTTP poller, and JSON parser (ADS-B).
- `P25NetworkBridge` — HTTP polling client for OP25. TCP mode uses `TcpStreamBridge` with `P25NetworkBridge.parseP25Json`.
- `ObservationBuilderRegistry` — dispatches `SdrReading` sealed variants to protocol-specific builders.
- `TpmsObservationBuilder`, `PocsagObservationBuilder`, `AdsbObservationBuilder`, `P25ObservationBuilder` — convert protocol readings into `ObservationInput`.
- `SdrReading` — sealed class with `Tpms`, `Pocsag`, `Adsb`, and `P25` variants. Common fields: `rssi`, `snr`, `frequencyMhz`, `rawJson`.
- `SdrPreferences` — SharedPreferences wrapper for source selection, frequency, gain, per-protocol network ports, and enabled protocols.
- `UsbDetector` / `SdrUsbDetector` — USB device enumeration and permission management for RTL-SDR and HackRF One.
- `JsonExtensions` — null-safe JSON helpers (`optStringOrNull`, `optDoubleOrNull`, `optIntOrNull`, `optBooleanOrNull`) shared across all parsers.

### `app/src/main/java/guru/urchin/scan`

Shared observation pipeline and diagnostics.

- `ObservationRecorder` — accepts `ObservationInput` and writes to the repository.
- `ObservationInput` — protocol-agnostic union DTO carrying fields for all four protocols plus legacy BLE fields.
- `DeviceKey` — generates a stable SHA-256 device identity using protocol-specific tokens (ADS-B by ICAO, POCSAG by CAP+function, P25 by WACN+system+unit, TPMS by model+sensor).
- `ScanDiagnosticsStore` / `ScanDiagnosticsSnapshot` — runtime diagnostics counters and state.

### `app/src/main/java/guru/urchin/data`

Room database layer.

- `DeviceEntity` / `SightingEntity` — Room entities with indices on `lastSeen`, `(protocolType, lastSeen)`, `deviceKey`, `timestamp`, and `(protocolType, timestamp)`.
- `DeviceDao` / `SightingDao` — per-protocol prune and delete queries.
- `DeviceRepository` — upsert, sighting recording, and per-protocol retention pruning (ADS-B 7 days, P25 14 days, TPMS/POCSAG/other 30 days).
- `AppDatabase` — Room database with migrations v1→v2 (protocolType column) and v2→v3 (composite indices).
- `ContinuousSightingPolicy` / `DeviceMaintenancePolicy` — time-based policies for sighting deduplication and prune scheduling.

### `app/src/main/java/guru/urchin/ui`

Activities, adapters, and ViewModels.

- `MainActivity` — device list with protocol filter chips, sort, search, live-only/starred/battery-low filters, and SDR controls.
- `MainViewModel` — consolidated `FilterState` + single `combine()` flow. Manages scan start/stop/pause lifecycle.
- `DeviceDetailActivity` — sensor detail with rename, JSON export (copy/save/share), sighting history.
- `DiagnosticsActivity` — runtime state, hardware info, callback counters, and log output.
- `DeviceAdapter` / `SightingAdapter` — RecyclerView adapters with `DiffUtil` and stable IDs.

### `app/src/main/java/guru/urchin/util`

Formatting, metadata, and helpers.

- `SensorMetadata` / `SensorMetadataParser` — deserialized form of `lastMetadataJson` covering all four protocols.
- `SensorPresentationBuilder` — protocol-dispatch builder producing display title, summary, detail lines, and search text.
- `Formatters` — timestamp, RSSI, pressure, temperature, gain, and sighting count formatting with cached `DateTimeFormatter`.
- `DebugLog` — ring-buffer debug log with UI display support.
