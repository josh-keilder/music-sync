package io.github.joshkeilder.musicsync.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SONGS_FOLDER = "Music/Songs/"

data class DeviceSong(
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
)

class MusicFolder(private val context: Context) {
    suspend fun save(fileName: String, input: InputStream): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, deviceSafeFileName(fileName))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, SONGS_FOLDER)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("Couldn't create $fileName")

        try {
            resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                ?: error("Couldn't open $fileName for writing")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        uri
    }

    suspend fun exists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(deviceSafeFileName(fileName), SONGS_FOLDER)

        context.contentResolver.query(collection, projection, selection, selectionArgs, null)
            ?.use { cursor -> cursor.count > 0 } ?: false
    }

    suspend fun listSongs(): List<DeviceSong> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<DeviceSong>()

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection =
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.SIZE)
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(SONGS_FOLDER)

        context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                songs += DeviceSong(
                    name = cursor.getString(nameColumn),
                    uri = ContentUris.withAppendedId(collection, id),
                    sizeBytes = cursor.getLong(sizeColumn),
                )
            }
        }

        songs
    }

    fun openSong(deviceSong: DeviceSong): InputStream =
        context.contentResolver.openInputStream(deviceSong.uri) ?: error("Couldn't open ${deviceSong.name}")

    fun deviceSafeFileName(name: String): String = name.replace(Regex("""["*/:<>?\\|]"""), "_")
}
