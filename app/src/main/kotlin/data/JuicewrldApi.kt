package io.github.joshkeilder.musicsync.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

interface JuicewrldApi {
    @GET("radio/random/")
    suspend fun randomSong(): RadioSong

    @Streaming
    @GET("files/download/")
    suspend fun downloadSong(@Query("path") path: String): ResponseBody

    companion object {
        private const val BASE_URL = "https://juicewrldapi.com/juicewrld/"

        fun create(): JuicewrldApi {
            val json = Json { ignoreUnknownKeys = true }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(JuicewrldApi::class.java)
        }
    }
}
