#!/usr/bin/env python3
"""
Multi-Protocol SDR Simulator — sends rtl_433, dump1090, OP25, LoRaWAN, Meshtastic,
Wireless M-Bus, Z-Wave, and Amazon Sidewalk-format JSON over TCP.

Simulates TPMS, POCSAG, ADS-B, P25, LoRaWAN, Meshtastic, Wireless M-Bus, Z-Wave,
and Amazon Sidewalk traffic for testing Urchin's multi-protocol pipeline without real
RF hardware.

Usage:
  python3 scripts/sdr-simulator.py                         # all protocols, 2s interval
  python3 scripts/sdr-simulator.py --port 1234              # custom port
  python3 scripts/sdr-simulator.py --protocols tpms pocsag  # only TPMS + POCSAG
  python3 scripts/sdr-simulator.py --burst                  # 100ms stress test
  python3 scripts/sdr-simulator.py --adsb-port 30003        # separate ADS-B port
  python3 scripts/sdr-simulator.py --p25-port 23456         # separate P25 port
  python3 scripts/sdr-simulator.py --lorawan-port 1680      # separate LoRaWAN port
  python3 scripts/sdr-simulator.py --meshtastic-port 1680   # separate Meshtastic port
  python3 scripts/sdr-simulator.py --wmbus-port 1681        # separate Wireless M-Bus port
  python3 scripts/sdr-simulator.py --zwave-port 1682        # separate Z-Wave port
  python3 scripts/sdr-simulator.py --sidewalk-port 1683     # separate Sidewalk port

Connect from Urchin: source=NETWORK, host=<this-IP>, port=1234
Or use adb forward: adb forward tcp:1234 tcp:1234, then host=127.0.0.1

ADS-B data based on publicly available format from dump1090/readsb.
POCSAG data uses standard pager protocol format (CAP codes, function codes).
TPMS data uses real rtl_433 decoder model names.
P25 data uses OP25-compatible metadata JSON format.
LoRaWAN data uses lora_json_bridge-compatible rxpk JSON format.
Meshtastic data uses lora_json_bridge-compatible format with mesh headers.
Wireless M-Bus data uses wmbus_json_bridge-compatible format.
Z-Wave data uses zwave_json_bridge-compatible format.
Amazon Sidewalk data uses sidewalk_json_bridge-compatible format.
"""

import argparse
import base64
import json
import random
import socket
import sys
import threading
import time

# ─── TPMS Profiles ───────────────────────────────────────────────────────────

TPMS_PROFILES = [
    {"model": "PMV-107J",         "id": "0x00ABCDEF", "freq": 433.92, "pressure_field": "pressure_kPa"},
    {"model": "PMV-107J",         "id": "0x00ABCDE0", "freq": 433.92, "pressure_field": "pressure_kPa"},
    {"model": "Schrader",         "id": "0x12345678", "freq": 433.92, "pressure_field": "pressure_kPa"},
    {"model": "Schrader",         "id": "0x12345679", "freq": 433.92, "pressure_field": "pressure_PSI"},
    {"model": "Ford",             "id": "0xAABBCCDD", "freq": 433.92, "pressure_field": "pressure_PSI"},
    {"model": "Hyundai-VDO",      "id": "0x55667788", "freq": 315.00, "pressure_field": "pressure_kPa"},
    {"model": "Renault",          "id": "0xDEADBEEF", "freq": 433.92, "pressure_field": "pressure_bar"},
    {"model": "Jansite-Solar",    "id": "0x11223344", "freq": 433.92, "pressure_field": "pressure_kPa"},
]

PRESSURE_RANGES = {
    "pressure_kPa": (200.0, 250.0),
    "pressure_PSI": (29.0, 36.0),
    "pressure_bar": (2.0, 2.5),
}

# ─── POCSAG Profiles ─────────────────────────────────────────────────────────
# Based on standard POCSAG/Flex pager protocol format.
# CAP codes are 7-digit addresses. Function codes 0-3.

