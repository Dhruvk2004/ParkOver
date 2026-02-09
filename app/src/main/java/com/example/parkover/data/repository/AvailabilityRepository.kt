package com.example.parkover.data.repository

import com.example.parkover.data.model.ParkingAvailability
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.data.model.VehicleType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
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
    
    // Real-time listener for all spots' availability
    fun getAllAvailabilityRealtime(): Flow<Map<String, ParkingAvailability>> = callbackFlow {
        val listener = availabilityCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyMap())
                return@addSnapshotListener
            }
            
            val availabilityMap = mutableMapOf<String, ParkingAvailability>()
            snapshot?.documents?.forEach { doc ->
                doc.toObject(ParkingAvailability::class.java)?.let {
                    availabilityMap[it.spotId] = it
                }
            }
            trySend(availabilityMap)
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
    
    // Decrease availability when booking is made
    suspend fun decreaseAvailability(
        spotId: String,
        vehicleType: VehicleType,
        floorNumber: Int? = null
    ): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val docRef = availabilityCollection.document(spotId)
                val snapshot = transaction.get(docRef)
                val current = snapshot.toObject(ParkingAvailability::class.java)
                    ?: throw Exception("Availability not found")
                
                val updates = mutableMapOf<String, Any>()
                
                when (vehicleType) {
                    VehicleType.TWO_WHEELER -> {
                        if (current.availableSpotsTwoWheeler <= 0) {
                            throw Exception("No spots available for two wheelers")
                        }
                        updates["availableSpotsTwoWheeler"] = current.availableSpotsTwoWheeler - 1
                    }
                    VehicleType.FOUR_WHEELER -> {
                        if (current.availableSpotsFourWheeler <= 0) {
                            throw Exception("No spots available for four wheelers")
                        }
                        updates["availableSpotsFourWheeler"] = current.availableSpotsFourWheeler - 1
                    }
                    VehicleType.HEAVY -> {
                        if (current.availableSpotsHeavy <= 0) {
                            throw Exception("No spots available for heavy vehicles")
                        }
                        updates["availableSpotsHeavy"] = current.availableSpotsHeavy - 1
                    }
                }
                
                // Update floor availability if specified
                floorNumber?.let { floor ->
                    val floorKey = floor.toString()
                    val currentFloorAvail = current.floorAvailability[floorKey] ?: 0
                    if (currentFloorAvail > 0) {
                        val newFloorAvailability = current.floorAvailability.toMutableMap()
                        newFloorAvailability[floorKey] = currentFloorAvail - 1
                        updates["floorAvailability"] = newFloorAvailability
                    }
                }
                
                updates["lastUpdated"] = Timestamp.now()
                transaction.update(docRef, updates)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Increase availability when booking ends or is cancelled
    suspend fun increaseAvailability(
        spotId: String,
        vehicleType: VehicleType,
        floorNumber: Int? = null,
        maxTwoWheeler: Int,
        maxFourWheeler: Int,
        maxHeavy: Int
    ): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val docRef = availabilityCollection.document(spotId)
                val snapshot = transaction.get(docRef)
                val current = snapshot.toObject(ParkingAvailability::class.java)
                    ?: throw Exception("Availability not found")
                
                val updates = mutableMapOf<String, Any>()
                
                when (vehicleType) {
                    VehicleType.TWO_WHEELER -> {
                        val newCount = minOf(current.availableSpotsTwoWheeler + 1, maxTwoWheeler)
                        updates["availableSpotsTwoWheeler"] = newCount
                    }
                    VehicleType.FOUR_WHEELER -> {
                        val newCount = minOf(current.availableSpotsFourWheeler + 1, maxFourWheeler)
                        updates["availableSpotsFourWheeler"] = newCount
                    }
                    VehicleType.HEAVY -> {
                        val newCount = minOf(current.availableSpotsHeavy + 1, maxHeavy)
                        updates["availableSpotsHeavy"] = newCount
                    }
                }
                
                updates["lastUpdated"] = Timestamp.now()
                transaction.update(docRef, updates)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Check if availability exists for spots
    suspend fun checkAndInitializeAvailability(spots: List<ParkingSpot>) {
        try {
            val existingDocs = availabilityCollection.get().await()
            val existingIds = existingDocs.documents.map { it.id }.toSet()
            
            val missingSpots = spots.filter { it.id !in existingIds }
            if (missingSpots.isNotEmpty()) {
                initializeAllAvailability(missingSpots)
            }
        } catch (e: Exception) {
            // Log error
        }
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
