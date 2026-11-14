#!/usr/bin/env bash
# Runs the on-device tests (androidTest) on a connected phone against the LOCAL Firebase
# emulator, and leaves the app installed with your real login intact.
#
#   scripts/run-device-tests.sh <adb-serial> [test class or class#method]
#   scripts/run-device-tests.sh 001206477020888
#   scripts/run-device-tests.sh 001206477020888 com.dhruvk.parkover.home              (a package)
#   scripts/run-device-tests.sh 001206477020888 com.dhruvk.parkover.home.HomeRouteTest (a class)
#
# How: starts the Firestore + Auth emulators, forwards their ports to the phone over USB
# (adb reverse), backs up the app's saved Firebase login, runs the tests, then restores it.
# Results go to test-results/<timestamp>/ (instrumentation output, plus a screenshot and
# logcat for each failed test). Needs Java 21 for the emulator (Android Studio's JBR works).
set -euo pipefail

SERIAL="${1:?Usage: $0 <adb-serial> [class]}"
TARGET="${2:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG="com.dhruvk.parkover"
OUT="$ROOT/test-results/$(date +%Y%m%d-%H%M%S)"
ADB=(adb -s "$SERIAL")
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
[[ -d "$JBR" ]] && export JAVA_HOME="$JBR" PATH="$JBR/bin:$PATH"
PROJECT="$(python3 -c "import json;print(json.load(open('$ROOT/app/google-services.json'))['project_info']['project_id'])")"

cd "$ROOT"
mkdir -p "$OUT"
echo "Building app and test APKs…"
./gradlew -q assembleDebug assembleDebugAndroidTest

echo "Installing on $SERIAL…"
"${ADB[@]}" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
"${ADB[@]}" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
"${ADB[@]}" shell rm -rf "/sdcard/Android/data/$PKG/files/test-failures" || true

# Keep the real login: tests sign in throwaway emulator users in the same app storage.
"${ADB[@]}" shell am force-stop "$PKG"
# (one quoted string: adb shell re-splits arguments on the phone)
"${ADB[@]}" shell "run-as $PKG sh -c 'cd /data/data/$PKG && rm -rf files/login-backup && mkdir -p files/login-backup && (cp shared_prefs/com.google.firebase.auth.api.Store.* files/login-backup/ 2>/dev/null; true)'"
BACKED_UP="$("${ADB[@]}" shell "run-as $PKG sh -c 'ls /data/data/$PKG/files/login-backup | wc -l'" | tr -d '[:space:]')"
[[ "$BACKED_UP" == "0" ]] && echo "No saved login on the phone (app is signed out)." || echo "Saved your login."
restore_login() {
  "${ADB[@]}" shell am force-stop "$PKG" || true
  "${ADB[@]}" shell "run-as $PKG sh -c 'cd /data/data/$PKG && if [ -d files/login-backup ]; then rm -f shared_prefs/com.google.firebase.auth.api.Store.*; cp files/login-backup/* shared_prefs/ 2>/dev/null; rm -rf files/login-backup; fi'" || true
  echo "Restored your login."
  "${ADB[@]}" reverse --remove tcp:8085 >/dev/null 2>&1 || true
  "${ADB[@]}" reverse --remove tcp:9099 >/dev/null 2>&1 || true
}
trap restore_login EXIT

"${ADB[@]}" reverse tcp:8085 tcp:8085 >/dev/null
"${ADB[@]}" reverse tcp:9099 tcp:9099 >/dev/null

INSTRUMENT="adb -s $SERIAL shell am instrument -w -r"
if [[ -n "$TARGET" ]]; then
  last="${TARGET##*.}"
  # com.x.home → package; com.x.home.HomeRouteTest or …#method → class
  if [[ "$TARGET" != *"#"* && "${last:0:1}" =~ [a-z] ]]; then INSTRUMENT="$INSTRUMENT -e package $TARGET"
  else INSTRUMENT="$INSTRUMENT -e class $TARGET"; fi
fi
INSTRUMENT="$INSTRUMENT $PKG.test/$PKG.testing.ParkOverTestRunner > '$OUT/instrument.txt'"

echo "Starting Firebase emulator ($PROJECT) and running tests…"
firebase emulators:exec --only firestore,auth --project "$PROJECT" --config "$ROOT/firebase.json" "$INSTRUMENT" \
  > "$OUT/emulator.txt" 2>&1 || true

grep -E "^INSTRUMENTATION_STATUS: test=|^INSTRUMENTATION_STATUS_CODE: -2|^OK \(|^FAILURES|^Tests run" "$OUT/instrument.txt" \
  | awk '/test=/{t=$0; sub(/.*test=/,"",t)} /CODE: -2/{print "  ✗ " t} /^OK|^FAILURES|^Tests run/{print}' | uniq || true

"${ADB[@]}" pull "/sdcard/Android/data/$PKG/files/test-failures" "$OUT/" >/dev/null 2>&1 || true

if grep -q "^OK (" "$OUT/instrument.txt" 2>/dev/null; then
  echo "PASSED: $(grep '^OK (' "$OUT/instrument.txt")  →  $OUT"
else
  echo "FAILED: see $OUT/instrument.txt, $OUT/emulator.txt and $OUT/test-failures/"
  exit 1
fi
