# Booking Flow — Dynamic Data & End-to-End Analysis

## Current State

The booking flow currently has several hardcoded/disconnected pieces. This document maps out what's static, what needs to become dynamic, and the implementation approach for each.

---

## Problem Areas

### 1. Booking Data Not Passed Through the Flow

**Current:** Each screen in the booking flow (SelectVehicle → SelectFloor → ConfirmBooking → ReviewSummary → Payment → Success) operates independently. The `BookingFlowViewModel` exists but isn't wired into the navigation graph.

**Fix:** Use a shared `BookingFlowViewModel` scoped to the booking flow's nav graph back stack entry. Each screen reads/writes to this shared state.

```
Implementation:
- In ParkOverNavGraph, create a nested navigation graph for the booking flow
- Scope BookingFlowViewModel to that nested graph using `hiltNavGraphViewModels()` or 
  `viewModel(backStackEntry)` pattern
- Each screen reads from bookingFlowViewModel.bookingData (StateFlow)
- Each screen updates the relevant fields before navigating forward
```

---

### 2. Payment Screen — Hardcoded Values

**Current:** `totalAmount = 220.0` is hardcoded. The booking creation uses empty `parkingId`, dummy vehicle number, etc.

**Fix:** Read all values from `BookingFlowViewModel.bookingData`:
- `totalPrice` (calculated from pricePerHour × duration + tax)
- `parkingId`, `parkingName`, `parkingAddress` (from spot selection)
- `vehicleId`, `vehicleNumber`, `vehicleType` (from vehicle selection)
- `floorNumber`, `floorName`, `spotNumber` (from floor/spot selection)
- `checkInTime`, `checkOutTime`, `durationHours` (from time selection)

After payment, create the actual `Booking` object with all real data and write to Firestore.

---

### 3. Vehicle Selection — Not Persisted to Booking

**Current:** User selects a vehicle but the selection isn't carried forward. The `selectedVehicleId` is local state only.

**Fix:** On "Continue" tap, call `bookingFlowViewModel.selectVehicle(vehicle)` which updates the shared booking data with vehicleId, vehicleName, vehicleNumber, vehicleType.

---

### 4. Floor/Spot Selection — Not Persisted to Booking

**Current:** Same issue. `selectedSpot` and `selectedFloor` are local state.

**Fix:** On "Continue" tap, call `bookingFlowViewModel.selectFloor(floor)` and `bookingFlowViewModel.selectSpot(spot)`.

---

### 5. Bookings Not Showing in "Scheduled" Tab

**Current:** The `BookingListScreen` calls `repository.getOngoingBookings()` which queries Firestore for bookings with status CONFIRMED/ACTIVE/PENDING for the current user. Since the payment screen doesn't actually create a real booking (it was failing due to empty parkingId), nothing shows up.

**Fix (after wiring dynamic data):**
1. Payment screen creates a real `Booking` with all fields from `BookingFlowViewModel`
2. `BookingRepository.createBooking()` writes to Firestore with proper data
3. The Firestore transaction also decreases availability
4. `getOngoingBookings()` will then return the newly created booking

---

## Implementation Plan (Ordered)

### Phase 1: Wire BookingFlowViewModel

1. Add `BookingFlowViewModel` to the nav graph (scoped to booking flow)
2. In `ParkingDetailsScreen` → on "Book Parking" click, call `bookingFlowViewModel.initBooking(spot)`
3. In `SelectVehicleScreen` → on "Continue", call `bookingFlowViewModel.selectVehicle(vehicle)`
4. In `SelectFloorScreen` → on "Continue", call `bookingFlowViewModel.selectFloor(floor)` + `selectSpot(spot)`
5. In `ConfirmBookingScreen` → on "Continue", call `bookingFlowViewModel.setCheckInTime(time)` + `setDuration(hours)` + `setSpecifications(...)`
6. In `ReviewSummaryScreen` → read all data from `bookingFlowViewModel.bookingData` to display
7. In `PaymentScreen` → read `totalPrice` from flow, on success create real booking

### Phase 2: Real Booking Creation

1. `PaymentScreen` calls `bookingFlowViewModel.setPaymentMethod(method)`
2. On pay click, create `Booking` from `bookingFlowViewModel.bookingData`:
   ```kotlin
   val data = bookingFlowViewModel.bookingData.value
   val booking = Booking(
       parkingId = data.parkingId,
       parkingName = data.parkingName,
       parkingAddress = data.parkingAddress,
       vehicleId = data.vehicleId,
       vehicleNumber = data.vehicleNumber,
       vehicleType = data.vehicleType,
       floorNumber = data.floorNumber,
       floorName = data.floorName,
       spotNumber = data.spotNumber,
       entryTime = Timestamp(Date(data.checkInTime)),
       exitTime = Timestamp(Date(data.checkOutTime)),
       durationHours = data.durationHours,
       basePrice = data.basePrice,
       taxAmount = data.taxAmount,
       totalPrice = data.totalPrice,
       paymentMethod = data.paymentMethod,
       paymentStatus = ...,
       bookingStatus = BookingStatus.CONFIRMED,
       qrCodeData = "PARKOVER-${bookingId}"
   )
   ```
3. Call `BookingRepository.createBooking(booking)` — this already handles:
   - Firestore transaction to validate availability
   - Decreasing spot count atomically
   - Writing the booking document

### Phase 3: Bookings Display

Once Phase 2 is done, `BookingListScreen` will automatically show bookings because:
- `getOngoingBookings()` queries `bookings` collection where `userId == currentUser` and status is CONFIRMED/ACTIVE/PENDING
- The booking we just created matches this query

### Phase 4: Availability Sync

The `AvailabilityRepository` already handles real-time updates via Firestore snapshots. Once bookings are created properly:
- Spot availability decreases on booking
- Map markers update color (AVAILABLE → LIMITED → FULL)
- Floor/spot selection shows correct occupied spots

---

## Data Flow Diagram (Target State)

```
User taps parking marker
    → ParkingDetailsScreen (reads from ParkingViewModel.selectedParkingSpot)
    → "Book Parking" → bookingFlowVM.initBooking(spot)
    
SelectVehicleScreen
    → Loads vehicles from Firestore (vehicles collection, userId filter)
    → User selects → bookingFlowVM.selectVehicle(vehicle)
    
SelectFloorScreen  
    → Loads floors from BookingRepository.getFloorsWithSpots(parkingId, vehicleType)
    → User selects floor + spot → bookingFlowVM.selectFloor() + selectSpot()
    
ConfirmBookingScreen
    → User picks time + duration → bookingFlowVM.setCheckInTime() + setDuration()
    → Price auto-calculated in VM
    
ReviewSummaryScreen
    → Reads all from bookingFlowVM.bookingData (display only)
    
PaymentScreen
    → Reads totalPrice from bookingFlowVM
    → On success: BookingRepository.createBooking(fullBooking)
    → Navigate to BookingSuccessScreen with bookingId
    
BookingSuccessScreen
    → Shows QR code with bookingId
    → "View Booking" → BookingListScreen (now shows the new booking)
```

---

## Estimated Effort

| Phase | Scope | Effort |
|-------|-------|--------|
| Phase 1 | Wire ViewModel through flow | ~1 hour |
| Phase 2 | Real booking creation | ~30 min |
| Phase 3 | Verify bookings display | ~10 min (should work automatically) |
| Phase 4 | Verify availability sync | ~10 min (already implemented) |

**Total: ~2 hours** to make the full booking flow dynamic end-to-end.
