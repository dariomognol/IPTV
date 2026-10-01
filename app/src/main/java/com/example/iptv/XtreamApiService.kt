package com.example.iptv

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApiService {
    @GET("player_api.php")
    suspend fun authenticateUser(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<LoginResponse>
}