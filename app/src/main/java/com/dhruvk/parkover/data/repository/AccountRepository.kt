package com.dhruvk.parkover.data.repository

import com.dhruvk.parkover.data.model.ParkingSpot
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class SavedParking(
    val parkingId: String = "",
    val name: String = "",
    val address: String = "",
    val imageUrl: String = "",
    val savedAt: Timestamp? = null
)

data class UpiId(
    val id: String = "",
    val vpa: String = "",
    val isDefault: Boolean = false
)

/** Per-user data under users/{uid}: saved parkings and UPI IDs, plus support tickets. */
class AccountRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun userDoc() = auth.currentUser?.uid?.let { firestore.collection("users").document(it) }

    // ---------- Saved parkings ----------

    fun observeSavedParkings(): Flow<List<SavedParking>> = callbackFlow {
        val doc = userDoc()
        if (doc == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = doc.collection("savedParkings")
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty().map { d ->
                        SavedParking(
                            parkingId = d.getString("parkingId") ?: d.id,
                            name = d.getString("name").orEmpty(),
                            address = d.getString("address").orEmpty(),
                            imageUrl = d.getString("imageUrl").orEmpty(),
                            savedAt = d.getTimestamp("savedAt")
                        )
                    }
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveParking(spot: ParkingSpot): Result<Unit> = runCatching {
        val doc = userDoc() ?: throw Exception("Not logged in")
        doc.collection("savedParkings").document(spot.id).set(
            mapOf(
                "parkingId" to spot.id,
                "name" to spot.name,
                "address" to spot.address,
                "latitude" to spot.latitude,
                "longitude" to spot.longitude,
                "pricePerHour" to spot.pricePerHourFourWheeler,
                "rating" to spot.rating,
                "imageUrl" to (spot.images.firstOrNull() ?: ""),
                "savedAt" to Timestamp.now()
            )
        ).await()
        Unit
    }

    suspend fun removeSavedParking(parkingId: String): Result<Unit> = runCatching {
        val doc = userDoc() ?: throw Exception("Not logged in")
        doc.collection("savedParkings").document(parkingId).delete().await()
        Unit
    }

    // ---------- UPI IDs ----------

    fun observeUpiIds(): Flow<List<UpiId>> = callbackFlow {
        val doc = userDoc()
        if (doc == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = doc.collection("upiIds").addSnapshotListener { snapshot, _ ->
            trySend(
                snapshot?.documents.orEmpty().map { d ->
                    UpiId(id = d.id, vpa = d.getString("vpa").orEmpty(), isDefault = d.getBoolean("isDefault") ?: false)
                }.sortedByDescending { it.isDefault }
            )
        }
        awaitClose { registration.remove() }
    }

    suspend fun addUpiId(vpa: String, makeDefault: Boolean): Result<Unit> = runCatching {
        val doc = userDoc() ?: throw Exception("Not logged in")
        val collection = doc.collection("upiIds")
        if (makeDefault) {
            collection.get().await().documents.forEach { it.reference.update("isDefault", false).await() }
        }
        collection.add(mapOf("vpa" to vpa, "isDefault" to makeDefault, "createdAt" to Timestamp.now())).await()
        Unit
    }

    suspend fun removeUpiId(id: String): Result<Unit> = runCatching {
        val doc = userDoc() ?: throw Exception("Not logged in")
        doc.collection("upiIds").document(id).delete().await()
        Unit
    }

    // ---------- Support ----------

    suspend fun submitSupportTicket(
        category: String,
        subject: String,
        description: String,
        bookingId: String?
    ): Result<String> = runCatching {
        val user = auth.currentUser ?: throw Exception("Not logged in")
        val ref = firestore.collection("support_tickets").add(
            mapOf(
                "userId" to user.uid,
                "email" to (user.email ?: ""),
                "category" to category,
                "subject" to subject,
                "description" to description,
                "bookingId" to bookingId,
                "status" to "OPEN",
                "createdAt" to Timestamp.now()
            )
        ).await()
        ref.id
    }

    suspend fun countSavedParkings(): Int = try {
        userDoc()?.collection("savedParkings")?.get()?.await()?.size() ?: 0
    } catch (e: Exception) {
        0
    }
}
