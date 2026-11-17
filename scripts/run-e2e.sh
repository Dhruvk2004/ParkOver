#!/usr/bin/env bash
# E2E smoke tests: the real app on a real phone against the LIVE dev Firebase project
# (real network, GPS, routing, search, rules). Each test uses brand-new throwaway accounts and
# cleans up after itself (bookings cancelled, vehicles/profile/accounts deleted).
#
#   scripts/run-e2e.sh <adb-serial>            (all stages)
#   scripts/run-e2e.sh <adb-serial> first      (first launch + location denied)
#   scripts/run-e2e.sh <adb-serial> smoke      (smoke, network, offline, login offline, GPS off)
#
# Safety: backs up and restores your real login; records Wi-Fi and mobile data and turns them
# back on even if a test fails; leaves location permission granted.
set -euo pipefail

SERIAL="${1:?Usage: $0 <adb-serial> [first|smoke]}"
ONLY="${2:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG="com.dhruvk.parkover"
OUT="$ROOT/test-results/e2e-$(date +%Y%m%d-%H%M%S)"
ADB=(adb -s "$SERIAL")
RUNNER="$PKG.test/$PKG.testing.ParkOverTestRunner"

cd "$ROOT"
mkdir -p "$OUT"
echo "Building app and test APKs…"
./gradlew -q assembleDebug assembleDebugAndroidTest
echo "Installing on $SERIAL…"
"${ADB[@]}" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
"${ADB[@]}" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
"${ADB[@]}" shell rm -rf "/sdcard/Android/data/$PKG/files/test-failures" || true

WIFI_WAS_ON="$("${ADB[@]}" shell settings get global wifi_on | tr -d '[:space:]')"
LOCATION_WAS_ON="$("${ADB[@]}" shell cmd location is-location-enabled | tr -d '[:space:]')"
DATA_WAS_ON="$("${ADB[@]}" shell settings get global mobile_data | tr -d '[:space:]')"

"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell "run-as $PKG sh -c 'cd /data/data/$PKG && rm -rf files/login-backup && mkdir -p files/login-backup && (cp shared_prefs/com.google.firebase.auth.api.Store.* files/login-backup/ 2>/dev/null; true)'"
echo "Saved your login."

cleanup() {
  for perm in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
    "${ADB[@]}" shell pm clear-permission-flags "$PKG" "android.permission.$perm" user-set user-fixed >/dev/null 2>&1 || true
  done
  [[ "$WIFI_WAS_ON" != "0" ]] && "${ADB[@]}" shell svc wifi enable || true
  [[ "$LOCATION_WAS_ON" == "true" ]] && "${ADB[@]}" shell cmd location set-location-enabled true || true
  [[ "$DATA_WAS_ON" == "1" ]] && "${ADB[@]}" shell svc data enable || true
  "${ADB[@]}" shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
  "${ADB[@]}" shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION || true
  "${ADB[@]}" shell am force-stop "$PKG" || true
  "${ADB[@]}" shell "run-as $PKG sh -c 'cd /data/data/$PKG && if [ -d files/login-backup ]; then rm -f shared_prefs/com.google.firebase.auth.api.Store.*; cp files/login-backup/* shared_prefs/ 2>/dev/null; rm -rf files/login-backup; fi'" || true
  echo "Restored your login, network, location setting and permission."
}
trap cleanup EXIT

reset_location_permission() { # revoke AND clear "don't ask again", so Android really asks
  for perm in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
    "${ADB[@]}" shell pm clear-permission-flags "$PKG" "android.permission.$perm" user-set user-fixed >/dev/null 2>&1 || true
    "${ADB[@]}" shell pm revoke "$PKG" "android.permission.$perm" >/dev/null 2>&1 || true
  done
}

run() { # $1 = class, $2 = output name
  "${ADB[@]}" shell am instrument -w -r -e firebase live -e class "$1" "$RUNNER" > "$OUT/$2.txt" || true
  grep -E "^INSTRUMENTATION_STATUS: test=|^INSTRUMENTATION_STATUS_CODE: -2|^OK \(|^FAILURES|^Tests run" "$OUT/$2.txt" \
    | awk '/test=/{t=$0; sub(/.*test=/,"",t)} /CODE: -2/{print "  ✗ " t} /^OK|^FAILURES|^Tests run/{print "  " $0}' | uniq || true
  grep -q "^OK (" "$OUT/$2.txt"
}

STATUS=0
if [[ "$ONLY" != "smoke" ]]; then
echo "1/2 First launch (location permission reset so Android asks)…"
reset_location_permission
run "$PKG.e2e.E2eFirstLaunchTest" first-launch || STATUS=1
echo "   Location denied (reset again so Android asks)…"
reset_location_permission
run "$PKG.e2e.E2eLocationDeniedTest" location-denied || STATUS=1
fi

if [[ "$ONLY" != "first" ]]; then
echo "2/2 Smoke, network drop/switch, offline launch, login offline, GPS off…"
"${ADB[@]}" shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
"${ADB[@]}" shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION || true
run "$PKG.e2e.E2eSmokeTest,$PKG.e2e.E2eNetworkTest,$PKG.e2e.E2eOfflineLaunchTest,$PKG.e2e.E2eLoginOfflineTest,$PKG.e2e.E2eGpsOffTest" smoke || STATUS=1
fi

"${ADB[@]}" pull "/sdcard/Android/data/$PKG/files/test-failures" "$OUT/" >/dev/null 2>&1 || true
if [[ $STATUS == 0 ]]; then echo "PASSED  →  $OUT"; else echo "FAILED: see $OUT"; exit 1; fi
