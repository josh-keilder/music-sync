package io.github.joshkeilder.musicsync.data.googledrive

import androidx.compose.ui.derivedMediaQuery

class GoogleDriveRepository {
    private val googleDriveApi = GoogleDriveApi.create()

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

        return songs
    }
}
