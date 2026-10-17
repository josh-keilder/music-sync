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
import io.github.joshkeilder.musicsync.data.googledrive.GoogleDriveAuth
import io.github.joshkeilder.musicsync.data.googledrive.GoogleDriveFile
import io.github.joshkeilder.musicsync.data.googledrive.GoogleDriveRepository
import io.github.joshkeilder.musicsync.data.storage.MusicFolder
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface GoogleDriveSyncState {
    data object SignedOut : GoogleDriveSyncState
    data object Working : GoogleDriveSyncState
    data object FolderNotFound : GoogleDriveSyncState
    data class NeedsConsent(val intent: PendingIntent) : GoogleDriveSyncState
    data class Loaded(val songs: List<GoogleDriveFile>, val missingSongNames: Set<String>) : GoogleDriveSyncState {
        val missingCount: Int
            get() = missingSongNames.size
    }

    data class Error(val message: String) : GoogleDriveSyncState
}

class GoogleDriveSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val googleDriveAuth = GoogleDriveAuth(application)
    private val googleDriveRepository = GoogleDriveRepository()
    private val musicFolder = MusicFolder(application)

    var googleDriveSyncState: GoogleDriveSyncState by mutableStateOf(GoogleDriveSyncState.SignedOut)
        private set

    init {
        signIn(interactive = false)
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

    fun signIn(interactive: Boolean = true) {
        googleDriveSyncState = GoogleDriveSyncState.Working

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

    fun onConsentResult(data: Intent?) {
        try {
            handleResult(googleDriveAuth.resultFromIntent(data), true)
        } catch (e: Exception) {
            googleDriveSyncState = GoogleDriveSyncState.Error(e.message ?: "Sign-in cancelled")
        }
    }

    private fun loadSongs(accessToken: String) {
        googleDriveSyncState = GoogleDriveSyncState.Working

        viewModelScope.launch {
            try {
                val folderId = googleDriveRepository.findSongsFolderId(accessToken)

                if (folderId == null) {
                    googleDriveSyncState = GoogleDriveSyncState.FolderNotFound
                    return@launch
                }

                val songs = googleDriveRepository.listSongs(accessToken, folderId)
                val sortedSongs = songs.sortedBy { it.name.lowercase() }
                val missingSongNames = sortedSongs.filter { !musicFolder.exists(it.name) }.map { it.name }.toSet()

                googleDriveSyncState = GoogleDriveSyncState.Loaded(sortedSongs, missingSongNames)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                googleDriveSyncState = GoogleDriveSyncState.Error(e.message ?: "Couldn't load your Google Drive songs")
            }
        }
    }
}
