package io.github.joshkeilder.musicsync.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import io.github.joshkeilder.musicsync.data.DriveAuth
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface DriveState {
    data object SignedOut : DriveState
    data object Working : DriveState
    data class NeedsConsent(val intent: PendingIntent) : DriveState
    data class SignedIn(val accessToken: String) : DriveState
    data class Error(val message: String) : DriveState
}

class DriveViewModel(application: Application) : AndroidViewModel(application) {
    private val driveAuth = DriveAuth(application)

    var driveState: DriveState by mutableStateOf(DriveState.SignedOut)
        private set

    private fun handleResult(result: AuthorizationResult) {
        driveState = if (result.hasResolution()) {
            DriveState.NeedsConsent(result.pendingIntent ?: error("No consent screen returned"))
        } else {
            DriveState.SignedIn(result.accessToken ?: error("No access token returned"))
        }
    }

    fun signIn() {
        driveState = DriveState.Working

        viewModelScope.launch {
            try {
                val result = driveAuth.authorize()
                handleResult(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                driveState = DriveState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        try {
            handleResult(driveAuth.resultFromIntent(data))
        } catch (e: Exception) {
            driveState = DriveState.Error(e.message ?: "Sign-in cancelled")
        }
    }
}
