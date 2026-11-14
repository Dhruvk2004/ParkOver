# ParkOver testing plan

How we try to break every feature before a real user does. The approach is adapted from the CometChat Flutter UIKit master app, translated to Kotlin + Jetpack Compose and to ParkOver's own risks: maps, GPS, live parking availability and bookings.

Status: **plan only**. Nothing below is built yet except two placeholder tests (`ExampleUnitTest`, `ExampleInstrumentedTest`).

---

## 1. Principles

1. **Every scenario has an ID** (`BOOK-07`) and a layer. Test names carry the ID, e.g. `BOOK_07_lastSpotTwoUsers_onlyOneSucceeds`, and a coverage table maps IDs to test files.
2. **Test at the lowest layer that can catch the bug.** Logic goes in unit tests. Real fonts, insets and the map go on the device. Only whole user journeys go in E2E.
3. **Wait for a condition, never for "idle".** The map, Firestore listeners and GPS never settle. Use `composeRule.waitUntil(timeout) { … }` or `onNode(…).assertExists()` with polling, never `waitForIdle()` on Home or booking screens.
4. **Find things by test tag or text, never coordinates.** Add `Modifier.testTag("…")` to key controls. Map pins are not Compose nodes, so tests select a pin through a debug hook (see §3.5), not by tapping pixels.
5. **Tests own their data.** Each run seeds what it needs, tags it with a unique run token, and deletes it afterwards. Never "pick the first booking": find yours or fail.
6. **No real passwords typed by tests.** Logins use dedicated test accounts read from `local.properties` / CI secrets, the same way as the Dev login button.
7. **Strict on structure, tolerant on timing.** A missing button fails the test. A route that takes 4 s instead of 2 s is logged, not failed.
8. **Never hammer free public services.** OpenRouteService (2,000 routes and 500 matrix calls a day), Photon and OpenFreeMap are faked in every layer except a handful of E2E smoke tests.

---

## 2. The four layers

| Layer | What it proves | Tools | Runs on | When |
|---|---|---|---|---|
| **1. Unit** | Logic: pricing, validation, conflict maths, JSON parsing, view-model state | JUnit 4, MockK, kotlinx-coroutines-test, Turbine (Flow), MockWebServer (HTTP parsing), Robolectric where Android classes are needed | Mac / CI, no device | Every commit (`./gradlew testDebugUnitTest`) |
| **2. Integration** (on device) | Screens with real rendering, real map, real permissions plumbing, wired to **fakes or the Firebase Emulator**; Firestore security rules | Compose UI Test (`createAndroidComposeRule`), Hilt-free manual DI swap, Firebase Local Emulator Suite, `@firebase/rules-unit-testing` for rules | Nothing A015 / emulator | Every PR (`./gradlew connectedDebugAndroidTest`) |
| **3. E2E** (real app, live test backend) | Full journeys exactly as a user does them: login → find parking → route → book → QR → cancel, against a real Firebase **test project** | Compose UI Test for in-app steps + UIAutomator for system UI (permission dialogs, Google Maps hand-off, notifications); optional Maestro flows for black-box smoke | Real phones (Nothing primary, Xiaomi secondary) | Before every merge to `main` and before every release, from the Mac |
| **4. Load** (many users) | The backend and data model hold up: concurrent bookings on the last spot, listener fan-out, quota limits | k6 (or Gatling) driving the Firestore REST API + Auth REST API with many test users; a headless "second driver" helper for two-user device tests | Mac / CI → **separate load-test Firebase project** | Before launch and before big data changes, never against production |

Security (rules, App Check, secrets) runs inside layers 1–2 and is listed separately in §4.15 because it matters as much as the rest.

---

## 3. Test infrastructure to build first

