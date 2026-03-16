#!/usr/bin/env python3
"""
Aaronia SPECTRAN V6 Bridge — connects RTSA Suite PRO's HTTP IQ stream to
protocol decoders (rtl_433, dump1090) and relays decoded JSON over TCP for
Urchin's Network mode.

The SPECTRAN V6 connects to a PC via USB 3.0 and is controlled by RTSA Suite
PRO. This bridge fetches raw IQ data from RTSA's HTTP Server block, pipes it
to decoder subprocesses, and serves the resulting JSON on the same TCP ports
that Urchin expects from an sdr-pi setup.

Prerequisites:
  - RTSA Suite PRO running with a mission:
      Spectran V6 Block → IQ Demodulator → HTTP Server
  - rtl_433 installed (for TPMS/POCSAG decoding)
  - dump1090 installed (for ADS-B decoding, optional)

Usage:
  python3 scripts/spectran-bridge.py
  python3 scripts/spectran-bridge.py --rtsa-url http://192.168.1.50:54664/stream
  python3 scripts/spectran-bridge.py --center-freq 433.92 --sample-rate 2048000
  python3 scripts/spectran-bridge.py --decoders rtl_433 dump1090
  python3 scripts/spectran-bridge.py --rtl433-port 1234 --adsb-port 30003

Connect from Urchin: source=NETWORK, host=<this-IP>, port=1234
"""

import argparse
import json
import socket
import struct
import subprocess
import sys
import threading
import time
import urllib.request
import urllib.error

# ─── IQ Format Conversion ──────────────────────────────────────────────────────

def float32_to_cu8(float32_data):
    """Convert raw float32 IQ samples to unsigned 8-bit (cu8) format.

    RTSA Suite PRO streams float32 IQ (range roughly -1.0 to +1.0).
    rtl_433 expects cu8 (unsigned 8-bit, center at 127.5).

    Args:
        float32_data: bytes of float32 IQ samples (little-endian)

    Returns:
        bytes of cu8 IQ samples
    """
    num_floats = len(float32_data) // 4
    # Trim to whole float32 boundary
    trimmed = float32_data[:num_floats * 4]
    if not trimmed:
        return b""

    floats = struct.unpack(f"<{num_floats}f", trimmed)
    cu8 = bytearray(num_floats)
    for i, val in enumerate(floats):
        # Clamp to [-1.0, 1.0] then scale to [0, 255]
        clamped = max(-1.0, min(1.0, val))
        cu8[i] = int(clamped * 127.5 + 127.5)
    return bytes(cu8)


# ─── RTSA HTTP IQ Client ───────────────────────────────────────────────────────

class RtsaIqClient:
    """Streams raw IQ data from RTSA Suite PRO's HTTP Server block.

    Connects to the HTTP endpoint, reads chunked binary data, and feeds it
    to registered consumers (decoder subprocesses).
    """

    def __init__(self, url, chunk_size=65536, convert_to_cu8=True):
        self.url = url
        self.chunk_size = chunk_size
        self.convert_to_cu8 = convert_to_cu8
        self._consumers = []
        self._lock = threading.Lock()
        self._running = False
        self._thread = None

    def add_consumer(self, write_fn):
        """Register a consumer callback that receives IQ byte chunks."""
        with self._lock:
            self._consumers.append(write_fn)

    def remove_consumer(self, write_fn):
        """Unregister a consumer callback."""
        with self._lock:
            self._consumers = [c for c in self._consumers if c is not write_fn]

    def start(self):
        """Start streaming in a background thread with automatic reconnection."""
        self._running = True
        self._thread = threading.Thread(target=self._stream_loop, daemon=True)
        self._thread.start()

    def stop(self):
        """Stop the streaming thread."""
        self._running = False

    def _stream_loop(self):
        """Connect to RTSA HTTP endpoint and stream IQ data, reconnecting on failure."""
        backoff = 1.0
        max_backoff = 30.0

        while self._running:
            try:
                print(f"[RTSA] Connecting to {self.url} ...")
                req = urllib.request.Request(self.url)
                with urllib.request.urlopen(req, timeout=30) as response:
                    print(f"[RTSA] Connected (status {response.status})")
                    backoff = 1.0  # Reset on successful connection

                    while self._running:
                        chunk = response.read(self.chunk_size)
                        if not chunk:
                            print("[RTSA] Stream ended (empty read)")
                            break

                        if self.convert_to_cu8:
                            chunk = float32_to_cu8(chunk)
                            if not chunk:
                                continue

                        with self._lock:
                            dead = []
                            for consumer in self._consumers:
                                try:
                                    consumer(chunk)
                                except (BrokenPipeError, OSError):
                                    dead.append(consumer)
                            for d in dead:
                                self._consumers.remove(d)

            except urllib.error.URLError as e:
                print(f"[RTSA] Connection failed: {e.reason}")
            except Exception as e:
                print(f"[RTSA] Error: {e}")

            if self._running:
                print(f"[RTSA] Reconnecting in {backoff:.0f}s ...")
                time.sleep(backoff)
                backoff = min(backoff * 2, max_backoff)


