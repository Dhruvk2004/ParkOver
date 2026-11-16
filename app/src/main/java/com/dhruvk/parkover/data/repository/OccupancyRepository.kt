package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.data.model.VehicleType
import com.dhruvk.parkover.di.AppServices
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** Booked bays in one 30-minute slot of one parking: per vehicle type and per floor. */
data class SlotOccupancy(
    val byType: Map<VehicleType, Int> = emptyMap(),
    val byFloor: Map<String, Int> = emptyMap()
)

/**
 * "Who is parked right now": the occupancy of the current 30-minute slot for every parking.
 * Only slots with bookings have a document, so this reads one doc per busy parking, not one
 * per parking. It re-subscribes at every :00 and :30, when "now" moves to the next slot.
 */
class OccupancyRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNow(): Flow<Map<String, SlotOccupancy>> = currentBucket().flatMapLatest { bucket -> observeBucket(bucket) }

    private fun currentBucket(): Flow<Long> = flow {
        while (true) {
            val now = AppServices.clock.nowMillis()
            emit(BookingRepository.bucketOf(now))
            val nextSlot = (BookingRepository.bucketOf(now) + 1) * BookingRepository.LOCK_BUCKET_MS
            delay(nextSlot - now + 500)
        }
    }.distinctUntilChanged()

    private fun observeBucket(bucket: Long): Flow<Map<String, SlotOccupancy>> = callbackFlow {
        val registration = firestore.collection(BookingRepository.OCCUPANCY)
            .whereEqualTo("bucket", bucket)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener // keep the last good numbers
                trySend(snapshot.documents.mapNotNull { doc -> doc.getString("parkingId")?.let { it to parse(doc) } }.toMap())
            }
        awaitClose { registration.remove() }
    }

    companion object {
        fun parse(doc: DocumentSnapshot): SlotOccupancy {
            @Suppress("UNCHECKED_CAST")
            val floors = (doc.get("floors") as? Map<String, Any?>).orEmpty()
            return SlotOccupancy(
                byType = VehicleType.entries.associateWith { (doc.getLong(it.name) ?: 0).toInt() },
                byFloor = floors.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 0 }
            )
        }
    }
}
