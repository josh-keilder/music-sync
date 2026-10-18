package io.github.joshkeilder.musicsync.ui.googledrivesync

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import io.github.joshkeilder.musicsync.MusicSyncApplication
import io.github.joshkeilder.musicsync.data.googledrive.GoogleDriveFile
import io.github.joshkeilder.musicsync.data.storage.DeviceSong
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class GoogleDriveSyncSong(
    val name: String,
    val googleDriveFile: GoogleDriveFile?,
    val deviceSong: DeviceSong?,
) {
    val isOnGoogleDrive: Boolean
        get() = googleDriveFile != null
    val isOnDevice: Boolean
        get() = deviceSong != null
}

sealed interface GoogleDriveSyncState {
    data object SignedOut : GoogleDriveSyncState
    data object Working : GoogleDriveSyncState
    data object FolderNotFound : GoogleDriveSyncState
    data class NeedsConsent(val intent: PendingIntent) : GoogleDriveSyncState
    data class Loaded(
        val syncSongs: List<GoogleDriveSyncSong>,
        val isRefreshing: Boolean = false,
    ) : GoogleDriveSyncState {
        val notOnDeviceSongs: List<GoogleDriveSyncSong>
            get() = syncSongs.filter { !it.isOnDevice }
        val notOnGoogleDriveSongs: List<GoogleDriveSyncSong>
            get() = syncSongs.filter { !it.isOnGoogleDrive }
    }

    data class Error(val message: String) : GoogleDriveSyncState
}

sealed interface GoogleDriveDownloadState {
    data object Idle : GoogleDriveDownloadState
    data class Downloading(val current: Int, val total: Int, val songName: String) : GoogleDriveDownloadState
    data class Finished(val downloadedCount: Int, val failedCount: Int) : GoogleDriveDownloadState
}

sealed interface GoogleDriveUploadState {
    data object Idle : GoogleDriveUploadState
    data class Uploading(val current: Int, val total: Int, val songName: String) : GoogleDriveUploadState
    data class Finished(val uploadedCount: Int, val failedCount: Int) : GoogleDriveUploadState
}

class GoogleDriveSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val musicSyncAppContainer = (application as MusicSyncApplication).musicSyncAppContainer
    private val googleDriveAuth = musicSyncAppContainer.googleDriveAuth
    private val googleDriveRepository = musicSyncAppContainer.googleDriveRepository
    private val musicFolder = musicSyncAppContainer.musicFolder

    var googleDriveSyncState: GoogleDriveSyncState by mutableStateOf(GoogleDriveSyncState.SignedOut)
        private set

    var googleDriveDownloadState: GoogleDriveDownloadState by mutableStateOf(GoogleDriveDownloadState.Idle)
        private set

    var googleDriveUploadState: GoogleDriveUploadState by mutableStateOf(GoogleDriveUploadState.Idle)
        private set

    init {
        showCachedGoogleDriveSongs()
        signIn(interactive = false)
    }

    fun signIn(interactive: Boolean = true) {
        showWorking()

        viewModelScope.launch {
            try {
                val result = googleDriveAuth.authorize()
                handleResult(result, interactive)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                googleDriveSyncState = GoogleDriveSyncState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun refreshGoogleDriveSongs() {
        signIn()
    }

    fun onConsentResult(data: Intent?) {
        try {
            handleResult(googleDriveAuth.resultFromIntent(data), interactive = true)
        } catch (e: Exception) {
            googleDriveSyncState = GoogleDriveSyncState.Error(e.message ?: "Sign-in cancelled")
        }
    }

    private fun handleResult(result: AuthorizationResult, interactive: Boolean) {
        if (result.hasResolution()) {
            googleDriveSyncState = if (interactive) {
                GoogleDriveSyncState.NeedsConsent(result.pendingIntent ?: error("No consent screen returned"))
            } else {
                GoogleDriveSyncState.SignedOut
            }
        } else {
            loadSongs(result.accessToken ?: error("No access token returned"))
        }
    }

    private fun showCachedGoogleDriveSongs() {
        val cachedSongs = googleDriveRepository.cachedGoogleDriveSongs ?: return

        viewModelScope.launch {
            googleDriveSyncState = buildLoadedState(cachedSongs, isRefreshing = true)
        }
    }

    private fun loadSongs(accessToken: String) {
        showWorking()

        viewModelScope.launch {
            try {
                val folderId = googleDriveRepository.findSongsFolderId(accessToken)

                if (folderId == null) {
                    googleDriveSyncState = GoogleDriveSyncState.FolderNotFound
                    return@launch
                }

                val songs = googleDriveRepository.listSongs(accessToken, folderId)
                googleDriveSyncState = buildLoadedState(songs, isRefreshing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                googleDriveSyncState = GoogleDriveSyncState.Error(e.message ?: "Couldn't load your Google Drive songs")
            }
        }
    }

    private suspend fun buildLoadedState(
        googleDriveSongs: List<GoogleDriveFile>,
        isRefreshing: Boolean,
    ): GoogleDriveSyncState.Loaded {
        val deviceSongs = musicFolder.listSongs()
        val deviceSongsByName = deviceSongs.associateBy { it.name }
        val matchedDeviceNames = mutableSetOf<String>()

        val songsFromGoogleDrive = googleDriveSongs.map { googleDriveFile ->
            val deviceName = musicFolder.deviceSafeFileName(googleDriveFile.name)
            val deviceSong = deviceSongsByName[deviceName]
            if (deviceSong != null) {
                matchedDeviceNames += deviceName
            }
            GoogleDriveSyncSong(name = googleDriveFile.name, googleDriveFile = googleDriveFile, deviceSong = deviceSong)
        }

        val songsOnlyOnDevice = deviceSongs
            .filter { it.name !in matchedDeviceNames }
            .map { GoogleDriveSyncSong(name = it.name, googleDriveFile = null, deviceSong = it) }

        val syncSongs = (songsFromGoogleDrive + songsOnlyOnDevice).sortedBy { it.name.lowercase() }

        return GoogleDriveSyncState.Loaded(syncSongs, isRefreshing)
    }

    private fun showWorking() {
        val current = googleDriveSyncState

        googleDriveSyncState = if (current is GoogleDriveSyncState.Loaded) {
            current.copy(isRefreshing = true)
        } else {
            GoogleDriveSyncState.Working
        }
    }

    fun downloadMissingSongs() {
        val loaded = googleDriveSyncState as? GoogleDriveSyncState.Loaded ?: return

        val songsToDownload = loaded.notOnDeviceSongs.mapNotNull { it.googleDriveFile }

        if (songsToDownload.isEmpty()) {
            return
        }

        viewModelScope.launch {
            try {
                val accessToken = freshGoogleDriveAccessToken()
                var failedCount = 0

                songsToDownload.forEachIndexed { index, song ->
                    googleDriveDownloadState =
                        GoogleDriveDownloadState.Downloading(index + 1, songsToDownload.size, song.name)

                    try {
                        val savedUri = googleDriveRepository.downloadSong(accessToken, song).use {
                            musicFolder.save(song.name, it.byteStream())
                        }
                        val downloadedDeviceSong = DeviceSong(
                            name = musicFolder.deviceSafeFileName(song.name),
                            uri = savedUri,
                            sizeBytes = song.sizeBytes ?: 0,
                        )

                        val current = googleDriveSyncState as? GoogleDriveSyncState.Loaded

                        if (current != null) {
                            googleDriveSyncState = current.copy(
                                syncSongs = current.syncSongs.map { syncSong ->
                                    if (syncSong.googleDriveFile?.id == song.id) {
                                        syncSong.copy(deviceSong = downloadedDeviceSong)
                                    } else {
                                        syncSong
                                    }
                                },
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedCount++
                    }
                }

                googleDriveDownloadState =
                    GoogleDriveDownloadState.Finished(songsToDownload.size - failedCount, failedCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                googleDriveSyncState = GoogleDriveSyncState.Error(e.message ?: "Couldn't download your songs")
            }
        }
    }

    fun uploadMissingSongs() {
        val loaded = googleDriveSyncState as? GoogleDriveSyncState.Loaded ?: return

        val songsToUpload = loaded.notOnGoogleDriveSongs.mapNotNull { it.deviceSong }

        if (songsToUpload.isEmpty()) {
            return
        }

        viewModelScope.launch {
            try {
                val accessToken = freshGoogleDriveAccessToken()
                val folderId = googleDriveRepository.findSongsFolderId(accessToken)
                    ?: error("Couldn't find your Songs folder on Google Drive")

                var failedCount = 0

                songsToUpload.forEachIndexed { index, deviceSong ->
                    googleDriveUploadState =
                        GoogleDriveUploadState.Uploading(index + 1, songsToUpload.size, deviceSong.name)

                    try {
                        val uploadedGoogleDriveFile =
                            googleDriveRepository.uploadSong(accessToken, folderId, deviceSong) {
                                musicFolder.openSong(deviceSong)
                            }

                        val current = googleDriveSyncState as? GoogleDriveSyncState.Loaded

                        if (current != null) {
                            googleDriveSyncState = current.copy(
                                syncSongs = current.syncSongs.map { syncSong ->
                                    if (syncSong.deviceSong?.uri == deviceSong.uri) {
                                        syncSong.copy(googleDriveFile = uploadedGoogleDriveFile)
                                    } else {
                                        syncSong
                                    }
                                },
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedCount++
                    }
                }

                googleDriveUploadState =
                    GoogleDriveUploadState.Finished(songsToUpload.size - failedCount, failedCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                googleDriveSyncState = GoogleDriveSyncState.Error(e.message ?: "Couldn't upload your songs")
            }
        }
    }

    fun clearFinishedTransferMessages() {
        if (googleDriveDownloadState is GoogleDriveDownloadState.Finished) {
            googleDriveDownloadState = GoogleDriveDownloadState.Idle
        }
        if (googleDriveUploadState is GoogleDriveUploadState.Finished) {
            googleDriveUploadState = GoogleDriveUploadState.Idle
        }
    }

    private suspend fun freshGoogleDriveAccessToken(): String {
        val result = googleDriveAuth.authorize()

        return result.accessToken ?: error("Google Drive sign-in expired. Please sign in again.")
    }
}