POCSAG_PROFILES = [
    {"model": "Flex",      "address": "1234567", "function": 1},
    {"model": "Flex",      "address": "2345678", "function": 0},
    {"model": "POCSAG-512","address": "3456789", "function": 2},
    {"model": "POCSAG-512","address": "4567890", "function": 3},
    {"model": "Flex",      "address": "5678901", "function": 1},
    {"model": "POCSAG-1200","address": "6789012", "function": 0},
]

POCSAG_MESSAGES = [
    "RESPOND TO 123 MAIN ST APT 4 - MEDICAL EMERGENCY",
    "STRUCTURE FIRE AT 456 OAK AVE - ENGINE 7 RESPOND",
    "10-50 INJURY ACCIDENT HWY 101 NB MM 42",
    "BURGLARY ALARM 789 PINE RD - SILENT ALARM",
    "WELFARE CHECK REQUESTED 321 ELM BLVD UNIT 12",
    "TEST PAGE - DISREGARD",
    "MVA WITH ENTRAPMENT I-95 SB EXIT 22",
    "HAZMAT INCIDENT INDUSTRIAL PARK RD",
    "MISSING PERSON ALERT - SILVER ALERT ACTIVATED",
    "MUTUAL AID REQUEST FROM COUNTY FD",
    "",  # numeric-only page (no alpha content)
]

# ─── ADS-B Profiles ──────────────────────────────────────────────────────────
# Based on publicly available ADS-B data format (dump1090/readsb aircraft.json).
# ICAO addresses are real format (24-bit hex). Callsigns follow ICAO format.
# All data is simulated, not real aircraft.

ADSB_PROFILES = [
    {"hex": "A00001", "flight": "AAL123 ", "category": "A3"},
    {"hex": "A00002", "flight": "UAL456 ", "category": "A3"},
    {"hex": "A00003", "flight": "DAL789 ", "category": "A3"},
    {"hex": "A00004", "flight": "SWA321 ", "category": "A3"},
    {"hex": "A00005", "flight": "N12345 ", "category": "A1"},  # GA
    {"hex": "A00006", "flight": "N67890 ", "category": "A1"},  # GA
    {"hex": "AE1234", "flight": "BLOCKED ", "category": "A5"}, # Military format
    {"hex": "A00007", "flight": "JBU100 ", "category": "A3"},
    {"hex": "A00008", "flight": "FDX901 ", "category": "A5"}, # Cargo
    {"hex": "A00009", "flight": "",         "category": "A0"}, # No callsign
]

# ─── UAT 978 MHz Profiles ────────────────────────────────────────────────────
# UAT is used primarily by GA aircraft below 18000 ft in US airspace.
# Format is compatible with dump978/dump1090 JSON output.

UAT_PROFILES = [
    {"hex": "A10001", "flight": "N100AB ", "category": "A1"},  # GA single-engine
    {"hex": "A10002", "flight": "N200CD ", "category": "A1"},
    {"hex": "A10003", "flight": "N300EF ", "category": "A2"},  # GA multi-engine
    {"hex": "A10004", "flight": "N400GH ", "category": "A1"},
    {"hex": "A10005", "flight": "",         "category": "A1"}, # No callsign
    {"hex": "A10006", "flight": "N500IJ ", "category": "A7"},  # Rotorcraft
]

# ─── P25 Profiles ──────────────────────────────────────────────────────────
# Based on standard P25 trunked radio system metadata (TSBK control channel).
# Unit IDs, talk group IDs, NAC, WACN, and system IDs are simulated.