# ─── Decoder Subprocess Management ─────────────────────────────────────────────

class DecoderProcess:
    """Manages a decoder subprocess (rtl_433 or dump1090) that reads IQ from
    stdin and outputs JSON on stdout."""

    def __init__(self, name, command, on_json_line):
        """
        Args:
            name: Display name for logging (e.g. "rtl_433")
            command: Command list to execute (e.g. ["rtl_433", "-r", "-", ...])
            on_json_line: Callback invoked with each decoded JSON line (str)
        """
        self.name = name
        self.command = command
        self.on_json_line = on_json_line
        self._process = None
        self._running = False
        self._stdout_thread = None
        self._stderr_thread = None

    def start(self):
        """Start the decoder subprocess."""
        self._running = True
        print(f"[{self.name}] Starting: {' '.join(self.command)}")
        try:
            self._process = subprocess.Popen(
                self.command,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
        except FileNotFoundError:
            print(f"[{self.name}] ERROR: '{self.command[0]}' not found. "
                  f"Install it or use --{self.name.lower().replace(' ', '-')}-path")
            self._running = False
            return False

        self._stdout_thread = threading.Thread(
            target=self._read_stdout, daemon=True
        )
        self._stdout_thread.start()

        self._stderr_thread = threading.Thread(
            target=self._read_stderr, daemon=True
        )
        self._stderr_thread.start()

        print(f"[{self.name}] Started (PID {self._process.pid})")
        return True

    def write_iq(self, data):
        """Write IQ data to the subprocess stdin."""
        if self._process and self._process.stdin:
            try:
                self._process.stdin.write(data)
                self._process.stdin.flush()
            except (BrokenPipeError, OSError):
                pass

    def stop(self):
        """Stop the decoder subprocess."""
        self._running = False
        if self._process:
            try:
                self._process.stdin.close()
            except OSError:
                pass
            self._process.terminate()
            try:
                self._process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self._process.kill()
            print(f"[{self.name}] Stopped")

    def _read_stdout(self):
        """Read JSON lines from subprocess stdout."""
        try:
            for line in self._process.stdout:
                line = line.decode("utf-8", errors="replace").strip()
                if not line:
                    continue
                # Validate it's JSON before forwarding
                try:
                    json.loads(line)
                    self.on_json_line(line)
                except json.JSONDecodeError:
                    pass  # Skip non-JSON output (startup messages, etc.)
        except (OSError, ValueError):
            pass
        if self._running:
            print(f"[{self.name}] stdout closed unexpectedly")

    def _read_stderr(self):
        """Log stderr output from the subprocess."""
        try:
            for line in self._process.stderr:
                line = line.decode("utf-8", errors="replace").strip()
                if line:
                    print(f"[{self.name}] {line}")
        except (OSError, ValueError):
            pass


# ─── TCP Relay Server ───────────────────────────────────────────────────────────

class TcpRelay:
    """TCP server that broadcasts lines to all connected clients.

    Each decoded JSON line from a decoder is sent to every connected Urchin
    client.
    """

    def __init__(self, port, name):
        self.port = port
        self.name = name
        self._clients = []
        self._lock = threading.Lock()
        self._server = None

    def start(self):
        """Start accepting TCP connections."""
        self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._server.bind(("0.0.0.0", self.port))
        self._server.listen(5)
        print(f"  [{self.name}] Listening on port {self.port}")

        thread = threading.Thread(target=self._accept_loop, daemon=True)
        thread.start()

    def broadcast(self, line):
        """Send a JSON line to all connected clients."""
        data = (line + "\n").encode("utf-8")
        with self._lock:
            dead = []
            for client in self._clients:
                try:
                    client.sendall(data)
                except (BrokenPipeError, ConnectionResetError, OSError):
                    dead.append(client)
            for d in dead:
                self._clients.remove(d)
                try:
                    d.close()
                except OSError:
                    pass

    def stop(self):
        """Stop the server and disconnect all clients."""
        if self._server:
            self._server.close()
        with self._lock:
            for client in self._clients:
                try:
                    client.close()
                except OSError:
                    pass
            self._clients.clear()

    def _accept_loop(self):
        """Accept incoming TCP connections."""
        while True:
            try:
                conn, addr = self._server.accept()
                print(f"  [{self.name}] Client connected: {addr}")
                with self._lock:
                    self._clients.append(conn)
            except OSError:
                break


# ─── Main ───────────────────────────────────────────────────────────────────────

def build_rtl433_command(args):
    """Build the rtl_433 command for reading IQ from stdin."""
    cmd = [
        args.rtl433_path,
        "-r", "-",                           # Read IQ from stdin
        "-s", str(args.sample_rate),          # Sample rate
        "-f", str(int(args.center_freq * 1e6)),  # Center frequency in Hz
        "-F", "json",                         # Output JSON to stdout
    ]
    return cmd


def build_dump1090_command(args):
    """Build the dump1090 command for reading IQ from stdin."""
    cmd = [
        args.dump1090_path,
        "--ifile", "-",                       # Read IQ from stdin
        "--iformat", "CU8",                   # Unsigned 8-bit IQ format
        "--net",                              # Enable network output
        "--net-sbs-port", str(args.adsb_port),  # SBS output port
        "--quiet",                            # Reduce console noise
    ]
    return cmd


def main():
    parser = argparse.ArgumentParser(
        description="Aaronia SPECTRAN V6 bridge for Urchin — streams decoded "
                    "RF data from RTSA Suite PRO to Urchin's Network mode"
    )
    parser.add_argument(
        "--rtsa-url", default="http://localhost:54664/stream",
        help="RTSA Suite PRO HTTP Server endpoint for raw IQ streaming "
             "(default: http://localhost:54664/stream)"
    )
    parser.add_argument(
        "--center-freq", type=float, default=433.92,
        help="Center frequency in MHz (default: 433.92)"
    )
    parser.add_argument(
        "--sample-rate", type=int, default=2048000,
        help="IQ sample rate in Hz (default: 2048000)"
    )
    parser.add_argument(
        "--decoders", nargs="+", default=["rtl_433"],
        choices=["rtl_433", "dump1090"],
        help="Decoders to run (default: rtl_433)"
    )
    parser.add_argument(
        "--rtl433-port", type=int, default=1234,
        help="TCP port for rtl_433 JSON output (default: 1234)"
    )
    parser.add_argument(
        "--adsb-port", type=int, default=30003,
        help="TCP port for ADS-B JSON output (default: 30003)"
    )
    parser.add_argument(
        "--rtl433-path", default="rtl_433",
        help="Path to rtl_433 binary (default: rtl_433)"
    )
    parser.add_argument(
        "--dump1090-path", default="dump1090",
        help="Path to dump1090 binary (default: dump1090)"
    )
    parser.add_argument(
        "--raw-iq", action="store_true",
        help="Skip float32→cu8 conversion (use if RTSA is already outputting cu8)"
    )
    args = parser.parse_args()

    decoders_set = set(args.decoders)
    relays = []
    decoder_procs = []

    print("Urchin SPECTRAN V6 Bridge")
    print(f"  RTSA URL:     {args.rtsa_url}")
    print(f"  Center freq:  {args.center_freq} MHz")
    print(f"  Sample rate:  {args.sample_rate} Hz")
    print(f"  Decoders:     {', '.join(sorted(decoders_set))}")
    print(f"  IQ format:    {'raw passthrough' if args.raw_iq else 'float32 → cu8'}")
    print()

    # Set up RTSA IQ client
    iq_client = RtsaIqClient(
        url=args.rtsa_url,
        convert_to_cu8=not args.raw_iq,
    )

    # Set up rtl_433 decoder + relay
    if "rtl_433" in decoders_set:
        rtl433_relay = TcpRelay(args.rtl433_port, "rtl_433")
        rtl433_relay.start()
        relays.append(rtl433_relay)

        rtl433_cmd = build_rtl433_command(args)
        rtl433_proc = DecoderProcess("rtl_433", rtl433_cmd, rtl433_relay.broadcast)
        if rtl433_proc.start():
            iq_client.add_consumer(rtl433_proc.write_iq)
            decoder_procs.append(rtl433_proc)
        else:
            print("\nERROR: Failed to start rtl_433. Exiting.")
            sys.exit(1)

    # Set up dump1090 decoder + relay
    if "dump1090" in decoders_set:
        # dump1090 manages its own network output, so we don't need a separate
        # TCP relay — it listens on --net-sbs-port directly. But we set up
        # a relay anyway for consistency with how Urchin expects to connect.
        dump1090_relay = TcpRelay(args.adsb_port, "dump1090")
        dump1090_relay.start()
        relays.append(dump1090_relay)

        dump1090_cmd = build_dump1090_command(args)
        dump1090_proc = DecoderProcess("dump1090", dump1090_cmd, dump1090_relay.broadcast)
        if dump1090_proc.start():
            iq_client.add_consumer(dump1090_proc.write_iq)
            decoder_procs.append(dump1090_proc)
        else:
            print("\nWARNING: Failed to start dump1090. ADS-B decoding disabled.")

    if not decoder_procs:
        print("\nERROR: No decoders running. Exiting.")
        sys.exit(1)

    # Start IQ streaming
    iq_client.start()
    print("  Waiting for connections from Urchin ...")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down ...")
    finally:
        iq_client.stop()
        for proc in decoder_procs:
            proc.stop()
        for relay in relays:
            relay.stop()
        print("Done.")


if __name__ == "__main__":
    main()
