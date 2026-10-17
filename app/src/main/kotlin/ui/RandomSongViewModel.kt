package io.github.joshkeilder.musicsync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.joshkeilder.musicsync.data.JuicewrldApi
import io.github.joshkeilder.musicsync.data.MusicFolder
import io.github.joshkeilder.musicsync.data.RadioSong
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface RandomSongState {
    data object Idle : RandomSongState
    data object Loading : RandomSongState
    data class Success(val song: RadioSong) : RandomSongState
    data class Error(val message: String) : RandomSongState
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Downloading : DownloadState
    data object Saved : DownloadState
    data class Failed(val message: String) : DownloadState
    data object AlreadyHave : DownloadState
}

class RandomSongViewModel(application: Application) : AndroidViewModel(application) {
    private val api = JuicewrldApi.create()

    private val musicFolder = MusicFolder(application)

    var state: RandomSongState by mutableStateOf(RandomSongState.Idle)
        private set

    var downloadState: DownloadState by mutableStateOf(DownloadState.Idle)
        private set

    fun loadRandomSong() {
        downloadState = DownloadState.Idle
        state = RandomSongState.Loading

        viewModelScope.launch {
            try {
                val song = api.randomSong()
                downloadState = if (musicFolder.exists(song.fileName))
                    DownloadState.AlreadyHave else DownloadState.Idle
                state = RandomSongState.Success(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = RandomSongState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun downloadCurrentSong() {
        val song = (state as? RandomSongState.Success)?.song ?: return

        val fileName = song.fileName

        downloadState = DownloadState.Downloading

        viewModelScope.launch {
            try {
                val body = api.downloadSong(song.path)
                body.use { musicFolder.save(fileName, it.byteStream()) }
                downloadState = DownloadState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                downloadState = DownloadState.Failed(e.message ?: "Download Failed")
            }
        }
    }
}