P25_PROFILES = [
    {"unit_id": "1001", "nac": "0x293", "wacn": "0xBEE00", "system_id": "0x3A1"},
    {"unit_id": "1002", "nac": "0x293", "wacn": "0xBEE00", "system_id": "0x3A1"},
    {"unit_id": "2050", "nac": "0x293", "wacn": "0xBEE00", "system_id": "0x3A1"},
    {"unit_id": "3100", "nac": "0x5E4", "wacn": "0xFEE10", "system_id": "0x102"},
    {"unit_id": "3101", "nac": "0x5E4", "wacn": "0xFEE10", "system_id": "0x102"},
    {"unit_id": "4200", "nac": "0x5E4", "wacn": "0xFEE10", "system_id": "0x102"},
    {"unit_id": "5000", "nac": "0x1A3", "wacn": "0xABC00", "system_id": "0x050"},
    {"unit_id": "5001", "nac": "0x1A3", "wacn": "0xABC00", "system_id": "0x050"},
]

P25_TALKGROUPS = [
    "100", "200", "300", "500", "1000", "1500", "2000",
    "10100", "10200", "20100", "30100", "50000",
]

P25_FREQUENCIES = [
    851.0125, 851.2625, 851.5125, 851.7625,
    852.0125, 852.2625, 852.5125,
    866.0125, 866.2625, 866.5125,
]

# ─── LoRaWAN Profiles ───────────────────────────────────────────────────────
# Simulates rxpk data from lora_json_bridge (Semtech packet forwarder output).
# DevAddr is encoded in a synthetic PHY payload (MHDR + DevAddr + padding).

LORAWAN_PROFILES = [
    {"dev_addr": "01ABCDEF"},
    {"dev_addr": "02345678"},
    {"dev_addr": "039A0B1C"},
    {"dev_addr": "04DE00FF"},
    {"dev_addr": "05112233"},
    {"dev_addr": "06AABB00"},
]

LORAWAN_SPREADING_FACTORS = [
    "SF7BW125", "SF8BW125", "SF9BW125", "SF10BW125", "SF11BW125", "SF12BW125",
]

LORAWAN_FREQUENCIES = [
    903.9, 904.1, 904.3, 904.5, 904.7, 904.9, 905.1, 905.3,
]

LORAWAN_CODING_RATES = ["4/5", "4/6", "4/7", "4/8"]

# ─── Meshtastic Profiles ──────────────────────────────────────────────────────
# Simulates Meshtastic LoRa mesh packets decoded from lora_json_bridge output.
# Node IDs are 8-character hex strings representing 4-byte sender addresses.

MESHTASTIC_PROFILES = [
    {"node_id": "1A2B3C4D"},
    {"node_id": "2B3C4D5E"},
    {"node_id": "3C4D5E6F"},
    {"node_id": "4D5E6F70"},
    {"node_id": "5E6F7081"},
    {"node_id": "6F708192"},
]

# ─── Wireless M-Bus Profiles ─────────────────────────────────────────────────
# Simulates smart meter readings decoded by wmbus_json_bridge.

WMBUS_PROFILES = [
    {"manufacturer": "KAM", "serial": "12345678", "version": 1, "device_type": "water"},
    {"manufacturer": "LAN", "serial": "23456789", "version": 2, "device_type": "gas"},
    {"manufacturer": "SEN", "serial": "34567890", "version": 1, "device_type": "electricity"},
    {"manufacturer": "KAM", "serial": "45678901", "version": 3, "device_type": "heat"},
    {"manufacturer": "LAN", "serial": "56789012", "version": 1, "device_type": "water"},
    {"manufacturer": "SEN", "serial": "67890123", "version": 2, "device_type": "gas"},
]

# ─── Z-Wave Profiles ─────────────────────────────────────────────────────────
# Simulates Z-Wave smart home packets decoded by zwave_json_bridge.

ZWAVE_PROFILES = [
    {"home_id": "AABBCCDD", "node_id": 1},
    {"home_id": "AABBCCDD", "node_id": 5},
    {"home_id": "AABBCCDD", "node_id": 12},
    {"home_id": "11223344", "node_id": 2},
    {"home_id": "11223344", "node_id": 8},
    {"home_id": "DEADBEEF", "node_id": 3},
]

ZWAVE_FRAME_TYPES = ["singlecast", "multicast", "ack", "routed"]

