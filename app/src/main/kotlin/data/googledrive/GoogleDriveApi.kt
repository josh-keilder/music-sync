package io.github.joshkeilder.musicsync.data.googledrive

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface GoogleDriveApi {
    @GET("files")
    suspend fun listFiles(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "nextPageToken,files(id,name,size,mimeType)",
        @Query("pageSize") pageSize: Int = 1000,
        @Query("pageToken") pageToken: String? = null,
    ): GoogleDriveFileList

    @Streaming
    @GET("files/{fileId}")
    suspend fun downloadFile(
        @Header("Authorization") authorization: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media",
    ): ResponseBody

    @POST("https://www.googleapis.com/upload/drive/v3/files")
    suspend fun uploadFile(
        @Header("Authorization") authorization: String,
        @Body body: RequestBody,
        @Query("uploadType") uploadType: String = "multipart",
        @Query("fields") fields: String = "id,name,size,mimeType",
    ): GoogleDriveFile

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
