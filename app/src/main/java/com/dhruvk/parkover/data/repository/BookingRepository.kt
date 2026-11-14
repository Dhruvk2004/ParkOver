package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.FloorData
import com.dhruvk.parkover.data.model.ParkingSlot
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.Vehicle
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.di.AppServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
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
    private val slotLocksCollection = firestore.collection(SLOT_LOCKS)

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
        // Bays held from now on, from the slot locks (other users' bookings are private).
        // This is display only: the booking transaction is what actually stops double bookings.
        val nowBucket = bucketOf(AppServices.clock.nowMillis())
        val booked = try {
            slotLocksCollection
                .whereEqualTo("parkingId", spot.id)
                .get()
                .await()
                .documents
                .filter { (it.getLong("bucket") ?: Long.MIN_VALUE) >= nowBucket }
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

    /**
     * Creates the booking, its slot locks and the availability decrement in one transaction.
     *
     * Each 30-minute bucket of a bay has one lock document. Reading the locks inside the
     * transaction makes two people booking the same bay and time collide: Firestore retries the
     * loser, which then sees the winner's lock and fails with a clear message. Any error fails the
     * booking (never "assume it's free").
     */
    suspend fun createBooking(booking: Booking): Result<Booking> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("You're signed out. Log in and try again."))

        if (booking.exitTime.toDate().time < AppServices.clock.nowMillis()) {
            return Result.failure(Exception("That time has already passed. Pick a later check-in."))
        }

        val bookingId = newBookingId()
        val newBooking = booking.copy(
            id = bookingId,
            userId = userId,
            qrCodeData = "PARKOVER:$bookingId",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        val buckets = lockBuckets(booking.entryTime.toDate().time, booking.exitTime.toDate().time)

        return try {
            firestore.runTransaction { transaction ->
                // All reads first (Firestore requires reads before writes).
                val lockRefs = buckets.map { slotLocksCollection.document(lockId(booking.parkingId, booking.spotNumber, it)) }
                if (lockRefs.any { transaction.get(it).exists() }) throw SlotTakenException(booking.spotNumber)
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
                    // The rules only accept a -1 tied to a booking created in the same write.
                    updates["lastBookingId"] = bookingId
                }

                transaction.set(bookingsCollection.document(bookingId), newBooking)
                buckets.zip(lockRefs).forEach { (bucket, ref) ->
                    transaction.set(ref, lockData(booking.parkingId, booking.spotNumber, bucket, bookingId, userId))
                }
                if (updates.isNotEmpty()) transaction.update(availRef, updates)
            }.await()
            Result.success(newBooking)
        } catch (e: Exception) {
            Result.failure(e.toBookingError())
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

    /** Cancels an active booking, frees its slot locks and gives the bay back, once. */
    suspend fun cancelBooking(booking: Booking): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: throw Exception("You're signed out. Log in and try again.")
        val buckets = lockBuckets(booking.entryTime.toDate().time, booking.exitTime.toDate().time)
        firestore.runTransaction { transaction ->
            val bookingRef = bookingsCollection.document(booking.id)
            val fresh = transaction.get(bookingRef)
            val status = fresh.getString("bookingStatus")
            if (!fresh.exists() || fresh.getString("userId") != userId) throw Exception("Booking not found.")
            if (status !in ACTIVE_STATUSES) throw Exception("This booking is already ${status?.lowercase() ?: "closed"}.")

            val lockSnaps = buckets.map { transaction.get(slotLocksCollection.document(lockId(booking.parkingId, booking.spotNumber, it))) }
            val availRef = availabilityCollection.document(booking.parkingId)
            val availSnapshot = transaction.get(availRef)

            transaction.update(
                bookingRef,
                mapOf("bookingStatus" to BookingStatus.CANCELLED.name, "updatedAt" to Timestamp.now())
            )
            // Only this booking's locks; older bookings made before locks existed have none.
            lockSnaps.filter { it.exists() && it.getString("bookingId") == booking.id }
                .forEach { transaction.delete(it.reference) }
            if (availSnapshot.exists()) {
                val field = availabilityField(booking.vehicleType)
                val updates = mutableMapOf<String, Any>(
                    field to (availSnapshot.getLong(field) ?: 0) + 1,
                    "lastUpdated" to Timestamp.now(),
                    "lastBookingId" to booking.id
                )
                @Suppress("UNCHECKED_CAST")
                val floorAvail = (availSnapshot.get("floorAvailability") as? Map<String, Long>)
                val floorKey = booking.floorNumber.toString()
                if (floorAvail?.containsKey(floorKey) == true) {
                    updates["floorAvailability"] = floorAvail.toMutableMap().apply { put(floorKey, (get(floorKey) ?: 0) + 1) }
                }
                transaction.update(availRef, updates)
            }
        }.await()
        Unit
    }

    /** Adds [hours] to an active booking, only if the bay is free for the extra time. */
    suspend fun extendBooking(booking: Booking, hours: Int, ratePerHour: Double, taxRate: Double): Result<Booking> = runCatching {
        val userId = auth.currentUser?.uid ?: throw Exception("You're signed out. Log in and try again.")
        val oldExit = booking.exitTime.toDate().time
        val newExitMs = oldExit + hours * 3_600_000L
        val held = lockBuckets(booking.entryTime.toDate().time, oldExit).toSet()
        val extra = lockBuckets(oldExit, newExitMs).filter { it !in held }
        val extraBase = ratePerHour * hours
        val updated = booking.copy(
            exitTime = Timestamp(Date(newExitMs)),
            durationHours = booking.durationHours + hours,
            basePrice = booking.basePrice + extraBase,
            taxAmount = booking.taxAmount + extraBase * taxRate,
            totalPrice = booking.totalPrice + extraBase * (1 + taxRate),
            updatedAt = Timestamp.now()
        )
        try {
            firestore.runTransaction { transaction ->
                val bookingRef = bookingsCollection.document(booking.id)
                val fresh = transaction.get(bookingRef)
                if (!fresh.exists() || fresh.getString("userId") != userId) throw Exception("Booking not found.")
                if (fresh.getString("bookingStatus") !in ACTIVE_STATUSES) throw Exception("Only active bookings can be extended.")
                val lockRefs = extra.map { slotLocksCollection.document(lockId(booking.parkingId, booking.spotNumber, it)) }
                if (lockRefs.any { transaction.get(it).exists() }) {
                    throw Exception("Spot ${booking.spotNumber} is booked by someone else right after your slot, so it can't be extended.")
                }
                transaction.set(bookingRef, updated)
                extra.zip(lockRefs).forEach { (bucket, ref) ->
                    transaction.set(ref, lockData(booking.parkingId, booking.spotNumber, bucket, booking.id, userId))
                }
            }.await()
        } catch (e: Exception) {
            throw e.toBookingError()
        }
        updated
    }

    private fun availabilityField(type: VehicleType) = when (type) {
        VehicleType.TWO_WHEELER -> "availableSpotsTwoWheeler"
        VehicleType.FOUR_WHEELER -> "availableSpotsFourWheeler"
        VehicleType.HEAVY -> "availableSpotsHeavy"
    }

    private fun lockData(parkingId: String, spotNumber: String, bucket: Long, bookingId: String, userId: String) = mapOf(
        "parkingId" to parkingId,
        "spotNumber" to spotNumber,
        "bucket" to bucket,
        "bookingId" to bookingId,
        "userId" to userId,
        // For a future TTL policy that clears old locks.
        "expiresAt" to Timestamp(Date((bucket + 1) * LOCK_BUCKET_MS))
    )

    class SlotTakenException(spotNumber: String) :
        Exception("Spot $spotNumber was just booked for that time. Pick another spot.")

    /** Turns raw Firestore failures into messages a driver can act on. Never reports success. */
    private fun Exception.toBookingError(): Exception {
        val chain = generateSequence<Throwable>(this) { it.cause }.toList()
        chain.firstOrNull { it is SlotTakenException }?.let { return it as Exception }
        return when (chain.filterIsInstance<FirebaseFirestoreException>().firstOrNull()?.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                Exception("You're offline, so nothing was booked. Check your connection and try again.")
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                Exception("This booking was refused. Refresh and try again.")
            FirebaseFirestoreException.Code.ABORTED ->
                Exception("Someone booked at the same moment. Pick the spot again.")
            else -> Exception(
                chain.firstOrNull { it !is FirebaseFirestoreException && !it.message.isNullOrBlank() }?.message
                    ?: "Couldn't complete the booking. Try again."
            )
        }
    }

    companion object {
        const val SLOT_LOCKS = "slot_locks"
        /** Bays are locked in 30-minute buckets; check-in times are on the half hour. */
        const val LOCK_BUCKET_MS = 30 * 60_000L
        val ACTIVE_STATUSES = setOf(BookingStatus.PENDING.name, BookingStatus.CONFIRMED.name, BookingStatus.ACTIVE.name)

        fun bucketOf(timeMs: Long): Long = Math.floorDiv(timeMs, LOCK_BUCKET_MS)

        /** Every bucket the interval touches, rounding outwards so partial buckets are held too. */
        fun lockBuckets(entryMs: Long, exitMs: Long): List<Long> {
            if (exitMs <= entryMs) return emptyList()
            val first = bucketOf(entryMs)
            val last = Math.floorDiv(exitMs - 1, LOCK_BUCKET_MS)
            return (first..last).toList()
        }

        fun lockId(parkingId: String, spotNumber: String, bucket: Long) = "${parkingId}_${spotNumber}_$bucket"

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
