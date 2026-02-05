package com.example.parkover.data.model

import com.google.firebase.Timestamp

data class Review(
    val id: String = "",
    val userId: String = "",
    val userName: String = "", // Denormalized
    val userPhotoUrl: String? = null,
    val parkingId: String = "",
    val bookingId: String = "",
    val rating: Int = 0, // 1-5
    val comment: String = "",
    val createdAt: Timestamp = Timestamp.now()
) {
    constructor() : this(id = "")
}