### 3.1 Firebase projects
| Project | Use |
|---|---|
| Firebase **Local Emulator Suite** (Auth + Firestore) | Layer 2 integration and rules tests. Free, offline, resettable per test. |
| `parkover-b042b` (current) | Development and manual testing only. |
| **New `parkover-test` project** | Layer 3 E2E. Same rules, seeded test parkings, test accounts. Keeps E2E junk out of the dev project. |
| **New `parkover-load` project** | Layer 4 only. Spark plan read/write limits apply, so load runs are sized to stay inside the free quota or run on the emulator for logic and a short burst on the real project for timing. |

### 3.2 Test accounts
- `e2e-driver-a@…`, `e2e-driver-b@…` (two-user flows), `e2e-empty@…` (no vehicles, no bookings), `e2e-full@…` (many bookings/vehicles for list and paging tests).
- Created once by a seed script through the Admin SDK. Passwords live in `local.properties` / CI secrets as `E2E_*` keys, injected into `BuildConfig` for the `androidTest` source set only.

### 3.3 Seams for fakes (small refactor)
Today the repositories are singletons/objects that call the network directly. Add an interface in front of each external service and pick the implementation in one place:

| Seam | Real | Fakes |
|---|---|---|
| `RoutingService` | ORS / OSRM | success, slow (5 s), HTTP 406/429/500, timeout, empty route, malformed JSON |
| `PlaceSearchService` | Photon | results, empty, error, slow |
| `ParkingSource` | GitHub JSON (later Firestore) | N spots, 0 spots, malformed, error |
| `LocationProvider` | Fused location | fixed point, no fix, slow fix (8 s), moving path, permission denied |
| `Clock` | System time | fixed time (for "check-in has passed", midnight, DST-free IST) |

### 3.4 Device control helpers (shell + Kotlin)
- **Network:** `adb shell svc wifi disable|enable`, `adb shell svc data disable|enable`, `adb shell cmd connectivity airplane-mode enable|disable`. A `NetworkRule` toggles these around a test.
- **Slow network:** Android emulator `-netdelay`/`-netspeed`; on real phones a throttling proxy (Charles / mitmproxy) for E2E-only cases.
- **Permissions:** `adb shell pm grant|revoke com.dhruvk.parkover android.permission.ACCESS_FINE_LOCATION`; `GrantPermissionRule` in Compose tests; UIAutomator for the cases that must see the real dialog.
- **Location:** the fake `LocationProvider` for layers 1–2; for E2E, a mock-location provider app selected in Developer options plus `adb shell appops set com.dhruvk.parkover android:mock_location allow`.
- **Process death:** `adb shell am kill com.dhruvk.parkover` while backgrounded, then relaunch from recents.
- **Clean state:** `adb shell pm clear com.dhruvk.parkover` before login/onboarding tests.
- **Screenshots and logs on failure:** a JUnit rule that saves a screenshot plus `logcat --pid` for the failed test.

### 3.5 Map test hooks (debug builds only)
Pins are MapLibre symbols, not Compose nodes. Expose a debug-only `ParkMapTestHooks`: `pinIds()`, `selectPin(id)`, `routePointCount()`, `cameraBounds()`, `isStyleLoaded()`. Tests assert on these instead of pixels.

### 3.6 Runner
`scripts/run-e2e.sh <serial>` runs each suite, writes `test-results/<timestamp>/` with per-suite logs, screenshots and a `SUMMARY.md`, and can resume (skips suites that already passed in that folder). Live E2E runs from the Mac, not CI emulators. CI runs layers 1–2 (emulator) only.