# ─── Amazon Sidewalk Profiles ────────────────────────────────────────────────
# Simulates Amazon Sidewalk packets decoded by sidewalk_json_bridge.
# SMSNs are 10-character hex strings (5-byte Sidewalk Manufacturing Serial Number).

SIDEWALK_PROFILES = [
    {"smsn": "0A1B2C3D4E"},
    {"smsn": "1B2C3D4E5F"},
    {"smsn": "2C3D4E5F60"},
    {"smsn": "3D4E5F6071"},
    {"smsn": "4E5F607182"},
    {"smsn": "5F60718293"},
]

SIDEWALK_FRAME_TYPES = ["data", "ack", "keep_alive", "auth"]

# Squawk codes — publicly known standard codes
SQUAWK_CODES = [
    "1200",  # VFR
    "7500",  # Hijack
    "7600",  # Comm failure
    "7700",  # Emergency
    "0100", "0200", "0300", "0400", "0500",
    "1000", "2000", "3000", "4000", "5000",
]


def generate_tpms(profile):
    """Generate a single rtl_433-compatible TPMS JSON reading."""
    pfield = profile["pressure_field"]
    pmin, pmax = PRESSURE_RANGES[pfield]
    return {
        "time": time.strftime("%Y-%m-%d %H:%M:%S"),
        "model": profile["model"],
        "type": "TPMS",
        "id": profile["id"],
        "status": random.choice([0, 0, 0, 1]),
        "battery_ok": random.choices([1, 0], weights=[95, 5])[0],
        pfield: round(random.uniform(pmin, pmax), 1),
        "temperature_C": round(random.uniform(15.0, 40.0), 1),
        "rssi": round(random.uniform(-20.0, -5.0), 1),
        "snr": round(random.uniform(8.0, 25.0), 1),
        "freq": profile["freq"],
    }


def generate_pocsag(profile):
    """Generate a single rtl_433-compatible POCSAG/Flex JSON reading."""
    msg = random.choice(POCSAG_MESSAGES)
    reading = {
        "time": time.strftime("%Y-%m-%d %H:%M:%S"),
        "model": profile["model"],
        "address": profile["address"],
        "function": profile["function"],
    }
    if msg:
        reading["alpha"] = msg
    reading["rssi"] = round(random.uniform(-25.0, -5.0), 1)
    return reading


def generate_adsb(profile):
    """Generate a single dump1090-compatible ADS-B JSON reading."""
    lat = round(random.uniform(25.0, 48.0), 4)   # Continental US range
    lon = round(random.uniform(-125.0, -70.0), 4)
    alt = random.randint(500, 45000)
    speed = round(random.uniform(80.0, 550.0), 1)
    heading = round(random.uniform(0.0, 360.0), 1)

    reading = {
        "hex": profile["hex"],
        "type": "adsb_icao",
        "flight": profile["flight"],
        "alt_baro": alt,
        "alt_geom": alt + random.randint(-200, 200),
        "gs": speed,
        "track": heading,
        "lat": lat,
        "lon": lon,
        "category": profile["category"],
        "rssi": round(random.uniform(-30.0, -3.0), 1),
    }
    if random.random() < 0.3:
        reading["squawk"] = random.choice(SQUAWK_CODES)
    if not profile["flight"].strip():
        del reading["flight"]
    return reading


def generate_p25(profile):
    """Generate a single OP25-compatible P25 control channel JSON reading."""
    return {
        "unit_id": profile["unit_id"],
        "talkgroup": random.choice(P25_TALKGROUPS),
        "nac": profile["nac"],
        "wacn": profile["wacn"],
        "system_id": profile["system_id"],
        "rssi": round(random.uniform(-85.0, -40.0), 1),
        "snr": round(random.uniform(5.0, 20.0), 1),
        "freq": random.choice(P25_FREQUENCIES),
    }


