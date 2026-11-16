package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.FloorData
import com.dhruvk.parkover.data.model.ParkingSlot
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.Vehicle
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.di.AppServices
import com.dhruvk.parkover.domain.BookingRules
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID

/** Bays a slot can hold: for one vehicle type, and (optionally) on one floor. */
data class SlotCapacity(val type: Int, val floor: Int? = null)

/** [firestore]/[auth] are the app's defaults; E2E tests pass a second Firebase app to act as another driver. */
class BookingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    private val bookingsCollection = firestore.collection("bookings")
    private val vehiclesCollection = firestore.collection("vehicles")
    private val occupancyCollection = firestore.collection(OCCUPANCY)
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
     * Floor plan for [spot] during the stay [fromMs]..[toMs]: a bay is taken if someone holds
     * its lock in any 30-minute slot of that window (from the public slot locks; other drivers'
     * bookings stay private). Bookings at other times don't block it.
     * Display only: the booking transaction is what actually stops double bookings.
     */
    suspend fun getFloorsWithSpots(spot: ParkingSpot, vehicleType: VehicleType, fromMs: Long, toMs: Long): List<FloorData> {
        if (spot.floors.isEmpty()) return emptyList()
        val window = lockBuckets(fromMs, toMs).let { if (it.isEmpty()) setOf(bucketOf(fromMs)) else it.toSet() }
        val booked = try {
            slotLocksCollection
                .whereEqualTo("parkingId", spot.id)
                .get()
                .await()
                .documents
                .filter { it.getLong("bucket") in window }
                .mapNotNull { it.getString("spotNumber") }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }

        return spot.floors.sortedBy { it.floorNumber }.map { floor ->
            val prefix = floorLabel(floor.floorNumber)
            val slots = (0 until floor.totalSpots).map { index ->
                val spotNumber = "$prefix-${String.format("%02d", index + 1)}"
                val taken = spotNumber in booked
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
     * Creates the booking, its bay locks and +1 occupancy for every 30-minute slot of the stay,
     * in one transaction.
     *
     * Bay locks (one per bay per slot) stop two drivers taking the same bay at the same time:
     * Firestore retries the loser, which then sees the winner's lock. Occupancy counts per slot
     * make "free now" and "full" time-aware: a booking for next week never changes today.
     * [capacity] (the parking's bays for this vehicle type, and on this floor) refuses slots
     * that are already full. Any error fails the booking (never "assume it's free").
     */
    suspend fun createBooking(booking: Booking, capacity: SlotCapacity? = null): Result<Booking> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("You're signed out. Log in and try again."))

        BookingRules.timeProblem(booking.entryTime.toDate().time, booking.exitTime.toDate().time, AppServices.clock.nowMillis())
            ?.let { return Result.failure(Exception(it)) }

        val bookingId = newBookingId()
        val newBooking = booking.copy(
            id = bookingId,
            userId = userId,
            qrCodeData = "PARKOVER:$bookingId",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        val buckets = lockBuckets(booking.entryTime.toDate().time, booking.exitTime.toDate().time)
        val type = booking.vehicleType.name
        val floorKey = booking.floorNumber.toString()

        return try {
            requireServer(occupancyCollection.document(occupancyId(booking.parkingId, buckets.first()))) // public read
            retryIfRefused { firestore.runTransaction { transaction ->
                // All reads first (Firestore requires reads before writes).
                val lockRefs = buckets.map { slotLocksCollection.document(lockId(booking.parkingId, booking.spotNumber, it)) }
                if (lockRefs.any { transaction.get(it).exists() }) throw SlotTakenException(booking.spotNumber)
                val occSnaps = buckets.map { transaction.get(occupancyCollection.document(occupancyId(booking.parkingId, it))) }
                if (capacity != null) {
                    if (occSnaps.any { occupiedCount(it, type) >= capacity.type }) {
                        throw Exception("This parking is full for your vehicle type at that time. Try another time.")
                    }
                    if (capacity.floor != null && occSnaps.any { occupiedFloor(it, floorKey) >= capacity.floor }) {
                        throw Exception("${booking.floorName} is full at that time. Pick another floor or time.")
                    }
                }

                transaction.set(bookingsCollection.document(bookingId), newBooking)
                buckets.zip(lockRefs).forEach { (bucket, ref) ->
                    transaction.set(ref, lockData(booking.parkingId, booking.spotNumber, bucket, bookingId, userId))
                }
                buckets.zip(occSnaps).forEach { (bucket, snap) ->
                    writeOccupancy(transaction, snap, booking.parkingId, bucket, type, floorKey, +1, bookingId)
                }
            }.await() }
            Result.success(newBooking)
        } catch (e: Exception) {
            Result.failure(BookingRules.errorFor(e))
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

    /**
     * Cancels an active booking, frees its bay locks and gives back -1 occupancy for each slot,
     * once. Everything comes from the booking as stored now (inside the transaction), not the
     * copy on screen, which may be stale (e.g. extended on another phone).
     */
    suspend fun cancelBooking(booking: Booking): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: throw Exception("You're signed out. Log in and try again.")
        val bookingRef = bookingsCollection.document(booking.id)
        try {
            requireServer(bookingRef)
            retryIfRefused { firestore.runTransaction { transaction ->
                val fresh = transaction.get(bookingRef)
                val status = fresh.getString("bookingStatus")
                if (!fresh.exists() || fresh.getString("userId") != userId) throw Exception("Booking not found.")
                if (status !in ACTIVE_STATUSES) throw Exception("This booking is already ${status?.lowercase() ?: "closed"}.")
                val current = fresh.toObject(Booking::class.java)!!
                val type = current.vehicleType.name
                val floorKey = current.floorNumber.toString()

                val buckets = lockBuckets(current.entryTime.toDate().time, current.exitTime.toDate().time)
                val lockSnaps = buckets.map { transaction.get(slotLocksCollection.document(lockId(current.parkingId, current.spotNumber, it))) }
                val occSnaps = buckets.map { transaction.get(occupancyCollection.document(occupancyId(current.parkingId, it))) }

                transaction.update(bookingRef, mapOf("bookingStatus" to BookingStatus.CANCELLED.name, "updatedAt" to Timestamp.now()))
                // Only this booking's locks; older bookings made before locks existed have none.
                lockSnaps.filter { it.exists() && it.getString("bookingId") == current.id }
                    .forEach { transaction.delete(it.reference) }
                // Bookings made before occupancy existed have nothing to give back: skip empty slots.
                buckets.zip(occSnaps)
                    .filter { (_, snap) -> occupiedCount(snap, type) > 0 && occupiedFloor(snap, floorKey) > 0 }
                    .forEach { (bucket, snap) -> writeOccupancy(transaction, snap, current.parkingId, bucket, type, floorKey, -1, current.id) }
            }.await() }
        } catch (e: Exception) {
            throw BookingRules.errorFor(e)
        }
        Unit
    }

    /**
     * Adds [hours] to an active booking, only if the bay (and, with [capacity], the parking) is
     * free for the extra time. Uses the stored end time, so two quick extends add up instead of
     * colliding with each other.
     */
    suspend fun extendBooking(
        booking: Booking, hours: Int, ratePerHour: Double, taxRate: Double, capacity: SlotCapacity? = null
    ): Result<Booking> = runCatching {
        val userId = auth.currentUser?.uid ?: throw Exception("You're signed out. Log in and try again.")
        val bookingRef = bookingsCollection.document(booking.id)
        try {
            requireServer(bookingRef)
            retryIfRefused { firestore.runTransaction { transaction ->
                val fresh = transaction.get(bookingRef)
                if (!fresh.exists() || fresh.getString("userId") != userId) throw Exception("Booking not found.")
                if (fresh.getString("bookingStatus") !in ACTIVE_STATUSES) throw Exception("Only active bookings can be extended.")
                val current = fresh.toObject(Booking::class.java)!!
                val type = current.vehicleType.name
                val floorKey = current.floorNumber.toString()
                val oldExit = current.exitTime.toDate().time
                val newExitMs = oldExit + hours * 3_600_000L
                val held = lockBuckets(current.entryTime.toDate().time, oldExit).toSet()
                val extra = lockBuckets(oldExit, newExitMs).filter { it !in held }
                val lockSnaps = extra.map { transaction.get(slotLocksCollection.document(lockId(current.parkingId, current.spotNumber, it))) }
                // A lock of this same booking (from a quick second extend) isn't a clash; the commit
                // check will retry us with fresh data. Only someone else's lock is.
                if (lockSnaps.any { it.exists() && it.getString("bookingId") != current.id }) {
                    throw Exception("Spot ${current.spotNumber} is booked by someone else right after your slot, so it can't be extended.")
                }
                val occSnaps = extra.map { transaction.get(occupancyCollection.document(occupancyId(current.parkingId, it))) }
                if (capacity != null && occSnaps.any { occupiedCount(it, type) >= capacity.type }) {
                    throw Exception("This parking is full for your vehicle type then, so it can't be extended.")
                }
                val extraBase = ratePerHour * hours
                val updated = current.copy(
                    exitTime = Timestamp(Date(newExitMs)),
                    durationHours = current.durationHours + hours,
                    basePrice = current.basePrice + extraBase,
                    taxAmount = current.taxAmount + extraBase * taxRate,
                    totalPrice = current.totalPrice + extraBase * (1 + taxRate),
                    updatedAt = Timestamp.now()
                )
                transaction.set(bookingRef, updated)
                extra.indices.filterNot { lockSnaps[it].exists() }.forEach { i ->
                    transaction.set(lockSnaps[i].reference, lockData(current.parkingId, current.spotNumber, extra[i], current.id, userId))
                    writeOccupancy(transaction, occSnaps[i], current.parkingId, extra[i], type, floorKey, +1, current.id)
                }
                updated
            }.await() }
        } catch (e: Exception) {
            throw BookingRules.errorFor(e)
        }
    }

    // ---------- Occupancy ----------

    private fun occupiedCount(snap: DocumentSnapshot, type: String): Long = if (snap.exists()) snap.getLong(type) ?: 0 else 0

    private fun occupiedFloor(snap: DocumentSnapshot, floorKey: String): Long =
        if (snap.exists()) (snap.get(FieldPath.of("floors", floorKey)) as? Number)?.toLong() ?: 0 else 0

    /** +1/-1 on one slot for one booking (its type and floor), creating the slot doc if needed. */
    private fun writeOccupancy(
        transaction: Transaction, snap: DocumentSnapshot, parkingId: String, bucket: Long,
        type: String, floorKey: String, delta: Long, bookingId: String
    ) {
        if (snap.exists()) {
            transaction.update(
                snap.reference,
                FieldPath.of(type), FieldValue.increment(delta),
                FieldPath.of("floors", floorKey), FieldValue.increment(delta),
                FieldPath.of("lastBookingId"), bookingId,
                FieldPath.of("lastUpdated"), Timestamp.now()
            )
        } else {
            transaction.set(snap.reference, mapOf(
                "parkingId" to parkingId,
                "bucket" to bucket,
                type to delta,
                "floors" to mapOf(floorKey to delta),
                "lastBookingId" to bookingId,
                "lastUpdated" to Timestamp.now()
            ))
        }
    }

    /**
     * Firestore holds a transaction while offline and quietly commits it once the connection is
     * back, so a driver who gave up at a spinner could still end up booked. Check the server is
     * reachable first and fail fast instead. [ref] must be a document this user may read.
     */
    private suspend fun requireServer(ref: com.google.firebase.firestore.DocumentReference) {
        val offline = FirebaseFirestoreException("Offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        val result = withTimeoutOrNull(ONLINE_CHECK_MS) {
            runCatching { ref.get(com.google.firebase.firestore.Source.SERVER).await() }
        } ?: throw offline
        result.exceptionOrNull()?.let { e ->
            if (e is FirebaseFirestoreException && e.code != FirebaseFirestoreException.Code.UNAVAILABLE) throw e
            throw offline
        }
    }

    /**
     * Under a rush, a transaction can be refused because another driver's write landed between
     * our read and commit. Run it again (it re-reads) so the driver gets the real outcome
     * ("just booked", "full") or success, not a generic refusal. A real refusal fails 3 times.
     */
    private suspend fun <T> retryIfRefused(block: suspend () -> T): T {
        repeat(2) {
            try {
                return block()
            } catch (e: Exception) {
                val chain = generateSequence<Throwable>(e) { it.cause }.toList()
                val refused = chain.any { it is FirebaseFirestoreException && it.code == FirebaseFirestoreException.Code.PERMISSION_DENIED }
                if (!refused || chain.any { it is SlotTakenException }) throw e
            }
        }
        return block()
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

    companion object {
        const val SLOT_LOCKS = "slot_locks"
        const val OCCUPANCY = "occupancy"
        /** Bays are locked in 30-minute buckets; check-in times are on the half hour. */
        const val LOCK_BUCKET_MS = 30 * 60_000L
        /** How long to wait for the server before saying "you're offline". */
        const val ONLINE_CHECK_MS = 10_000L
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

        fun occupancyId(parkingId: String, bucket: Long) = "${parkingId}_$bucket"

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
