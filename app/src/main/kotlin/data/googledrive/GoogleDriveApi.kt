package io.github.joshkeilder.musicsync.data.googledrive

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GoogleDriveApi {
    @GET("files")
    suspend fun listFiles(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "nextPageToken,files(id,name,size,mimeType)",
        @Query("pageSize") pageSize: Int = 1000,
        @Query("pageToken") pageToken: String? = null,
    ): GoogleDriveFileList

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/drive/v3/"

        fun create(): GoogleDriveApi {
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
                .create(GoogleDriveApi::class.java)
        }
    }
}
