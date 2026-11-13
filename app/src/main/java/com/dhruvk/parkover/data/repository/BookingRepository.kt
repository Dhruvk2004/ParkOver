package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.FloorData
import com.dhruvk.parkover.data.model.ParkingSlot
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.Vehicle
import com.dhruvk.parkover.data.model.VehicleType
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class BookingRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val bookingsCollection = firestore.collection("bookings")
    private val vehiclesCollection = firestore.collection("vehicles")
    private val availabilityCollection = firestore.collection("parking_availability")

    private val activeStatuses = listOf(BookingStatus.CONFIRMED.name, BookingStatus.ACTIVE.name, BookingStatus.PENDING.name)

    // ---------- Vehicles ----------

    suspend fun getUserVehicles(): List<Vehicle> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            vehiclesCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .toObjects(Vehicle::class.java)
                .sortedByDescending { it.isDefault }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addVehicle(vehicle: Vehicle): Result<Vehicle> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val vehicleId = UUID.randomUUID().toString()
            val newVehicle = vehicle.copy(id = vehicleId, userId = userId)
            if (newVehicle.isDefault) clearDefaultVehicle(userId)
            vehiclesCollection.document(vehicleId).set(newVehicle).await()
            Result.success(newVehicle)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVehicle(vehicleId: String): Result<Unit> = runCatching {
        vehiclesCollection.document(vehicleId).delete().await()
        Unit
    }

    suspend fun setDefaultVehicle(vehicleId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return runCatching {
            clearDefaultVehicle(userId)
            vehiclesCollection.document(vehicleId).update("default", true).await()
            Unit
        }
    }

    private suspend fun clearDefaultVehicle(userId: String) {
        val defaults = vehiclesCollection.whereEqualTo("userId", userId).get().await()
        defaults.documents.forEach { doc -> doc.reference.update("default", false).await() }
    }

    // ---------- Floors and spots ----------

    /**
     * Builds the floor plan for [spot] from its own floor list (real names, including basements).
     * Bays booked in Firestore are marked taken; the rest of the occupancy comes from the live
     * per-floor availability count.
     */
    suspend fun getFloorsWithSpots(spot: ParkingSpot, vehicleType: VehicleType): List<FloorData> {
        if (spot.floors.isEmpty()) return emptyList()
        val booked = try {
            bookingsCollection
                .whereEqualTo("parkingId", spot.id)
                .whereIn("bookingStatus", listOf(BookingStatus.CONFIRMED.name, BookingStatus.ACTIVE.name))
                .get()
                .await()
                .documents
                .mapNotNull { it.getString("spotNumber") }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }

        return spot.floors.sortedBy { it.floorNumber }.map { floor ->
            val prefix = floorLabel(floor.floorNumber)
            val occupied = (floor.totalSpots - floor.availableSpots).coerceIn(0, floor.totalSpots)
            val slots = (0 until floor.totalSpots).map { index ->
                val spotNumber = "$prefix-${String.format("%02d", index + 1)}"
                // Spread the counted occupancy across the floor instead of filling it front to back
                val takenByCount = floor.totalSpots > 0 && ((index * 37) % floor.totalSpots) < occupied
                val taken = spotNumber in booked || takenByCount
                ParkingSlot(
                    id = "${spot.id}_${floor.floorNumber}_$spotNumber",
                    spotNumber = spotNumber,
                    floorNumber = floor.floorNumber,
                    isAvailable = !taken,
                    isBooked = taken,
                    vehicleType = vehicleType,
                    hasEvCharging = spot.amenities.contains("EV Charging") && index % 10 == 9,
                    hasWheelchairAccess = spot.amenities.contains("Wheelchair Access") && index < 2,
                    row = index / 2,
                    column = index % 2
                )
            }
            FloorData(
                floorNumber = floor.floorNumber,
                name = floor.name,
                spots = slots,
                totalSpots = floor.totalSpots,
                availableSpots = slots.count { it.isAvailable }
            )
        }
    }

    // ---------- Bookings ----------

    private suspend fun hasTimeConflict(booking: Booking): Boolean = try {
        bookingsCollection
            .whereEqualTo("parkingId", booking.parkingId)
            .whereEqualTo("spotNumber", booking.spotNumber)
            .get()
            .await()
            .toObjects(Booking::class.java)
            .filter { it.bookingStatus.name in activeStatuses }
            .any { existing ->
                booking.entryTime.toDate().before(existing.exitTime.toDate()) &&
                    booking.exitTime.toDate().after(existing.entryTime.toDate())
            }
    } catch (e: Exception) {
        false
    }

    /** Creates the booking and decrements availability in one transaction. */
    suspend fun createBooking(booking: Booking): Result<Booking> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("You're signed out. Log in and try again."))

        if (booking.exitTime.toDate().time < System.currentTimeMillis()) {
            return Result.failure(Exception("That time has already passed. Pick a later check-in."))
        }
        if (hasTimeConflict(booking)) {
            return Result.failure(Exception("Spot ${booking.spotNumber} was just booked for that time. Pick another spot."))
        }

        val bookingId = newBookingId()
        val newBooking = booking.copy(
            id = bookingId,
            userId = userId,
            qrCodeData = "PARKOVER:$bookingId",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        return try {
            firestore.runTransaction { transaction ->
                val availRef = availabilityCollection.document(booking.parkingId)
                val availSnapshot = transaction.get(availRef)
                val updates = mutableMapOf<String, Any>()

                if (availSnapshot.exists()) {
                    val field = availabilityField(booking.vehicleType)
                    val current = availSnapshot.getLong(field) ?: 0
                    if (current <= 0) throw Exception("This parking is full for your vehicle type.")
                    updates[field] = current - 1

                    @Suppress("UNCHECKED_CAST")
                    val floorAvail = (availSnapshot.get("floorAvailability") as? Map<String, Long>)?.toMutableMap()
                    val floorKey = booking.floorNumber.toString()
                    if (floorAvail != null && floorAvail.containsKey(floorKey)) {
                        val currentFloor = floorAvail[floorKey] ?: 0
                        if (currentFloor <= 0) throw Exception("${booking.floorName} is full. Pick another floor.")
                        floorAvail[floorKey] = currentFloor - 1
                        updates["floorAvailability"] = floorAvail
                    }
                    updates["lastUpdated"] = Timestamp.now()
                }

                transaction.set(bookingsCollection.document(bookingId), newBooking)
                if (updates.isNotEmpty()) transaction.update(availRef, updates)
            }.await()
            Result.success(newBooking)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBooking(bookingId: String): Booking? = try {
        bookingsCollection.document(bookingId).get().await().toObject(Booking::class.java)
    } catch (e: Exception) {
        null
    }

    /** Live list of the signed-in user's bookings, newest check-in first. */
    fun observeUserBookings(): Flow<List<Booking>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = bookingsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val bookings = snapshot?.toObjects(Booking::class.java).orEmpty()
                trySend(bookings.sortedByDescending { it.entryTime.toDate() })
            }
        awaitClose { registration.remove() }
    }

    suspend fun cancelBooking(booking: Booking): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val bookingRef = bookingsCollection.document(booking.id)
            val availRef = availabilityCollection.document(booking.parkingId)
            val availSnapshot = transaction.get(availRef)

            transaction.update(
                bookingRef,
                mapOf("bookingStatus" to BookingStatus.CANCELLED.name, "updatedAt" to Timestamp.now())
            )
            if (availSnapshot.exists()) {
                val field = availabilityField(booking.vehicleType)
                val updates = mutableMapOf<String, Any>(field to FieldValue.increment(1), "lastUpdated" to Timestamp.now())
                @Suppress("UNCHECKED_CAST")
                val floorAvail = (availSnapshot.get("floorAvailability") as? Map<String, Long>)
                if (floorAvail?.containsKey(booking.floorNumber.toString()) == true) {
                    updates["floorAvailability.${booking.floorNumber}"] = FieldValue.increment(1)
                }
                transaction.update(availRef, updates)
            }
        }.await()
        Unit
    }

    /** Adds [hours] to a booking's end time and price. */
    suspend fun extendBooking(booking: Booking, hours: Int, ratePerHour: Double, taxRate: Double): Result<Booking> = runCatching {
        val newExit = Timestamp(Date(booking.exitTime.toDate().time + hours * 3_600_000L))
        val extraBase = ratePerHour * hours
        val updated = booking.copy(
            exitTime = newExit,
            durationHours = booking.durationHours + hours,
            basePrice = booking.basePrice + extraBase,
            taxAmount = booking.taxAmount + extraBase * taxRate,
            totalPrice = booking.totalPrice + extraBase * (1 + taxRate),
            updatedAt = Timestamp.now()
        )
        bookingsCollection.document(booking.id).set(updated).await()
        updated
    }

    private fun availabilityField(type: VehicleType) = when (type) {
        VehicleType.TWO_WHEELER -> "availableSpotsTwoWheeler"
        VehicleType.FOUR_WHEELER -> "availableSpotsFourWheeler"
        VehicleType.HEAVY -> "availableSpotsHeavy"
    }

    companion object {
        /** Short, readable label for a floor number: B2, B1, G, L1, L2… */
        fun floorLabel(floorNumber: Int): String = when {
            floorNumber < 0 -> "B${-floorNumber}"
            floorNumber == 0 -> "G"
            else -> "L$floorNumber"
        }

        private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun newBookingId(): String {
            val random = java.security.SecureRandom()
            fun block() = (1..4).map { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] }.joinToString("")
            return "PK-${block()}-${block()}"
        }
    }
}
