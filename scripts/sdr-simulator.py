#!/usr/bin/env python3
"""
Multi-Protocol SDR Simulator — sends rtl_433, dump1090, and OP25-format JSON over TCP.

Simulates TPMS, POCSAG, ADS-B, and P25 traffic for testing Urchin's multi-protocol
pipeline without real RF hardware.

Usage:
  python3 scripts/sdr-simulator.py                         # all protocols, 2s interval
  python3 scripts/sdr-simulator.py --port 1234              # custom port
  python3 scripts/sdr-simulator.py --protocols tpms pocsag  # only TPMS + POCSAG
  python3 scripts/sdr-simulator.py --burst                  # 100ms stress test
  python3 scripts/sdr-simulator.py --adsb-port 30003        # separate ADS-B port
  python3 scripts/sdr-simulator.py --p25-port 23456         # separate P25 port

Connect from Urchin: source=NETWORK, host=<this-IP>, port=1234
Or use adb forward: adb forward tcp:1234 tcp:1234, then host=127.0.0.1

ADS-B data based on publicly available format from dump1090/readsb.
POCSAG data uses standard pager protocol format (CAP codes, function codes).
TPMS data uses real rtl_433 decoder model names.
P25 data uses OP25-compatible metadata JSON format.
"""

import argparse
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
        "--p25-port", type=int, default=23456,
        help="TCP port for P25 data (default: 23456)"
    )
    parser.add_argument(
        "--protocols", nargs="+", default=["tpms", "pocsag", "adsb", "p25"],
        choices=["tpms", "pocsag", "adsb", "p25"],
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