def generate_uat(profile):
    """Generate a single UAT 978 MHz ADS-B JSON reading.
    UAT aircraft are typically GA below 18000 ft."""
    lat = round(random.uniform(25.0, 48.0), 4)
    lon = round(random.uniform(-125.0, -70.0), 4)
    alt = random.randint(500, 17500)  # UAT: below FL180
    speed = round(random.uniform(60.0, 250.0), 1)  # GA speeds
    heading = round(random.uniform(0.0, 360.0), 1)

    reading = {
        "hex": profile["hex"],
        "type": "adsb_icao_nt",  # UAT non-transponder
        "flight": profile["flight"],
        "alt_baro": alt,
        "alt_geom": alt + random.randint(-100, 100),
        "gs": speed,
        "track": heading,
        "lat": lat,
        "lon": lon,
        "category": profile["category"],
        "rssi": round(random.uniform(-30.0, -5.0), 1),
    }
    if not profile["flight"].strip():
        del reading["flight"]
    return reading


def generate_lorawan(profile):
    """Generate a single lora_json_bridge-compatible LoRaWAN rxpk JSON reading."""
    dev_addr_bytes = bytes.fromhex(profile["dev_addr"])
    # Build minimal PHY payload: MHDR (0x40 = Unconfirmed Data Up) + DevAddr (LE) + padding
    mhdr = bytes([0x40])
    dev_addr_le = dev_addr_bytes[::-1]  # reverse to little-endian
    payload_body = random.randbytes(random.randint(8, 30))
    phy_payload = mhdr + dev_addr_le + payload_body
    data_b64 = base64.b64encode(phy_payload).decode("ascii")

    return {
        "type": "lorawan",
        "tmst": random.randint(0, 2**32 - 1),
        "freq": random.choice(LORAWAN_FREQUENCIES),
        "chan": random.randint(0, 7),
        "rfch": random.choice([0, 1]),
        "stat": random.choices([1, 0, -1], weights=[90, 5, 5])[0],
        "modu": "LORA",
        "datr": random.choice(LORAWAN_SPREADING_FACTORS),
        "codr": random.choice(LORAWAN_CODING_RATES),
        "rssi": round(random.uniform(-120.0, -40.0), 1),
        "lsnr": round(random.uniform(-15.0, 12.0), 1),
        "size": len(phy_payload),
        "data": data_b64,
    }


def generate_meshtastic(profile):
    """Generate a single Meshtastic LoRa mesh packet JSON reading."""
    sender = bytes.fromhex(profile["node_id"])
    dest_id = random.choice(MESHTASTIC_PROFILES)["node_id"]
    dest = bytes.fromhex(dest_id)
    # Build data: destination(4B LE) + sender(4B LE) + packetId(1B) + flags(1B) + payload
    dest_le = dest[::-1]
    sender_le = sender[::-1]
    packet_id = bytes([random.randint(0, 255)])
    flags = bytes([random.randint(0, 15)])
    payload_body = random.randbytes(random.randint(10, 30))
    raw = dest_le + sender_le + packet_id + flags + payload_body
    data_b64 = base64.b64encode(raw).decode("ascii")

    return {
        "type": "meshtastic",
        "freq": random.choice([906.875, 907.500, 908.125]),
        "rssi": round(random.uniform(-110.0, -50.0), 1),
        "lsnr": round(random.uniform(-5.0, 12.0), 1),
        "datr": "SF12BW125",
        "codr": "4/5",
        "size": len(raw),
        "data": data_b64,
        "chan": random.randint(0, 7),
    }


def generate_wmbus(profile):
    """Generate a single Wireless M-Bus smart meter JSON reading."""
    return {
        "type": "wmbus",
        "manufacturer": profile["manufacturer"],
        "serial": profile["serial"],
        "version": profile["version"],
        "device_type": profile["device_type"],
        "rssi": round(random.uniform(-100.0, -40.0), 1),
        "freq": 868.95,
    }


def generate_zwave(profile):
    """Generate a single Z-Wave smart home JSON reading."""
    return {
        "type": "zwave",
        "home_id": profile["home_id"],
        "node_id": profile["node_id"],
        "frame_type": random.choice(ZWAVE_FRAME_TYPES),
        "rssi": round(random.uniform(-95.0, -40.0), 1),
        "freq": 908.42,
    }


