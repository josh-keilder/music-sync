package io.github.joshkeilder.musicsync.data.googledrive

import io.github.joshkeilder.musicsync.data.storage.DeviceSong
import java.io.InputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.source

class GoogleDriveRepository {
    private val googleDriveApi = GoogleDriveApi.create()

    var cachedGoogleDriveSongs: List<GoogleDriveFile>? = null
        private set

    suspend fun findSongsFolderId(accessToken: String): String? {
        val result = googleDriveApi.listFiles(
            authorization = "Bearer $accessToken",
            query = "name = 'Songs' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
        )

        return result.files.firstOrNull()?.id
    }

    suspend fun listSongs(accessToken: String, folderId: String): List<GoogleDriveFile> {
        val songs = mutableListOf<GoogleDriveFile>()
        var pageToken: String? = null

        do {
            val page = googleDriveApi.listFiles(
                authorization = "Bearer $accessToken",
                query = "'$folderId' in parents and trashed = false and mimeType != 'application/vnd.google-apps.folder'",
                pageToken = pageToken,
            )
            songs += page.files
            pageToken = page.nextPageToken
        } while (pageToken != null)

        cachedGoogleDriveSongs = songs
        return songs
    }

    suspend fun downloadSong(accessToken: String, song: GoogleDriveFile): ResponseBody =
        googleDriveApi.downloadFile(
            authorization = "Bearer $accessToken",
            fileId = song.id,
        )

    suspend fun uploadSong(
        accessToken: String,
        folderId: String,
        deviceSong: DeviceSong,
        openSong: () -> InputStream,
    ): GoogleDriveFile {
        val metadata = GoogleDriveUploadMetadata(name = deviceSong.name, parents = listOf(folderId))
        val metadataJson = Json.encodeToString(metadata)
        val metadataBody = metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())

        val songBody = object : RequestBody() {
            override fun contentType() = "audio/mpeg".toMediaType()
            override fun contentLength() = deviceSong.sizeBytes
            override fun writeTo(sink: BufferedSink) {
                openSong().source().use { source -> sink.writeAll(source) }
            }
        }

        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataBody)
            .addPart(songBody)
            .build()

        return googleDriveApi.uploadFile(
            authorization = "Bearer $accessToken",
            body = body,
        )
    }
}
