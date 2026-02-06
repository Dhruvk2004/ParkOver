package com.example.parkover.data.api

import retrofit2.Response
import retrofit2.http.GET

interface ParkOverApiService {
    
    @GET("Dhruvk2004/parkover-api/main/vehicles.json")
    suspend fun getVehicles(): Response<VehiclesResponse>
    
    @GET("Dhruvk2004/parkover-api/main/parking-spots.json")
    suspend fun getParkingSpots(): Response<ParkingSpotsResponse>
}
