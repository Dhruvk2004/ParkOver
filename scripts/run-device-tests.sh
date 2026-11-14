#!/usr/bin/env bash
# Runs the on-device tests (androidTest) on a connected phone and keeps the app installed,
# unlike `./gradlew connectedDebugAndroidTest`, which uninstalls it and logs you out.
#
#   scripts/run-device-tests.sh <adb-serial> [test class or class#method]
#   scripts/run-device-tests.sh 001206477020888
#   scripts/run-device-tests.sh 001206477020888 com.dhruvk.parkover.home.HomeRouteTest
#
# Results go to test-results/<timestamp>/ (instrumentation output, plus screenshots and
# logcat for any failed test).
set -euo pipefail

SERIAL="${1:?Usage: $0 <adb-serial> [class]}"
TARGET="${2:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG="com.dhruvk.parkover"
OUT="$ROOT/test-results/$(date +%Y%m%d-%H%M%S)"
ADB=(adb -s "$SERIAL")

cd "$ROOT"
mkdir -p "$OUT"
echo "Building app and test APKs…"
./gradlew -q assembleDebug assembleDebugAndroidTest

echo "Installing on $SERIAL…"
"${ADB[@]}" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
"${ADB[@]}" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
"${ADB[@]}" shell rm -rf "/sdcard/Android/data/$PKG/files/test-failures" || true

ARGS=(-w -r)
[[ -n "$TARGET" ]] && ARGS+=(-e class "$TARGET")
echo "Running tests…"
"${ADB[@]}" shell am instrument "${ARGS[@]}" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrument.txt" \
  | grep -E "^INSTRUMENTATION_STATUS: (class|test)=|^INSTRUMENTATION_STATUS_CODE: -2|^OK \(|^FAILURES|^Tests run" || true

"${ADB[@]}" pull "/sdcard/Android/data/$PKG/files/test-failures" "$OUT/" >/dev/null 2>&1 || true

if grep -q "^OK (" "$OUT/instrument.txt"; then
  echo "PASSED: $(grep '^OK (' "$OUT/instrument.txt")  →  $OUT"
else
  echo "FAILED: see $OUT/instrument.txt and $OUT/test-failures/"
  exit 1
fi