### 3.7 Device matrix
| Device | Why |
|---|---|
| Nothing A015 (Android 15) | Primary E2E phone, no automation blockers |
| Xiaomi 2409FPCC4I (HyperOS) | Aggressive battery/permission behaviour. Needs "USB debugging (Security settings)" for taps |
| Emulator API 24 | `minSdk` 24: oldest supported behaviour |
| Emulator API 36 | `targetSdk` 36: newest permission/edge-to-edge behaviour |
| Small screen emulator (e.g. 5", 360 dp) + large font (1.3×) | Text overflow, sheets covering buttons |

---

## 4. Scenario catalogue

Layer codes: **U** unit · **I** integration (device + fakes/emulator) · **E** E2E (real device + test backend) · **L** load · **S** security.
Priority: **P0** must pass before any release · **P1** before public launch · **P2** nice to have.

### 4.1 Splash, start and auth (AUTH)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| AUTH-01 | Fresh install, open app | Splash → Start; status-bar icons readable on each | I | P0 |
| AUTH-02 | Logged-in user reopens app | Splash → Home, no Login flash | E | P0 |
| AUTH-03 | Login with valid test account | Lands on Home with name/initial in header | E | P0 |
| AUTH-04 | Wrong password | Clear inline error, fields kept, no crash | I | P0 |
| AUTH-05 | Unknown email | Friendly error, not raw Firebase text | I | P1 |
| AUTH-06 | Email/password validation (empty, spaces, bad format, short password) | Field-level errors; button disabled or blocked | U | P0 |
| AUTH-07 | Sign up new user | User doc created in `users/{uid}`; lands on Home | E | P0 |
| AUTH-08 | Sign up with existing email | "Account exists" message | I | P1 |
| AUTH-09 | Forgot password | Reset email request succeeds; message shown | E | P2 |
| AUTH-10 | Google sign-in | Account picker (UIAutomator) → Home | E | P1 |
| AUTH-11 | Login with no network | "No connection" message, retry works after network returns | I | P0 |
| AUTH-12 | Double-tap Login quickly | One sign-in request, one navigation | I | P1 |
| AUTH-13 | Sign out | Back to Start; back button does not return to Home | E | P0 |
| AUTH-14 | Session revoked/disabled account while app open | Next Firestore call fails gracefully → Login | E | P2 |
| AUTH-15 | Dev login button absent in release build | Not in release APK | U (build check) | P0 |

### 4.2 Location and permissions (LOC)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| LOC-01 | First launch, grant "While using" | Map centres on user, header shows English area name | E | P0 |
| LOC-02 | Deny permission | Map shows Mumbai with "Location off" note; app fully usable | I | P0 |
| LOC-03 | "Don't ask again" denied, tap locate button | Explains and opens app settings | E | P1 |
| LOC-04 | Approximate location only | Works with coarse fix; distances still shown | E | P1 |
| LOC-05 | GPS off at system level | Prompt or clear message, no infinite spinner | E | P0 |
| LOC-06 | Fix arrives 8 s after launch | Dot appears, camera centres once, header updates | I | P0 |
| LOC-07 | Geocoder returns Devanagari only | Latin-script fallback, never Hindi in header | U | P1 |
| LOC-08 | Geocoder throws / offline | Header shows "Current location", no crash | U | P1 |
| LOC-09 | Revoke permission while app in background, return | No crash; falls back cleanly | E | P1 |
| LOC-10 | User moves 2 km while on Home | Dot moves; "Nearby" list refreshes on recentre | I | P2 |

### 4.3 Map (MAP)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| MAP-01 | Map style loads | ParkOver colours; `isStyleLoaded()` true within 10 s | I | P0 |
| MAP-02 | Style URL fails (OpenFreeMap down) | Fallback style or clear error, pins still usable as a list | I | P1 |
| MAP-03 | Pins render for every visible spot | `pinIds()` equals filtered spot IDs | I | P0 |
| MAP-04 | Full spot pin | Red "Full" pin | I | P1 |
| MAP-05 | Tap a pin | Spot sheet opens; pin highlighted | I | P0 |
| MAP-06 | Tap empty map | Sheet closes, route cleared | I | P0 |
| MAP-07 | Pinch, pan, rotate disabled where intended | No tilt; compass hidden | I | P2 |
| MAP-08 | Switch tabs and return | Camera stays where it was | I | P1 |
| MAP-09 | Rotate screen / dark mode / font 1.3× | Map survives; header and chips don't overlap | I | P1 |
| MAP-10 | 500 pins | Smooth pan (no ANR); frame time logged | I | P2 |
| MAP-11 | Attribution visible and tappable | OSM/OpenFreeMap credit shown (licence requirement) | I | P1 |
| MAP-12 | Map tiles offline after first load | Cached tiles show; no crash when panning to uncached area | E | P1 |

### 4.4 Filters (FLT)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| FLT-01 | Nearby on/off | Spots beyond radius hidden/shown | U | P0 |
| FLT-02 | Under ₹50 | Only spots ≤ ₹50 | U | P0 |
| FLT-03 | EV, Covered, 24h | Matching amenities only | U | P1 |
| FLT-04 | Combine all filters with no match | Empty state message, not a blank map | I | P1 |
| FLT-05 | Filters survive tab switch | Same chips selected | I | P2 |

### 4.5 Search (SRCH)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| SRCH-01 | Type "Govandi" | Our spots first, then Photon places | I | P0 |
| SRCH-02 | Typing fast | One request after 350 ms debounce, not one per letter | U | P1 |
| SRCH-03 | Tap our spot result | Selects spot, opens sheet, draws route | I | P0 |
| SRCH-04 | Tap a place result | Destination dot, camera moves, nearby spots around destination | I | P0 |
| SRCH-05 | No results | "No places found" | I | P1 |
| SRCH-06 | Photon error/offline | Our own spots still searchable; no crash | I | P0 |
| SRCH-07 | Special characters, emoji, 200-char text | No crash; URL encoded | U | P1 |
| SRCH-08 | Clear search | Destination cleared, back to user location | I | P1 |
| SRCH-09 | Voice search (mic) | Speech dialog (UIAutomator); text filled | E | P2 |

### 4.6 Routing and distance (ROUTE)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| ROUTE-01 | Tap pin with GPS fix | Purple road route, "X km · Y min" by road | E | P0 |
| ROUTE-02 | Tap pin before GPS fix | "Finding your location…", route appears when fix lands | I | P0 |
| ROUTE-03 | No location permission | "Turn on location for distance"; no route from the Mumbai fallback | I | P0 |
| ROUTE-04 | ORS returns 406/429/500 | Straight-line distance labelled "straight-line"; warning logged | U + I | P0 |
| ROUTE-05 | ORS timeout (15 s) | Loading ends; fallback shown | U | P0 |
| ROUTE-06 | Route bends outside its ends | Camera fits whole route above the sheet | I | P1 |
| ROUTE-07 | Tap pin A then quickly pin B | Only B's route shown (A's late reply ignored) | I | P0 |
| ROUTE-08 | Destination searched, then tap pin | Route from destination, not from user | I | P1 |
| ROUTE-09 | ORS key missing in release | No calls to OSRM demo; straight-line fallback | U | P0 |
| ROUTE-10 | Saved tab road distances (matrix) | "X km by road" per saved spot; one matrix call, not N | U + I | P1 |
| ROUTE-11 | "Route" button | Google Maps app opens with destination (UIAutomator); browser fallback if Maps not installed | E | P1 |
| ROUTE-12 | Daily quota exhausted (429) | Fallback for the rest of the day; no retry storm | U | P1 |

