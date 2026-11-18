package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.data.model.Booking
import com.dhruvk.parkover.data.model.BookingStatus
import com.dhruvk.parkover.data.model.PaymentMethod
import com.dhruvk.parkover.data.model.PaymentStatus
import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.data.repository.BookingRepository.Companion.bucketOf
import com.dhruvk.parkover.data.repository.BookingRepository.Companion.lockBuckets
import com.dhruvk.parkover.data.repository.BookingRepository.Companion.lockId
import com.dhruvk.parkover.data.repository.BookingRepository.Companion.occupancyId
import com.dhruvk.parkover.di.AppServices
import com.dhruvk.parkover.domain.BookingRules
import com.dhruvk.parkover.domain.OperatorRules
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

/**
 * The parking place an operator account runs. ParkOver creates `operators/{uid}` by hand in the
 * Firebase console (nobody can sign themselves up as an operator), so the account decides the role.
 */
data class OperatorProfile(
    val uid: String = "",
    val name: String = "",
    val parkingId: String = "",
    val parkingName: String = ""
)

/** One bay held for one 30-minute slot (public: no personal details). */
data class BayLock(val spotNumber: String, val bucket: Long, val bookingId: String)

/** Why the operator blocked a bay. */
enum class BlockReason(val label: String) {
    WalkIn("Walk-in car parked"),
    Repair("Repair or blocked"),
    Staff("Kept for staff")
}

class OperatorRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val bookings: BookingRepository = BookingRepository(firestore, auth)
) {
    private val bookingsCollection = firestore.collection("bookings")
    private val slotLocks = firestore.collection(BookingRepository.SLOT_LOCKS)
    private val occupancy = firestore.collection(BookingRepository.OCCUPANCY)

    /** The signed-in account's operator profile, or null for a driver (or when it can't be read). */
    suspend fun loadProfile(): OperatorProfile? {
        val user = auth.currentUser ?: return null
        return try {
            withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
                val snap = firestore.collection(OPERATORS).document(user.uid).get().await()
                val parkingId = snap.getString("parkingId")
                if (!snap.exists() || parkingId.isNullOrBlank()) null
                else OperatorProfile(
                    uid = user.uid,
                    name = snap.getString("name") ?: user.displayName.orEmpty(),
                    parkingId = parkingId,
                    parkingName = snap.getString("parkingName").orEmpty()
                )
            }
        } catch (e: Exception) {
            null // Not an operator (the rules only let you read your own document)
        }
    }

    /** Every booking at the operator's parking place, live. Today's are picked out on the phone. */
    fun observeBookings(parkingId: String): Flow<Result<List<Booking>>> = callbackFlow {
        val registration = bookingsCollection
            .whereEqualTo("parkingId", parkingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) trySend(Result.failure(error))
                else trySend(Result.success(snapshot?.toObjects(Booking::class.java).orEmpty()))
            }
        awaitClose { registration.remove() }
    }

    /**
     * Bays held at this parking place, live, from [fromBucket] on. Filtered on the phone: a
     * range on "bucket" next to the parkingId filter would need a composite index.
     */
    fun observeLocks(parkingId: String, fromBucket: Long): Flow<List<BayLock>> = callbackFlow {
        val registration = slotLocks
            .whereEqualTo("parkingId", parkingId)
            .addSnapshotListener { snapshot, _ ->
                val locks = snapshot?.documents.orEmpty().mapNotNull { d ->
                    BayLock(
                        spotNumber = d.getString("spotNumber") ?: return@mapNotNull null,
                        bucket = d.getLong("bucket") ?: return@mapNotNull null,
                        bookingId = d.getString("bookingId").orEmpty()
                    )
                }.filter { it.bucket >= fromBucket }
                trySend(locks)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getBooking(id: String): Booking? = bookings.getBooking(id)

    /** Booked → Arrived. Only for a booking that is still waiting. */
    suspend fun checkIn(bookingId: String): Result<Unit> = runCatching {
        val ref = bookingsCollection.document(bookingId)
        try {
            bookings.requireServer(ref)
            bookings.retryIfRefused { firestore.runTransaction { tx ->
                val fresh = tx.get(ref)
                if (!fresh.exists()) throw Exception("Booking not found.")
                when (fresh.getString("bookingStatus")) {
                    BookingStatus.PENDING.name, BookingStatus.CONFIRMED.name -> Unit
                    BookingStatus.ACTIVE.name -> throw Exception("This car is already checked in.")
                    BookingStatus.CANCELLED.name -> throw Exception("The driver cancelled this booking.")
                    else -> throw Exception("This booking is already closed.")
                }
                val now = Timestamp(Date(AppServices.clock.nowMillis()))
                tx.update(ref, mapOf(
                    "bookingStatus" to BookingStatus.ACTIVE.name,
                    "checkedInAt" to now,
                    "updatedAt" to now
                ))
            }.await() }
        } catch (e: Exception) {
            throw BookingRules.errorFor(e)
        }
        Unit
    }

    /**
     * Arrived → Exited. Works out any overstay charge and frees the bay for the rest of the
     * booked time (its later slot locks and +1 occupancy), all in one transaction.
     * Returns the extra amount to collect.
     */
    suspend fun checkOut(bookingId: String): Result<Double> = runCatching {
        val ref = bookingsCollection.document(bookingId)
        try {
            bookings.requireServer(ref)
            bookings.retryIfRefused { firestore.runTransaction { tx ->
                val fresh = tx.get(ref)
                if (!fresh.exists()) throw Exception("Booking not found.")
                if (fresh.getString("bookingStatus") != BookingStatus.ACTIVE.name) {
                    throw Exception("Only a parked car can be checked out.")
                }
                val current = fresh.toObject(Booking::class.java)!!
                val nowMs = AppServices.clock.nowMillis()
                val extra = OperatorRules.overstayCharge(current, nowMs)
                val type = current.vehicleType.name
                val floorKey = current.floorNumber.toString()

                // Slots after this one are no longer needed: give them back.
                val release = lockBuckets(current.entryTime.toDate().time, current.exitTime.toDate().time)
                    .filter { it > bucketOf(nowMs) }
                val lockSnaps = release.map { tx.get(slotLocks.document(lockId(current.parkingId, current.spotNumber, it))) }
                val occSnaps = release.map { tx.get(occupancy.document(occupancyId(current.parkingId, it))) }

                val now = Timestamp(Date(nowMs))
                tx.update(ref, mapOf(
                    "bookingStatus" to BookingStatus.COMPLETED.name,
                    "actualExitTime" to now,
                    "extraCharges" to extra,
                    "updatedAt" to now
                ))
                lockSnaps.filter { it.exists() && it.getString("bookingId") == current.id }
                    .forEach { tx.delete(it.reference) }
                release.zip(occSnaps)
                    .filter { (_, snap) -> bookings.occupiedCount(snap, type) > 0 && bookings.occupiedFloor(snap, floorKey) > 0 }
                    .forEach { (bucket, snap) -> bookings.writeOccupancy(tx, snap, current.parkingId, bucket, type, floorKey, -1, current.id) }
                extra
            }.await() }
        } catch (e: Exception) {
            throw BookingRules.errorFor(e)
        }
    }

    /**
     * Blocks a free bay from now until [untilMs] (a walk-in car, a repair, staff parking) so the
     * app stops selling it. Stored as a booking owned by the operator, so it takes the same bay
     * locks and occupancy as any booking; freeing it is a check-out.
     */
    suspend fun blockBay(
        profile: OperatorProfile,
        floorNumber: Int,
        floorName: String,
        spotNumber: String,
        untilMs: Long,
        reason: BlockReason,
        vehicleNumber: String
    ): Result<Booking> {
        val now = AppServices.clock.nowMillis()
        val hours = ((untilMs - now + BookingRules.HOUR_MS - 1) / BookingRules.HOUR_MS).toInt().coerceAtLeast(1)
        val block = Booking(
            parkingId = profile.parkingId,
            parkingName = profile.parkingName,
            vehicleNumber = vehicleNumber.trim().uppercase(),
            vehicleType = VehicleType.FOUR_WHEELER,
            floorNumber = floorNumber,
            floorName = floorName,
            spotNumber = spotNumber,
            entryTime = Timestamp(Date(now)),
            exitTime = Timestamp(Date(untilMs)),
            durationHours = hours,
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = PaymentStatus.COMPLETED, // Nothing to pay in the app
            bookingStatus = BookingStatus.ACTIVE, // Already in the bay
            checkedInAt = Timestamp(Date(now)),
            driverName = reason.label,
            walkIn = true,
            blockReason = reason.name
        )
        // No capacity check: the car is physically there whatever the counts say.
        return bookings.createBooking(block, capacity = null)
    }

    companion object {
        const val OPERATORS = "operators"
        private const val PROFILE_TIMEOUT_MS = 8_000L
    }
}