def generate_sidewalk(profile):
    """Generate a single Amazon Sidewalk JSON reading."""
    return {
        "type": "sidewalk",
        "smsn": profile["smsn"],
        "frame_type": random.choice(SIDEWALK_FRAME_TYPES),
        "rssi": round(random.uniform(-110.0, -50.0), 1),
        "freq": 903.0,
    }


def generate_adsb_aircraft_json(profiles, count=None):
    """Generate dump1090 aircraft.json format (array wrapper)."""
    if count is None:
        count = random.randint(3, len(profiles))
    selected = random.sample(profiles, min(count, len(profiles)))
    aircraft = [generate_adsb(p) for p in selected]
    return {
        "now": time.time(),
        "messages": random.randint(1000, 50000),
        "aircraft": aircraft,
    }


def handle_rtl433_client(conn, addr, tpms_profiles, pocsag_profiles, interval, enabled_protocols):
    """Send rtl_433-format readings (TPMS + POCSAG) to a connected client."""
    print(f"[rtl_433] Client connected: {addr}")
    generators = []
    if "tpms" in enabled_protocols and tpms_profiles:
        generators.append(("tpms", tpms_profiles))
    if "pocsag" in enabled_protocols and pocsag_profiles:
        generators.append(("pocsag", pocsag_profiles))

    if not generators:
        print(f"[rtl_433] No protocols enabled for {addr}, closing")
        conn.close()
        return

    try:
        while True:
            proto, profiles = random.choice(generators)
            profile = random.choice(profiles)
            if proto == "tpms":
                reading = generate_tpms(profile)
            else:
                reading = generate_pocsag(profile)
            line = json.dumps(reading) + "\n"
            conn.sendall(line.encode("utf-8"))
            time.sleep(interval)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[rtl_433] Client disconnected: {addr}")
    finally:
        conn.close()


def handle_adsb_client(conn, addr, adsb_profiles, interval, mode="line"):
    """Send ADS-B readings to a connected client.

    mode="line": one aircraft JSON object per line (dump1090 single-line format)
    mode="bulk": aircraft.json bulk format
    """
    print(f"[ADS-B] Client connected: {addr}")
    try:
        while True:
            if mode == "bulk":
                data = generate_adsb_aircraft_json(adsb_profiles)
                line = json.dumps(data) + "\n"
            else:
                profile = random.choice(adsb_profiles)
                reading = generate_adsb(profile)
                line = json.dumps(reading) + "\n"
            conn.sendall(line.encode("utf-8"))
            time.sleep(interval)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[ADS-B] Client disconnected: {addr}")
    finally:
        conn.close()


def handle_uat_client(conn, addr, uat_profiles, interval):
    """Send UAT 978 MHz readings to a connected client."""
    print(f"[UAT] Client connected: {addr}")
    try:
        while True:
            profile = random.choice(uat_profiles)
            reading = generate_uat(profile)
            line = json.dumps(reading) + "\n"
            conn.sendall(line.encode("utf-8"))
            time.sleep(interval)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[UAT] Client disconnected: {addr}")
    finally:
        conn.close()


def handle_p25_client(conn, addr, p25_profiles, interval):
    """Send P25 control channel metadata readings to a connected client."""
    print(f"[P25] Client connected: {addr}")
    try:
        while True:
            profile = random.choice(p25_profiles)
            reading = generate_p25(profile)
            line = json.dumps(reading) + "\n"
            conn.sendall(line.encode("utf-8"))
            time.sleep(interval)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[P25] Client disconnected: {addr}")
    finally:
        conn.close()


def handle_lorawan_client(conn, addr, lorawan_profiles, interval):
    """Send LoRaWAN rxpk readings to a connected client."""
    print(f"[LoRaWAN] Client connected: {addr}")
    try:
        while True:
            profile = random.choice(lorawan_profiles)
            reading = generate_lorawan(profile)
            line = json.dumps(reading) + "\n"
            conn.sendall(line.encode("utf-8"))
            time.sleep(interval)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[LoRaWAN] Client disconnected: {addr}")
    finally:
        conn.close()