### 4.7 Parking data and live availability (SPOT)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| SPOT-01 | Spots load | Pins and sheet data match source | I | P0 |
| SPOT-02 | Spots source unreachable (DNS fail, like on office Wi‑Fi) | "Couldn't load parking" + Retry works | I | P0 |
| SPOT-03 | Malformed / partial JSON | Bad entries skipped, others shown | U | P0 |
| SPOT-04 | Availability changes in Firestore | Sheet's "N free" and pin colour update live (< 3 s) | E | P0 |
| SPOT-05 | Availability doc missing for a spot | Shows capacity or "unknown", not 0/Full | U | P1 |
| SPOT-06 | Parking details screen | Photos, price, hours, amenities, reviews render; long names wrap | I | P1 |
| SPOT-07 | Spot closed now (hours) | Shown as closed; booking blocked or warned | U | P1 |
| SPOT-08 | Save / unsave from sheet | Bookmark toggles; appears in Saved | E | P0 |

### 4.8 Booking flow (BOOK)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| BOOK-01 | Happy path: vehicle → floor/slot → time → review → pay → success | Booking in Firestore, availability −1, QR shown | E | P0 |
| BOOK-02 | No vehicles yet | Add-vehicle sheet inline, then continue | E | P0 |
| BOOK-03 | Price maths: duration × rate + tax, per vehicle type | Exact rupee totals, rounding consistent | U | P0 |
| BOOK-04 | Check-in in the past | Blocked with "That time has already passed" | U | P0 |
| BOOK-05 | Check-in across midnight / multi-day duration | Correct exit time and price | U | P1 |
| BOOK-06 | Slot already booked for overlapping time | Rejected with "just booked" message | I (emulator) | P0 |
| BOOK-07 | **Two users book the last spot at the same moment** | Exactly one succeeds; availability never negative; no duplicate slot booking | L + I | P0 |
| BOOK-08 | Floor full / parking full for vehicle type | Clear message; user returned to pick another floor | I | P0 |
| BOOK-09 | Network drops on "Pay/Confirm" | No half booking; clear retry; no double booking on retry | E | P0 |
| BOOK-10 | Double-tap Confirm | One booking only | I | P0 |
| BOOK-11 | Back navigation mid-flow | Selections kept going back; flow cleared after success | I | P1 |
| BOOK-12 | Process killed on Review screen, relaunch | No crash; flow restarts cleanly or restores | E | P1 |
| BOOK-13 | Booking ID format and uniqueness (10k generated) | Matches pattern, no collisions | U | P1 |
| BOOK-14 | EV/wheelchair options | Saved on booking; only offered when spot supports them | U + I | P2 |
| BOOK-15 | Signed out mid-flow (token expired) | "You're signed out" → Login → returns to flow or Home | I | P1 |

