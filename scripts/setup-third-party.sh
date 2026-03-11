#!/usr/bin/env bash
# Clones third-party SDR dependencies needed for the NDK build.
# Run once before building: ./scripts/setup-third-party.sh

set -euo pipefail
cd "$(dirname "$0")/.."

THIRD_PARTY=third_party
mkdir -p "$THIRD_PARTY"

clone_if_missing() {
  local dir="$1" url="$2"
  if [ -d "$THIRD_PARTY/$dir" ]; then
    echo "✓ $dir already cloned"
  else
    echo "Cloning $dir..."
    git clone --depth 1 "$url" "$THIRD_PARTY/$dir"
  fi
}

clone_if_missing libusb   https://github.com/libusb/libusb.git
clone_if_missing rtl-sdr  https://github.com/osmocom/rtl-sdr.git
clone_if_missing rtl_433  https://github.com/merbanan/rtl_433.git

echo ""
echo "Done. Third-party sources are in $THIRD_PARTY/"
echo "Run ./gradlew assembleDebug to build."
