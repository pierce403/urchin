#!/usr/bin/env bash
# Clones third-party SDR dependencies needed for the NDK build.
# Run once before building: ./scripts/setup-third-party.sh

set -euo pipefail
cd "$(dirname "$0")/.."

ROOT_DIR="$(pwd)"
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

apply_patch_if_needed() {
  local dir="$1" patch_rel="$2"
  local repo_dir="$THIRD_PARTY/$dir"
  local patch_path="$ROOT_DIR/$patch_rel"

  if git -C "$repo_dir" apply --check "$patch_path" >/dev/null 2>&1; then
    echo "Applying $(basename "$patch_path") to $dir..."
    git -C "$repo_dir" apply "$patch_path"
  elif git -C "$repo_dir" apply --reverse --check "$patch_path" >/dev/null 2>&1; then
    echo "✓ $(basename "$patch_path") already applied to $dir"
  else
    echo "ERROR: couldn't apply $(basename "$patch_path") to $dir" >&2
    exit 1
  fi
}

clone_if_missing libusb   https://github.com/libusb/libusb.git
clone_if_missing rtl-sdr  https://github.com/osmocom/rtl-sdr.git
clone_if_missing rtl_433  https://github.com/merbanan/rtl_433.git
apply_patch_if_needed rtl-sdr third_party/patches/rtl-sdr-android-usb-fd.patch
apply_patch_if_needed rtl_433 third_party/patches/rtl433-stderr-logging.patch

echo ""
echo "Done. Third-party sources are in $THIRD_PARTY/"
echo "Run ./gradlew assembleDebug to build."