### 4.9 Bookings list, QR, extend, cancel (BKL)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| BKL-01 | Ongoing vs past tabs | Correct split by status/time | U | P0 |
| BKL-02 | New booking appears without refresh | Realtime listener | E | P0 |
| BKL-03 | QR code | Encodes `PARKOVER:<id>`; scannable by a QR reader | I | P1 |
| BKL-04 | Cancel booking | Status cancelled, availability +1, moves to past | E | P0 |
| BKL-05 | Cancel twice / cancel already-ended booking | Blocked; availability not incremented twice | U + I | P0 |
| BKL-06 | Extend booking | New exit time and extra charge correct | U + E | P1 |
| BKL-07 | Extend into another user's booking of same slot | Rejected | I | P1 |
| BKL-08 | 100 bookings | List scrolls smoothly; lazy items found via scroll | I | P2 |
| BKL-09 | Firestore "No setter/field for ongoing/past" warnings | Model fixed, no warnings in log | U | P2 |

### 4.10 Vehicles (VEH)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| VEH-01 | Add vehicle (type, brand, model, number) | Saved; shown in list and booking flow | E | P0 |
| VEH-02 | Indian plate validation (MH 01 AB 1234, BH series, spaces, lowercase) | Accepts valid, rejects invalid, normalises format | U | P0 |
| VEH-03 | Duplicate plate | Rejected or warned | U + I | P1 |
| VEH-04 | Set default | Only one default at a time | I (emulator) | P0 |
| VEH-05 | Delete with Undo | Undo restores exactly | I | P1 |
| VEH-06 | Delete vehicle used by an active booking | Booking still shows vehicle details | I | P1 |
| VEH-07 | Vehicle catalogue fetch fails | Fallback catalogue used | U | P1 |

### 4.11 Saved (SAVE)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| SAVE-01 | Empty state | Friendly message + CTA | I | P1 |
| SAVE-02 | Saved list with road distances | "X km by road"; straight-line fallback if routing fails | I | P1 |
| SAVE-03 | Unsave from list | Removed instantly; Home bookmark updates | E | P1 |
| SAVE-04 | Saved spot removed from source data | Graceful "no longer available" | U | P2 |

