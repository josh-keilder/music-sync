package io.github.joshkeilder.musicsync.data.googledrive

import kotlinx.serialization.Serializable

@Serializable
data class GoogleDriveFile(
    val id: String,
    val name: String,
    val size: String? = null,
    val mimeType: String = "",
) {
    val sizeBytes: Long?
        get() = size?.toLongOrNull()
}

@Serializable
data class GoogleDriveFileList(
    val files: List<GoogleDriveFile> = emptyList(),
    val nextPageToken: String? = null,
)