def handle_meshtastic_client(conn, addr, meshtastic_profiles, interval):
    """Send Meshtastic mesh packet readings to a connected client."""
    print(f"[Meshtastic] Client connected: {addr}")
    try:
        while True:
            profile = random.choice(meshtastic_profiles)
            reading = generate_meshtastic(profile)
            line = json.dumps(reading) + "\n"
            conn.sendall(line.encode("utf-8"))
            time.sleep(interval)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[Meshtastic] Client disconnected: {addr}")
    finally:
        conn.close()


def handle_wmbus_client(conn, addr, wmbus_profiles, interval):
    """Send Wireless M-Bus smart meter readings to a connected client."""
    print(f"[W-MBus] Client connected: {addr}")
    try:
        while True:
            profile = random.choice(wmbus_profiles)
            reading = generate_wmbus(profile)
            line = json.dumps(reading) + "\n"
            conn.sendall(line.encode("utf-8"))
            time.sleep(interval)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[W-MBus] Client disconnected: {addr}")
    finally:
        conn.close()


def handle_zwave_client(conn, addr, zwave_profiles, interval):
    """Send Z-Wave smart home readings to a connected client."""
    print(f"[Z-Wave] Client connected: {addr}")
    try:
        while True:
            profile = random.choice(zwave_profiles)
            reading = generate_zwave(profile)
            line = json.dumps(reading) + "\n"
            conn.sendall(line.encode("utf-8"))
            time.sleep(interval)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[Z-Wave] Client disconnected: {addr}")
    finally:
        conn.close()


def handle_sidewalk_client(conn, addr, sidewalk_profiles, interval):
    """Send Amazon Sidewalk readings to a connected client."""
    print(f"[Sidewalk] Client connected: {addr}")
    try:
        while True:
            profile = random.choice(sidewalk_profiles)
            reading = generate_sidewalk(profile)
            line = json.dumps(reading) + "\n"
            conn.sendall(line.encode("utf-8"))
            time.sleep(interval)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[Sidewalk] Client disconnected: {addr}")
    finally:
        conn.close()


def start_server(port, handler, name, *handler_args):
    """Start a TCP server on the given port."""
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", port))
    server.listen(5)
    print(f"  [{name}] Listening on port {port}")

    def accept_loop():
        while True:
            conn, addr = server.accept()
            thread = threading.Thread(
                target=handler,
                args=(conn, addr, *handler_args),
                daemon=True,
            )
            thread.start()

    thread = threading.Thread(target=accept_loop, daemon=True)
    thread.start()
    return server


