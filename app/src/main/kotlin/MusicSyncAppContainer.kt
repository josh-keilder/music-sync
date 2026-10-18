package io.github.joshkeilder.musicsync

import android.content.Context
import io.github.joshkeilder.musicsync.data.googledrive.GoogleDriveAuth
import io.github.joshkeilder.musicsync.data.googledrive.GoogleDriveRepository
import io.github.joshkeilder.musicsync.data.juicewrld.JuicewrldApi
import io.github.joshkeilder.musicsync.data.storage.MusicFolder

class MusicSyncAppContainer(context: Context) {
    val juicewrldApi = JuicewrldApi.create()
    val musicFolder = MusicFolder(context)
    val googleDriveAuth = GoogleDriveAuth(context)
    val googleDriveRepository = GoogleDriveRepository()
}
