package com.example.parkover.data.model

data class SearchResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: String? = null
)
