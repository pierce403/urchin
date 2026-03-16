# Protocol Reference

Urchin captures twelve radio protocols. Each protocol has its own parser, observation builder,
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
- **Captured fields:** unit ID, NAC, WACN, system ID, talk group ID, encryption algorithm, encryption key ID, emergency flag, voice/data type
- **Device key:** `p25:<wacn>:<system_id>:<unit_id>` → SHA-256
- **Retention:** 14 days

## LoRaWAN (Long Range Wide Area Network)

- **Frequency:** 902-928 MHz (US) / 863-870 MHz (EU)
- **Hardware:** RAK2243 HAT (SX1301 LoRa channels)
- **Source tool:** lora_pkt_fwd + lora_json_bridge
- **Captured fields:** DevAddr (4-byte device address), spreading factor, coding rate, payload size, CRC status, FPort, frame counter, message type
- **Device key:** `lorawan:<devAddr_uppercase>` → SHA-256
- **Retention:** 14 days

## Meshtastic (LoRa P2P Mesh)

- **Frequency:** 902-928 MHz (US) / 863-870 MHz (EU)
- **Hardware:** RAK2243 HAT (SX1301 LoRa channels)
- **Source tool:** lora_pkt_fwd + lora_json_bridge
- **Captured fields:** node ID (sender), destination ID, packet ID, hop limit, hop start, channel hash, port number, payload text (unencrypted)
- **Device key:** `meshtastic:<nodeId_uppercase>` → SHA-256
- **Retention:** 14 days

## Wireless M-Bus (Smart Metering)

- **Frequency:** 868.95 MHz (EU only)
- **Hardware:** RAK2243 HAT (SX1301 FSK channel)
- **Source tool:** wmbus_json_bridge
- **Captured fields:** manufacturer (3-letter code), serial number, meter version, meter type (water/gas/electric/heat)
- **Device key:** `wmbus:<manufacturer>:<serialNumber>` → SHA-256
- **Retention:** 30 days

## Z-Wave (Smart Home)

- **Frequency:** 908.42 MHz (US)
- **Hardware:** RAK2243 HAT (SX1301 FSK channel)
- **Source tool:** zwave_json_bridge
- **Captured fields:** Home ID (4-byte network ID), Node ID, frame type, command class, node role, security level
- **Device key:** `zwave:<homeId_uppercase>:<nodeId>` → SHA-256
- **Retention:** 30 days

## Amazon Sidewalk

- **Frequency:** 900 MHz (US)
- **Hardware:** RAK2243 HAT (SX1301 FSK channel)
- **Source tool:** sidewalk_json_bridge
- **Captured fields:** SMSN (Sidewalk Manufacturing Serial Number, 5 bytes), frame type
- **Device key:** `sidewalk:<smsn_uppercase>` → SHA-256
- **Retention:** 14 days

## DMR (Digital Mobile Radio)

- **Frequency:** 400 MHz, 900 MHz (varies by region)
- **Source tool:** dmr_json_bridge (network bridge)
- **Default port:** 1684
- **Captured fields:** radio ID, color code, slot, talk group, data type, encryption status
- **Device key:** `dmr:<radioId>` → SHA-256
- **Retention:** 14 days

## NXDN (Kenwood/Icom Digital Radio)

- **Frequency:** 700/800/900 MHz (varies by system)
- **Source tool:** nxdn_json_bridge (network bridge)
- **Default port:** 1685
- **Captured fields:** unit ID, RAN (Radio Access Number), talk group, message type
- **Device key:** `nxdn:<unitId>` → SHA-256
- **Retention:** 14 days

## Device key generation

`DeviceKey.from()` checks protocol-specific identifiers in priority order:

1. ADS-B ICAO address
2. POCSAG CAP code + function code
3. P25 WACN + system ID + unit ID
4. LoRaWAN DevAddr
5. Meshtastic node ID
6. Wireless M-Bus manufacturer + serial number
7. Z-Wave Home ID + Node ID
8. Amazon Sidewalk SMSN
9. DMR radio ID
10. NXDN unit ID
11. TPMS model + sensor ID
12. Fallback to normalized address, address, or name

All keys are SHA-256 hashed to a 64-character hex string for storage.
