package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.data.model.ParkingAvailability
import com.dhruvk.parkover.data.model.ParkingSpot
import com.dhruvk.parkover.data.model.VehicleType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AvailabilityRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val availabilityCollection = firestore.collection("parking_availability")
    
    // Real-time listener for a single spot's availability
    fun getAvailabilityRealtime(spotId: String): Flow<ParkingAvailability?> = callbackFlow {
        val listener = availabilityCollection.document(spotId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val availability = snapshot?.toObject(ParkingAvailability::class.java)
                trySend(availability)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Live counts for every parking. [fromServer] is false while the data only comes from the
     * local cache, which may be incomplete, so never decide "this parking has no doc" from it.
     */
    fun observeAllAvailability(): Flow<AvailabilitySnapshot> = callbackFlow {
        // INCLUDE metadata so we also hear when the cached data is confirmed by the server.
        val listener = availabilityCollection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(AvailabilitySnapshot(emptyMap(), emptySet(), fromServer = false))
                return@addSnapshotListener
            }
            val byParking = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ParkingAvailability::class.java)?.let { it.spotId.ifBlank { doc.id } to it }
            }.toMap()
            trySend(AvailabilitySnapshot(byParking, snapshot.documents.map { it.id }.toSet(), !snapshot.metadata.isFromCache))
        }
        awaitClose { listener.remove() }
    }
    
    // Initialize availability for a spot (called when spot doesn't exist in Firestore)
    suspend fun initializeAvailability(spot: ParkingSpot): Result<Unit> {
        return try {
            val availability = ParkingAvailability(
                spotId = spot.id,
                availableSpotsTwoWheeler = spot.totalSpotsTwoWheeler,
                availableSpotsFourWheeler = spot.totalSpotsFourWheeler,
                availableSpotsHeavy = spot.totalSpotsHeavy,
                floorAvailability = spot.floors.associate { 
                    it.floorNumber.toString() to it.totalSpots 
                },
                lastUpdated = Timestamp.now()
            )
            availabilityCollection.document(spot.id).set(availability).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Initialize availability for multiple spots
    suspend fun initializeAllAvailability(spots: List<ParkingSpot>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            spots.forEach { spot ->
                val docRef = availabilityCollection.document(spot.id)
                val availability = ParkingAvailability(
                    spotId = spot.id,
                    availableSpotsTwoWheeler = spot.totalSpotsTwoWheeler,
                    availableSpotsFourWheeler = spot.totalSpotsFourWheeler,
                    availableSpotsHeavy = spot.totalSpotsHeavy,
                    floorAvailability = spot.floors.associate { 
                        it.floorNumber.toString() to it.totalSpots 
                    },
                    lastUpdated = Timestamp.now()
                )
                batch.set(docRef, availability)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /** Creates live-count docs for parkings that don't have one yet ([knownIds] from a server snapshot). */
    suspend fun seedMissing(spots: List<ParkingSpot>, knownIds: Set<String>) {
        val missing = spots.filter { it.id !in knownIds }
        if (missing.isNotEmpty()) initializeAllAvailability(missing)
    }

    companion object {
        @Volatile
        private var INSTANCE: AvailabilityRepository? = null
        
        fun getInstance(): AvailabilityRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AvailabilityRepository().also { INSTANCE = it }
            }
        }
    }
}

data class AvailabilitySnapshot(
    val byParking: Map<String, ParkingAvailability>,
    val docIds: Set<String>,
    val fromServer: Boolean
)
