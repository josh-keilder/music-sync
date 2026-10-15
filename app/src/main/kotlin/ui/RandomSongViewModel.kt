package com.josh.musicsync.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.josh.musicsync.data.JuicewrldApi
import com.josh.musicsync.data.RadioSong
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException


sealed interface RandomSongState {
    data object Idle : RandomSongState
    data object Loading : RandomSongState
    data class Success(val song: RadioSong) : RandomSongState
    data class Error(val message: String) : RandomSongState
}

class RandomSongViewModel : ViewModel() {
    private val api = JuicewrldApi.create()

    var state: RandomSongState by mutableStateOf(RandomSongState.Idle)
        private set

    fun loadRandomSong() {
        state = RandomSongState.Loading
        viewModelScope.launch {
            state = try {
                RandomSongState.Success(api.randomSong())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RandomSongState.Error(e.message ?: "Something went wrong")
            }
        }
    }
}