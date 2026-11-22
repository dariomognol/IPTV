package com.example.iptv

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

interface XtreamApiService {
    @GET("player_api.php")
    suspend fun authenticateUser(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<LoginResponse>
}

object NetworkUtils {
    fun fetchUrlOptimized(urlString: String, timeoutMs: Int = 12000): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate")

        val encoding = conn.contentEncoding
        val inputStream = if (encoding != null && encoding.equals("gzip", ignoreCase = true)) {
            GZIPInputStream(conn.inputStream)
        } else {
            conn.inputStream
        }

        return inputStream.bufferedReader().use { it.readText() }
    }
}