### 4.12 Profile, payments, help (PROF)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| PROF-01 | Edit name | Saved; header initial updates | E | P1 |
| PROF-02 | Upload photo (camera/gallery picker via UIAutomator) | Uploaded to Storage; shown | E | P2 |
| PROF-03 | Add UPI ID valid/invalid (`name@bank`) | Validation; default handling | U + I | P1 |
| PROF-04 | Remove UPI ID | Removed; default reassigned or none | I | P2 |
| PROF-05 | Submit support ticket | Doc created with own `userId` only | E | P1 |
| PROF-06 | Help links (call, email, WhatsApp) | Correct intents fire; no crash if app missing | I | P2 |

### 4.13 Network and backend failure (NET), cross-cutting
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| NET-01 | Launch in airplane mode | App opens; clear offline state; no crash | E | P0 |
| NET-02 | Wi‑Fi drops while on Home | Map and pins stay; banner; recovers when back | E | P0 |
| NET-03 | Switch Wi‑Fi ↔ mobile data mid-booking | Booking either completes once or fails cleanly | E | P0 |
| NET-04 | Very slow network (2G profile) | Loaders, no ANR, timeouts handled | I (emulator) | P1 |
| NET-05 | Captive portal / DNS failure | Same as offline; no raw exception text | E | P1 |
| NET-06 | Firestore offline cache | Previously seen bookings visible offline; writes queued or blocked clearly | E | P1 |
| NET-07 | **Conflict check fails open**: `hasTimeConflict` returns `false` on error | Must fail closed (block booking) | U | P0 |
| NET-08 | Firebase PERMISSION_DENIED (rules) | Friendly message, logged | I | P1 |

### 4.14 App lifecycle and device (LIFE)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| LIFE-01 | Rotate on every screen | State kept, no crash | I | P1 |
| LIFE-02 | Background 10 min, return | Map and listeners resume; no duplicate listeners | E | P1 |
| LIFE-03 | Process death on each tab | Restores to sensible screen | E | P1 |
| LIFE-04 | Dark mode, font 1.3×, display size large | Readable, nothing clipped | I | P1 |
| LIFE-05 | Back button from every screen | Never exits mid-booking without confirm; never returns to Login after login | I | P0 |
| LIFE-06 | Low memory (`adb shell am send-trim-memory`) | MapView survives or recreates cleanly | E | P2 |
| LIFE-07 | Install over previous version (upgrade) | User stays logged in; data intact | E | P1 |
| LIFE-08 | Battery saver / MIUI restrictions | Location and listeners still work while foreground | E | P2 |
| LIFE-09 | TalkBack | Pins, chips, buttons have labels; booking possible | E | P2 |
| LIFE-10 | Memory leak after 20 tab switches | No MapView leak (LeakCanary in debug) | I | P2 |

### 4.15 Security (SEC)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| SEC-01 | User A reads `users/B` | Denied | S | P0 |
| SEC-02 | User A reads/edits B's vehicles | Denied | S | P0 |
| SEC-03 | User A reads B's bookings | **Currently allowed** (any signed-in user). Tighten, then must be denied | S | P0 |
| SEC-04 | User A creates booking with `userId = B` | Denied | S | P0 |
| SEC-05 | Delete any booking | Denied | S | P0 |
| SEC-06 | Unauthenticated write to `parking_availability` | Denied | S | P0 |
| SEC-07 | Signed-in user sets availability to 9999 or negative | **Currently allowed**. Must be limited to ±1 via booking transaction or moved to a Cloud Function | S | P0 |
| SEC-08 | Support ticket spoofing `userId` | Denied | S | P0 |
| SEC-09 | No secrets in APK except expected client keys | Scan built APK for `DEV_LOGIN`, ORS key exposure noted until proxied | U (build check) | P1 |
| SEC-10 | App Check enforced | Requests from a non-genuine client rejected | E | P1 |
| SEC-11 | Release build: no debug logs of locations/tokens | `Log.d/w` with PII stripped | U (lint) | P1 |

