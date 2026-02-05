package com.example.parkover.data.model

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val profilePhotoUrl: String? = null,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val savedParkingIds: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
) {
    constructor() : this(uid = "")
}
