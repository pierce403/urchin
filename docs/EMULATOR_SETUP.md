# Android Emulator Setup

This guide covers creating an AVD, launching the emulator, and connecting SDR
dongles for development and testing.

## Prerequisites

- Android SDK with command-line tools installed (see [SETUP_ANDROID.md](SETUP_ANDROID.md))
- System images installed:

```
sdkmanager "system-images;android-35;google_apis;x86_64" "emulator"
```

## 1. Create an AVD

```
avdmanager create avd \
  -n urchin_test \
  -k "system-images;android-35;google_apis;x86_64" \
  -d pixel_6
```

Accept the default hardware profile when prompted.

## 2. Launch the emulator

```
emulator -avd urchin_test -no-snapshot-load -no-audio
```

Useful flags:

| Flag | Purpose |
| ---- | ------- |
| `-no-snapshot-load` | Cold-boot every time (avoids stale state) |
| `-no-audio` | Skip audio init (faster start on headless hosts) |
| `-gpu swiftshader_indirect` | Software rendering when no GPU is available |

Wait for the device to appear in `adb devices` before continuing.

## 3. Connect SDR dongles

The emulator cannot access USB hardware directly, so SDR data reaches the app
through network bridges.

### Option A: Network bridge with a host-side SDR (recommended)

Run the SDR tool on the host machine (or any reachable host) and use
`adb reverse` to make the host port visible inside the emulator at
`127.0.0.1`.

**TPMS / POCSAG** (rtl_433):

```
# Host — start rtl_433 with the dongle plugged into the host
rtl_433 -F json -S 0 -M level

# Forward port into the emulator
adb reverse tcp:1234 tcp:1234
```

**ADS-B** (dump1090):

```
dump1090 --net --quiet
adb reverse tcp:30003 tcp:30003
```

**P25** (OP25):

```
# Start OP25 with TCP JSON output on port 23456
adb reverse tcp:23456 tcp:23456
```

Then in Urchin, select **Network bridge**, set the host to `127.0.0.1` and the
matching port, and tap **Start**.

### Option B: Raspberry Pi with sdr-pi

Use [sdr-pi](https://github.com/ingmarvg/sdr-pi) to run your SDR dongles on a
Raspberry Pi and stream data over the network. Point Urchin's network bridge at
the Pi's IP address and the corresponding port — no `adb reverse` needed since
the Pi is a real network host.

### Option C: Simulated data (no hardware)

The included simulator generates fake TPMS readings for pipeline testing:

```
python3 scripts/sdr-simulator.py --port 1234
adb reverse tcp:1234 tcp:1234
```

Or run the full smoke test which automates the above:

```
bash scripts/smoke-test-sdr.sh
```

## 4. Verify the connection

1. Open Urchin on the emulator.
2. Select **Network bridge** and enter `127.0.0.1` with the port for your
   protocol.
3. Tap **Start** — observations should begin appearing in the list.
4. Open **Diagnostics** from the menu to confirm callback counts and
   connection state.

## Troubleshooting

| Symptom | Fix |
| ------- | --- |
| `adb reverse` fails | Ensure only one emulator/device is connected, or target with `adb -s emulator-5554 reverse ...` |
| Emulator exits immediately on launch | Try `-no-snapshot-load` or `-wipe-data` to reset state |
| No observations after starting scan | Check that the host-side tool is running and the port matches; verify with `adb reverse --list` |
| Emulator is slow or unresponsive | Enable hardware acceleration (`-accel on`) and ensure HAXM/KVM is installed |
