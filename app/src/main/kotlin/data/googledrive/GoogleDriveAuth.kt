package io.github.joshkeilder.musicsync.data.googledrive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

private const val GOOGLE_DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"

class GoogleDriveAuth(context: Context) {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(GOOGLE_DRIVE_SCOPE)))
        .build()

    suspend fun authorize(): AuthorizationResult {
        return client.authorize(request).await()
    }

    fun resultFromIntent(data: Intent?): AuthorizationResult {
        return client.getAuthorizationResultFromIntent(data)
    }
}
