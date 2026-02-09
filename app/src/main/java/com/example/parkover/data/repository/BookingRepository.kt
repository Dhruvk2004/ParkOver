package com.example.parkover.data.repository

import com.example.parkover.data.model.Booking
import com.example.parkover.data.model.BookingStatus
import com.example.parkover.data.model.FloorData
import com.example.parkover.data.model.ParkingSlot
import com.example.parkover.data.model.ParkingSpot
import com.example.parkover.data.model.Vehicle
import com.example.parkover.data.model.VehicleType
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.UUID

class BookingRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val bookingsCollection = firestore.collection("bookings")
    private val parkingSpotsCollection = firestore.collection("parkingSpots")
    private val vehiclesCollection = firestore.collection("vehicles")
    private val usersCollection = firestore.collection("users")
    
    // Get parking spot details
    suspend fun getParkingSpot(parkingId: String): ParkingSpot? {
        return try {
            val doc = parkingSpotsCollection.document(parkingId).get().await()
            doc.toObject(ParkingSpot::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    // Get user's vehicles
    suspend fun getUserVehicles(): List<Vehicle> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = vehiclesCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            snapshot.toObjects(Vehicle::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Add a new vehicle
    suspend fun addVehicle(vehicle: Vehicle): Result<Vehicle> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val vehicleId = UUID.randomUUID().toString()
            val newVehicle = vehicle.copy(id = vehicleId, userId = userId)
            vehiclesCollection.document(vehicleId).set(newVehicle).await()
            Result.success(newVehicle)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get floors and spots for a parking location
    suspend fun getFloorsWithSpots(parkingId: String, vehicleType: VehicleType): List<FloorData> {
        // Try to get real floor data from Firestore first
        return try {
            val parkingDoc = parkingSpotsCollection.document(parkingId).get().await()
            val parkingSpot = parkingDoc.toObject(ParkingSpot::class.java)
            
            if (parkingSpot != null && parkingSpot.floors.isNotEmpty()) {
                // Convert API floors to FloorData with spots
                parkingSpot.floors.map { floor ->
                    val spots = generateSpotsForFloor(
                        parkingId = parkingId,
                        floorNumber = floor.floorNumber,
                        totalSpots = floor.totalSpots,
                        availableSpots = floor.availableSpots,
                        vehicleType = vehicleType
                    )
                    FloorData(
                        floorNumber = floor.floorNumber,
                        name = floor.name,
                        spots = spots,
                        totalSpots = floor.totalSpots,
                        availableSpots = floor.availableSpots
                    )
                }
            } else {
                // Fallback to mock data
                generateMockFloors(vehicleType)
            }
        } catch (e: Exception) {
            // Fallback to mock data on error
            generateMockFloors(vehicleType)
        }
    }
    
    // Generate spots for a floor based on availability
    private suspend fun generateSpotsForFloor(
        parkingId: String,
        floorNumber: Int,
        totalSpots: Int,
        availableSpots: Int,
        vehicleType: VehicleType
    ): List<ParkingSlot> {
        val spots = mutableListOf<ParkingSlot>()
        val occupiedCount = totalSpots - availableSpots
        
        // Get booked spots from Firestore
        val bookedSpots = try {
            bookingsCollection
                .whereEqualTo("parkingId", parkingId)
                .whereEqualTo("floorNumber", floorNumber)
                .whereIn("bookingStatus", listOf(
                    BookingStatus.CONFIRMED.name,
                    BookingStatus.ACTIVE.name
                ))
                .get()
                .await()
                .documents
                .mapNotNull { it.getString("spotNumber") }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
        
        val spotsPerRow = 2
        val totalRows = (totalSpots + 1) / 2
        
        for (row in 0 until totalRows) {
            for (col in 0 until spotsPerRow) {
                val spotIndex = row * 2 + col
                if (spotIndex >= totalSpots) break
                
                val spotNumber = "${('A' + floorNumber - 1)}${String.format("%02d", spotIndex + 1)}"
                val isBooked = bookedSpots.contains(spotNumber) || spotIndex < occupiedCount
                
                spots.add(
                    ParkingSlot(
                        id = "${parkingId}_${floorNumber}_${spotNumber}",
                        spotNumber = spotNumber,
                        floorNumber = floorNumber,
                        isAvailable = !isBooked,
                        isBooked = isBooked,
                        vehicleType = vehicleType,
                        row = row,
                        column = col
                    )
                )
            }
        }
        
        return spots
    }
    
    // Check if a spot is available for the given time range
    suspend fun isSpotAvailable(
        parkingId: String,
        spotId: String,
        checkInTime: Long,
        checkOutTime: Long
    ): Boolean {
        return try {
            val conflictingBookings = bookingsCollection
                .whereEqualTo("parkingId", parkingId)
                .whereEqualTo("spotNumber", spotId)
                .whereIn("bookingStatus", listOf(
                    BookingStatus.CONFIRMED.name,
                    BookingStatus.ACTIVE.name
                ))
                .get()
                .await()
            
            // Check for time conflicts
            for (doc in conflictingBookings.documents) {
                val booking = doc.toObject(Booking::class.java) ?: continue
                val bookingStart = booking.entryTime.toDate().time
                val bookingEnd = booking.exitTime.toDate().time
                
                // Check if times overlap
                if (checkInTime < bookingEnd && checkOutTime > bookingStart) {
                    return false
                }
            }
            true
        } catch (e: Exception) {
            true // Assume available on error
        }
    }
    
    // Create a new booking
    suspend fun createBooking(booking: Booking): Result<Booking> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val bookingId = "PK${System.currentTimeMillis().toString().takeLast(6)}"
            val newBooking = booking.copy(
                id = bookingId,
                userId = userId,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            
            // Use transaction to create booking and update availability atomically
            firestore.runTransaction { transaction ->
                // Create the booking
                val bookingRef = bookingsCollection.document(bookingId)
                transaction.set(bookingRef, newBooking)
                
                // Update availability in parking_availability collection
                val availRef = firestore.collection("parking_availability").document(booking.parkingId)
                val availSnapshot = transaction.get(availRef)
                
                if (availSnapshot.exists()) {
                    val updates = mutableMapOf<String, Any>()
                    
                    // Decrease availability based on vehicle type
                    when (booking.vehicleType) {
                        VehicleType.TWO_WHEELER -> {
                            val current = availSnapshot.getLong("availableSpotsTwoWheeler") ?: 0
                            if (current > 0) {
                                updates["availableSpotsTwoWheeler"] = current - 1
                            }
                        }
                        VehicleType.FOUR_WHEELER -> {
                            val current = availSnapshot.getLong("availableSpotsFourWheeler") ?: 0
                            if (current > 0) {
                                updates["availableSpotsFourWheeler"] = current - 1
                            }
                        }
                        VehicleType.HEAVY -> {
                            val current = availSnapshot.getLong("availableSpotsHeavy") ?: 0
                            if (current > 0) {
                                updates["availableSpotsHeavy"] = current - 1
                            }
                        }
                    }
                    
                    // Update floor availability if floor is specified
                    if (booking.floorNumber > 0) {
                        @Suppress("UNCHECKED_CAST")
                        val floorAvail = (availSnapshot.get("floorAvailability") as? Map<String, Long>)?.toMutableMap()
                            ?: mutableMapOf()
                        val floorKey = booking.floorNumber.toString()
                        val currentFloor = floorAvail[floorKey] ?: 0
                        if (currentFloor > 0) {
                            floorAvail[floorKey] = currentFloor - 1
                            updates["floorAvailability"] = floorAvail
                        }
                    }
                    
                    updates["lastUpdated"] = Timestamp.now()
                    transaction.update(availRef, updates)
                }
            }.await()
            
            Result.success(newBooking)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get user's ongoing bookings
    suspend fun getOngoingBookings(): List<Booking> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = bookingsCollection
                .whereEqualTo("userId", userId)
                .whereIn("bookingStatus", listOf(
                    BookingStatus.CONFIRMED.name,
                    BookingStatus.ACTIVE.name,
                    BookingStatus.PENDING.name
                ))
                .orderBy("entryTime", Query.Direction.ASCENDING)
                .get()
                .await()
            snapshot.toObjects(Booking::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Get user's completed bookings
    suspend fun getCompletedBookings(): List<Booking> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = bookingsCollection
                .whereEqualTo("userId", userId)
                .whereIn("bookingStatus", listOf(
                    BookingStatus.COMPLETED.name,
                    BookingStatus.CANCELLED.name
                ))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.toObjects(Booking::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Generate mock floor data
    private fun generateMockFloors(vehicleType: VehicleType): List<FloorData> {
        val floors = mutableListOf<FloorData>()
        
        for (floorNum in 1..3) {
            val spots = mutableListOf<ParkingSlot>()
            val spotsPerRow = 2
            val totalRows = 6
            
            for (row in 0 until totalRows) {
                for (col in 0 until spotsPerRow) {
                    val spotNumber = "${('A' + floorNum - 1)}${String.format("%02d", row * 2 + col + 1)}"
                    val isOccupied = (row * 2 + col) % 3 == 0 // Every 3rd spot is occupied
                    
                    spots.add(
                        ParkingSlot(
                            id = "${floorNum}_${spotNumber}",
                            spotNumber = spotNumber,
                            floorNumber = floorNum,
                            isAvailable = !isOccupied,
                            isBooked = isOccupied,
                            vehicleType = vehicleType,
                            row = row,
                            column = col
                        )
                    )
                }
            }
            
            val availableCount = spots.count { it.isAvailable }
            floors.add(
                FloorData(
                    floorNumber = floorNum,
                    name = "${floorNum}${getOrdinalSuffix(floorNum)} Floor",
                    spots = spots,
                    totalSpots = spots.size,
                    availableSpots = availableCount
                )
            )
        }
        
        return floors
    }
    
    private fun getOrdinalSuffix(n: Int): String {
        return when {
            n in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }
}
