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
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface GoogleDriveSyncState {
    data object SignedOut : GoogleDriveSyncState
    data object Working : GoogleDriveSyncState
    data object FolderNotFound : GoogleDriveSyncState
    data class NeedsConsent(val intent: PendingIntent) : GoogleDriveSyncState
    data class Loaded(
        val songs: List<GoogleDriveFile>,
        val missingSongNames: Set<String>,
        val isRefreshing: Boolean = false,
    ) : GoogleDriveSyncState {
        val missingCount: Int
            get() = missingSongNames.size
    }

    data class Error(val message: String) : GoogleDriveSyncState
}

sealed interface GoogleDriveDownloadState {
    data object Idle : GoogleDriveDownloadState
    data class Downloading(val current: Int, val total: Int, val songName: String) : GoogleDriveDownloadState
    data class Finished(val downloadedCount: Int, val failedCount: Int) : GoogleDriveDownloadState
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
        songs: List<GoogleDriveFile>,
        isRefreshing: Boolean,
    ): GoogleDriveSyncState.Loaded {
        val sortedSongs = songs.sortedBy { it.name.lowercase() }
        val missingSongNames = sortedSongs.filter { !musicFolder.exists(it.name) }.map { it.name }.toSet()

        return GoogleDriveSyncState.Loaded(sortedSongs, missingSongNames, isRefreshing)
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

        val songsToDownload = loaded.songs.filter { it.name in loaded.missingSongNames }

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
                        googleDriveRepository.downloadSong(accessToken, song).use {
                            musicFolder.save(song.name, it.byteStream())
                        }

                        val current = googleDriveSyncState as? GoogleDriveSyncState.Loaded

                        if (current != null) {
                            googleDriveSyncState = current.copy(missingSongNames = current.missingSongNames - song.name)
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

    private suspend fun freshGoogleDriveAccessToken(): String {
        val result = googleDriveAuth.authorize()

        return result.accessToken ?: error("Google Drive sign-in expired. Please sign in again.")
    }
}
