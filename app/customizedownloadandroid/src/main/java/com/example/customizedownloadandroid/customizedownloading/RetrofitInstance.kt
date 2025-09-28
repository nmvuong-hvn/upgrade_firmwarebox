package com.example.customizedownloadandroid.customizedownloading

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.HeaderMap
import retrofit2.http.Headers
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

object RetrofitInstance {
    private val okHttpLogging = HttpLoggingInterceptor()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS)
        .addInterceptor(okHttpLogging.setLevel(HttpLoggingInterceptor.Level.HEADERS))
        .build()

    val instanceService = Retrofit.Builder()
        .baseUrl("http:localhost/")
        .client(okHttpClient)
        .build().create(DownloadingService::class.java)

}

interface DownloadingService {

    @Streaming
    @GET
    suspend fun getUrl(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>?

    @HEAD
    suspend fun getHearsOnly(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<Unit>
}