### 4.16 Load and many users (LOAD)
| ID | Scenario | Expected | Layer | P |
|---|---|---|---|---|
| LOAD-01 | 50 virtual users book the same last spot within 1 s | Exactly 1 success; availability ≥ 0; others get a clear "full" | L | P0 |
| LOAD-02 | 200 users booking across 20 parkings for 10 min | p95 booking < 2 s; zero double-booked slots; availability equals capacity − active bookings at the end | L | P1 |
| LOAD-03 | 500 listeners on one popular parking's availability | Updates reach all within 3 s; cost per update estimated | L | P1 |
| LOAD-04 | Cancel/extend storm on same bookings | No negative or over-capacity availability | L | P1 |
| LOAD-05 | Routing quota at scale | Daily user × taps estimate vs ORS free tier; decide proxy + cache before launch | L (calc) | P0 |
| LOAD-06 | Firestore read cost per Home open | Count reads per session; Spark/Blaze cost projection for 1k/10k users | L (calc) | P1 |
| LOAD-07 | Two-device realtime: driver B (headless REST) books while A watches | A's pin/sheet updates live | E | P0 |

---

## 5. Risks found while writing this plan

Status (14 Nov): risks 1–4 are **fixed** in code and rules, covered by `firestore-tests/rules.test.js` (21 emulator tests, all passing; 16 of them fail against the old rules). Risk 5 is open.

1. **Double booking race (BOOK-07, LOAD-01).** `BookingRepository.hasTimeConflict` runs a query *outside* the transaction in `createBooking`. Two users can both pass the check and book the same slot for the same time. The transaction only guards the availability counter, not the slot. Fix: a deterministic slot-lock document per `parkingId/spot/time-bucket` read and written inside the same transaction, or move booking creation to a Cloud Function.
2. **Conflict check fails open (NET-07).** `hasTimeConflict` returns `false` on any exception, so an offline or permission error *allows* the booking. It should fail closed.
3. **Availability writable by any signed-in user (SEC-07).** The rules allow any signed-in client to write any value to `parking_availability`.
4. **Bookings readable by all signed-in users (SEC-03).** Already on the Later Plan; needs a rules test to lock it.
5. **Routing key ships in the APK (SEC-09, LOAD-05).** Fine for testing; needs a proxy (Cloud Function) with caching before public launch.

---

## 6. Build order

| Phase | What | Gets us |
|---|---|---|
| **0. Foundations** | Add test deps (MockK, coroutines-test, Turbine, MockWebServer, Compose UI test, UIAutomator); seams from §3.3; test tags on key controls; map test hooks; failure screenshot/log rule | Everything else depends on this |
| **1. Unit P0** | Pricing, validation, conflict maths, filters, routing parser + fallbacks, booking ID, fail-closed conflict check | Fast safety net on every commit |
| **2. Rules + emulator** | Firestore rules tests (SEC-01…08), booking transaction tests on the emulator incl. the race (BOOK-06/07) | Proves and then fixes the two biggest data risks |
| **3. Device integration P0** | Home/map/search/route/booking screens against fakes, offline and permission cases | Catches UI breakage without the live backend |
| **4. E2E smoke on Nothing** | `parkover-test` project, test accounts, `run-e2e.sh`; AUTH-03, ROUTE-01, BOOK-01, BKL-04, NET-01/02, LOAD-07 | One command proves the real app works end to end |
| **5. Load** | `parkover-load` project, k6 scripts for LOAD-01…04, cost/quota calculations | Confidence before launch |
| **6. P1/P2 sweep** | Remaining scenarios, Xiaomi + emulators matrix, accessibility | Release-ready |

---

## 7. Commands (target state)

```bash
# Layer 1
./gradlew testDebugUnitTest

# Layer 2 (emulator suite running: firebase emulators:start --only auth,firestore)
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=com.dhruvk.parkover.test.Integration

# Rules
cd firestore-tests && npm test

# Layer 3 on the Nothing phone
scripts/run-e2e.sh 001206477020888

# Layer 4
k6 run load/book-last-spot.js
```
