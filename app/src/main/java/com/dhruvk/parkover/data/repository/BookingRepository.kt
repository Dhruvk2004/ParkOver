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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID

/** [firestore]/[auth] are the app's defaults; E2E tests pass a second Firebase app to act as another driver. */
class BookingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

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

        return try {
            requireServer(availabilityCollection.document(booking.parkingId)) // public, readable before booking
            retryIfRefused { firestore.runTransaction { transaction ->
                // All reads first (Firestore requires reads before writes).
                val lockRefs = buckets.map { slotLocksCollection.document(lockId(booking.parkingId, booking.spotNumber, it)) }
                if (lockRefs.any { transaction.get(it).exists() }) throw SlotTakenException(booking.spotNumber)
                val availRef = availabilityCollection.document(booking.parkingId)
                val availSnapshot = transaction.get(availRef)

                var countUpdate: List<Any>? = null
                if (availSnapshot.exists()) {
                    val field = availabilityField(booking.vehicleType)
                    if ((availSnapshot.getLong(field) ?: 0) <= 0) throw Exception("This parking is full for your vehicle type.")
                    @Suppress("UNCHECKED_CAST")
                    val floorAvail = availSnapshot.get("floorAvailability") as? Map<String, Long>
                    val floorKey = booking.floorNumber.toString()
                    val tracksFloor = floorAvail?.containsKey(floorKey) == true
                    if (tracksFloor && (floorAvail!![floorKey] ?: 0) <= 0) throw Exception("${booking.floorName} is full. Pick another floor.")
                    // increment(-1), not "value read minus one": two drivers booking different bays
                    // at once must both succeed. The rules still allow exactly -1, never below 0,
                    // and only for a booking created in this same write (lastBookingId).
                    countUpdate = countChange(field, floorKey.takeIf { tracksFloor }, -1, bookingId)
                }

                transaction.set(bookingsCollection.document(bookingId), newBooking)
                buckets.zip(lockRefs).forEach { (bucket, ref) ->
                    transaction.set(ref, lockData(booking.parkingId, booking.spotNumber, bucket, bookingId, userId))
                }
                countUpdate?.let { applyCountChange(transaction, availRef, it) }
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
     * Cancels an active booking, frees its slot locks and gives the bay back, once.
     * Everything is computed from the booking as stored now (inside the transaction), not from
     * the copy on screen, which may be stale (e.g. extended on another phone).
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

                val buckets = lockBuckets(current.entryTime.toDate().time, current.exitTime.toDate().time)
                val lockSnaps = buckets.map { transaction.get(slotLocksCollection.document(lockId(current.parkingId, current.spotNumber, it))) }
                val availRef = availabilityCollection.document(current.parkingId)
                val availSnapshot = transaction.get(availRef)

                transaction.update(bookingRef, mapOf("bookingStatus" to BookingStatus.CANCELLED.name, "updatedAt" to Timestamp.now()))
                // Only this booking's locks; older bookings made before locks existed have none.
                lockSnaps.filter { it.exists() && it.getString("bookingId") == current.id }
                    .forEach { transaction.delete(it.reference) }
                if (availSnapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val floorAvail = availSnapshot.get("floorAvailability") as? Map<String, Long>
                    val floorKey = current.floorNumber.toString()
                    val change = countChange(availabilityField(current.vehicleType), floorKey.takeIf { floorAvail?.containsKey(it) == true }, +1, current.id)
                    applyCountChange(transaction, availRef, change)
                }
            }.await() }
        } catch (e: Exception) {
            throw BookingRules.errorFor(e)
        }
        Unit
    }

    /**
     * Adds [hours] to an active booking, only if the bay is free for the extra time. Uses the
     * stored end time, so two quick extends add up instead of colliding with each other.
     */
    suspend fun extendBooking(booking: Booking, hours: Int, ratePerHour: Double, taxRate: Double): Result<Booking> = runCatching {
        val userId = auth.currentUser?.uid ?: throw Exception("You're signed out. Log in and try again.")
        val bookingRef = bookingsCollection.document(booking.id)
        try {
            requireServer(bookingRef)
            retryIfRefused { firestore.runTransaction { transaction ->
                val fresh = transaction.get(bookingRef)
                if (!fresh.exists() || fresh.getString("userId") != userId) throw Exception("Booking not found.")
                if (fresh.getString("bookingStatus") !in ACTIVE_STATUSES) throw Exception("Only active bookings can be extended.")
                val current = fresh.toObject(Booking::class.java)!!
                val oldExit = current.exitTime.toDate().time
                val newExitMs = oldExit + hours * 3_600_000L
                val held = lockBuckets(current.entryTime.toDate().time, oldExit).toSet()
                val extra = lockBuckets(oldExit, newExitMs).filter { it !in held }
                val lockRefs = extra.map { slotLocksCollection.document(lockId(current.parkingId, current.spotNumber, it)) }
                val lockSnaps = lockRefs.map { transaction.get(it) }
                // A lock of this same booking (from a quick second extend) isn't a clash; the commit
                // check will retry us with fresh data. Only someone else's lock is.
                if (lockSnaps.any { it.exists() && it.getString("bookingId") != current.id }) {
                    throw Exception("Spot ${current.spotNumber} is booked by someone else right after your slot, so it can't be extended.")
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
                extra.zip(lockSnaps).filterNot { (_, snap) -> snap.exists() }.forEach { (bucket, snap) ->
                    transaction.set(snap.reference, lockData(current.parkingId, current.spotNumber, bucket, current.id, userId))
                }
                updated
            }.await() }
        } catch (e: Exception) {
            throw BookingRules.errorFor(e)
        }
    }

    private fun availabilityField(type: VehicleType) = when (type) {
        VehicleType.TWO_WHEELER -> "availableSpotsTwoWheeler"
        VehicleType.FOUR_WHEELER -> "availableSpotsFourWheeler"
        VehicleType.HEAVY -> "availableSpotsHeavy"
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

    /** Field/value pairs for a ±1 change of a parking's free counts, tied to [bookingId]. */
    private fun countChange(field: String, floorKey: String?, delta: Long, bookingId: String): List<Any> = buildList {
        add(FieldPath.of(field)); add(FieldValue.increment(delta))
        if (floorKey != null) { add(FieldPath.of("floorAvailability", floorKey)); add(FieldValue.increment(delta)) }
        add(FieldPath.of("lastUpdated")); add(Timestamp.now())
        add(FieldPath.of("lastBookingId")); add(bookingId)
    }

    private fun applyCountChange(
        transaction: com.google.firebase.firestore.Transaction,
        ref: com.google.firebase.firestore.DocumentReference,
        pairs: List<Any>
    ) {
        transaction.update(ref, pairs[0] as FieldPath, pairs[1], *pairs.drop(2).toTypedArray())
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
