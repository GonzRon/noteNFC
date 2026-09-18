#!/usr/bin/env bash
# The local, no-secrets, no-network equivalent of .github/workflows/release.yml (target §8):
# assert the submodule pin, run the full test gate, build the release APK, and — when signing
# material is present — verify its certificate and version the same way the workflow does.
#
# Self-test hook: RELEASE_DRY_RUN_APK overrides the APK path this script inspects, so the later
# checks (apksigner / version / checksum) can be exercised on this machine even when there is no
# release signing material yet — e.g. against a debug build:
#   RELEASE_DRY_RUN_APK=app/build/outputs/apk/debug/app-debug.apk bash tools/release-dry-run.sh
# This override is a self-test convenience ONLY. It is never used for an actual release: a real
# release always inspects the real app-release.apk produced by :app:assembleRelease, and this
# variable must never be set when cutting a release.
#
# This app's two marked constants (the NoteTag copy of this script differs only in these two
# lines):
APP_DIR=servicetag
APK_BASENAME=ServiceTag

set -euo pipefail
cd "$(dirname "$0")/.."

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
bt="$ANDROID_HOME/build-tools/36.0.0"

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT INT TERM

echo "== the shared library is pinned at an exact tag =="
bash tools/check-submodule-pin.sh

echo "== the complete test gate =="
./gradlew :nfc-core:test :nfc-android:testDebugUnitTest :core:test :app:testDebugUnitTest --console=plain

echo "== build the release APK =="
./gradlew :app:assembleRelease --console=plain

apk="${RELEASE_DRY_RUN_APK:-app/build/outputs/apk/release/app-release.apk}"
self_test=0
if [ -n "${RELEASE_DRY_RUN_APK:-}" ]; then
  self_test=1
  echo "self-test hook: inspecting $apk (never a release path; the verdict is capped at PARTIAL)"
  if [ ! -f "$apk" ]; then
    echo "RELEASE DRY RUN: FAIL — RELEASE_DRY_RUN_APK names a file that does not exist"
    exit 1
  fi
elif [ ! -f "$apk" ]; then
  echo "BLOCKED: no signing material (target §8)"
  exit 3
fi
echo "inspecting: $apk"

echo "== the APK is signed =="
certs="$scratch/certs.txt"
"$bt/apksigner" verify --print-certs "$apk" > "$certs"
if [ "$(grep -c 'SHA-256 digest' "$certs")" != 1 ]; then
  echo "RELEASE DRY RUN: FAIL — expected exactly one signer"
  exit 1
fi

actual=$(grep -m1 'SHA-256 digest' "$certs" | awk '{print $NF}' | tr -d ':' | tr 'a-f' 'A-F')

expected=""
if [ -n "${RELEASE_CERT_SHA256:-}" ]; then
  expected=$(printf '%s' "$RELEASE_CERT_SHA256" | tr -d ':' | tr 'a-f' 'A-F')
elif [ -f "$HOME/.config/$APP_DIR/release-cert-sha256.txt" ]; then
  expected=$(tr -d ':\n' < "$HOME/.config/$APP_DIR/release-cert-sha256.txt" | tr 'a-f' 'A-F')
fi

fingerprint_status="skipped (no expected value configured)"
identity_checked=0
if [ -n "$expected" ]; then
  if [ "$actual" = "$expected" ]; then
    fingerprint_status="matches"
    # An override is never an identity proof of the artifact assembleRelease produced.
    [ "$self_test" -eq 0 ] && identity_checked=1
  else
    fingerprint_status="differs"
    echo "fingerprint compare: $fingerprint_status"
    echo "RELEASE DRY RUN: FAIL — certificate fingerprint differs"
    exit 1
  fi
fi
echo "fingerprint compare: $fingerprint_status"

echo "== the version matches =="
built=$("$bt/aapt2" dump badging "$apk" | grep -o "versionName='[^']*'" | cut -d"'" -f2)
declared=$(grep -m1 -E '^[[:space:]]*versionName[[:space:]]*=' app/build.gradle.kts | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')
if [ "$built" != "$declared" ]; then
  echo "version: $declared vs $built differs"
  echo "RELEASE DRY RUN: FAIL — built versionName differs from app/build.gradle.kts"
  exit 1
fi
echo "version: $declared matches $built"

echo "== checksum =="
out="$scratch/${APK_BASENAME}-${built}.apk"
cp "$apk" "$out"
sha256sum "$out" > "$out.sha256"
echo "checksum written to scratch dir: $(basename "$out").sha256"

if [ "$identity_checked" -eq 1 ]; then
  echo "RELEASE DRY RUN: PASS"
  exit 0
else
  echo "RELEASE DRY RUN: PARTIAL — signing identity not independently checked"
  exit 0
fi
