package io.github.joshkeilder.musicsync.data.juicewrld

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RadioSong(
    val title: String,
    val path: String,
    val size: Long,
    val song: Song? = null,
) {
    val fileName: String
        get() = path.substringAfterLast("/")
}

@Serializable
data class Song(
    val id: Int,
    val name: String,
    val category: String = "",
    val era: Era? = null,
    @SerialName("credited_artists") val creditedArtists: String = "",
    val producers: String = "",
    val length: String = "",
    @SerialName("image_url") val imageUrl: String = "",
) {
    val fullImageUrl: String?
        get() = when {
            imageUrl.isBlank() -> null
            imageUrl.startsWith("http") -> imageUrl
            else -> "https://juicewrldapi.com$imageUrl"
        }
}

@Serializable
data class Era(
    val id: Int,
    val name: String,
    val description: String = "",
    @SerialName("time_frame") val timeFrame: String = "",
)