def main():
    parser = argparse.ArgumentParser(
        description="Multi-protocol SDR simulator for Urchin testing"
    )
    parser.add_argument(
        "--port", type=int, default=1234,
        help="TCP port for rtl_433 data (TPMS + POCSAG) (default: 1234)"
    )
    parser.add_argument(
        "--adsb-port", type=int, default=30003,
        help="TCP port for ADS-B data (default: 30003)"
    )
    parser.add_argument(
        "--uat-port", type=int, default=30978,
        help="TCP port for UAT 978 MHz data (default: 30978)"
    )
    parser.add_argument(
        "--p25-port", type=int, default=23456,
        help="TCP port for P25 data (default: 23456)"
    )
    parser.add_argument(
        "--lorawan-port", type=int, default=1680,
        help="TCP port for LoRaWAN data (default: 1680)"
    )
    parser.add_argument(
        "--meshtastic-port", type=int, default=1680,
        help="TCP port for Meshtastic data (default: 1680)"
    )
    parser.add_argument(
        "--wmbus-port", type=int, default=1681,
        help="TCP port for Wireless M-Bus data (default: 1681)"
    )
    parser.add_argument(
        "--zwave-port", type=int, default=1682,
        help="TCP port for Z-Wave data (default: 1682)"
    )
    parser.add_argument(
        "--sidewalk-port", type=int, default=1683,
        help="TCP port for Amazon Sidewalk data (default: 1683)"
    )
    parser.add_argument(
        "--protocols", nargs="+",
        default=["tpms", "pocsag", "adsb", "uat", "p25", "lorawan", "meshtastic", "wmbus", "zwave", "sidewalk"],
        choices=["tpms", "pocsag", "adsb", "uat", "p25", "lorawan", "meshtastic", "wmbus", "zwave", "sidewalk"],
        help="Protocols to simulate (default: all)"
    )
    parser.add_argument(
        "--interval", type=float, default=2.0,
        help="Seconds between readings (default: 2.0)"
    )
    parser.add_argument(
        "--burst", action="store_true",
        help="Burst mode: 100ms interval for stress testing"
    )
    parser.add_argument(
        "--adsb-mode", choices=["line", "bulk"], default="line",
        help="ADS-B output mode: 'line' for single objects, 'bulk' for aircraft.json (default: line)"
    )
    args = parser.parse_args()

    interval = 0.1 if args.burst else args.interval
    protocols = set(args.protocols)
    servers = []

    print("Urchin SDR Simulator")
    print(f"  Protocols: {', '.join(sorted(protocols))}")
    print(f"  Interval:  {interval}s {'(burst mode)' if args.burst else ''}")

    # rtl_433 server (TPMS + POCSAG)
    rtl433_protocols = protocols & {"tpms", "pocsag"}
    if rtl433_protocols:
        s = start_server(
            args.port,
            handle_rtl433_client,
            "rtl_433",
            TPMS_PROFILES if "tpms" in protocols else [],
            POCSAG_PROFILES if "pocsag" in protocols else [],
            interval,
            rtl433_protocols,
        )
        servers.append(s)

    # P25 server
    if "p25" in protocols:
        s = start_server(
            args.p25_port,
            handle_p25_client,
            "P25",
            P25_PROFILES,
            interval,
        )
        servers.append(s)

    # UAT server
    if "uat" in protocols:
        s = start_server(
            args.uat_port,
            handle_uat_client,
            "UAT",
            UAT_PROFILES,
            interval,
        )
        servers.append(s)

    # LoRaWAN server
    if "lorawan" in protocols:
        s = start_server(
            args.lorawan_port,
            handle_lorawan_client,
            "LoRaWAN",
            LORAWAN_PROFILES,
            interval,
        )
        servers.append(s)

    # Meshtastic server
    if "meshtastic" in protocols:
        s = start_server(
            args.meshtastic_port,
            handle_meshtastic_client,
            "Meshtastic",
            MESHTASTIC_PROFILES,
            interval,
        )
        servers.append(s)

    # Wireless M-Bus server
    if "wmbus" in protocols:
        s = start_server(
            args.wmbus_port,
            handle_wmbus_client,
            "W-MBus",
            WMBUS_PROFILES,
            interval,
        )
        servers.append(s)

    # Z-Wave server
    if "zwave" in protocols:
        s = start_server(
            args.zwave_port,
            handle_zwave_client,
            "Z-Wave",
            ZWAVE_PROFILES,
            interval,
        )
        servers.append(s)

    # Amazon Sidewalk server
    if "sidewalk" in protocols:
        s = start_server(
            args.sidewalk_port,
            handle_sidewalk_client,
            "Sidewalk",
            SIDEWALK_PROFILES,
            interval,
        )
        servers.append(s)

    # ADS-B server
    if "adsb" in protocols:
        s = start_server(
            args.adsb_port,
            handle_adsb_client,
            "ADS-B",
            ADSB_PROFILES,
            interval,
            args.adsb_mode,
        )
        servers.append(s)

    print("  Waiting for connections...")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down.")
    finally:
        for s in servers:
            s.close()


if __name__ == "__main__":
    main()
