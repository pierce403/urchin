# Protocol Reference

Urchin captures four radio protocols. Each protocol has its own parser, observation builder,
device key format, and data retention period.

## TPMS (Tire Pressure Monitoring System)

- **Frequencies:** 315 MHz, 433.92 MHz
- **Source tool:** rtl_433
- **Captured fields:** model, sensor ID, pressure (kPa), temperature (°C), battery status, SNR
- **Device key:** `tpms:<model_lowercase>:<sensor_id_uppercase>` → SHA-256
- **Retention:** 30 days

## POCSAG (Pager Protocol)

- **Frequency:** 929.6125 MHz
- **Source tool:** rtl_433
- **Captured fields:** CAP code (address), function code, alphanumeric message
- **Device key:** `pocsag:<cap_code>:<function_code>` → SHA-256
- **Retention:** 30 days

## ADS-B (Automatic Dependent Surveillance – Broadcast)

- **Frequency:** 1090 MHz
- **Source tool:** dump1090
- **Captured fields:** ICAO address, callsign, altitude (ft), speed (kts), heading (°), latitude/longitude, squawk code
- **Device key:** `adsb:<icao_uppercase>` → SHA-256
- **Retention:** 7 days

## P25 (Project 25 Digital Radio)

- **Frequency:** 851 MHz (typical US trunked base)
- **Source tool:** OP25 (TCP stream or HTTP polling)
- **Captured fields:** unit ID, NAC, WACN, system ID, talk group ID
- **Device key:** `p25:<wacn>:<system_id>:<unit_id>` → SHA-256
- **Retention:** 14 days

## Device key generation

`DeviceKey.from()` checks protocol-specific identifiers in priority order:

1. ADS-B ICAO address
2. POCSAG CAP code + function code
3. P25 WACN + system ID + unit ID
4. TPMS model + sensor ID
5. Fallback to normalized address, address, or name

All keys are SHA-256 hashed to a 64-character hex string for storage.
