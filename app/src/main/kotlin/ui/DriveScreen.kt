@file:Suppress("FunctionName")

package io.github.joshkeilder.musicsync.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DriveScreen(modifier: Modifier = Modifier, viewModel: DriveViewModel = viewModel()) {
    val state = viewModel.driveState

    val consentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            viewModel.onConsentResult(result.data)
        }

    LaunchedEffect(state) {
        if (state is DriveState.NeedsConsent) {
            consentLauncher.launch(IntentSenderRequest.Builder(state.intent).build())
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            DriveState.SignedOut -> Button(onClick = { viewModel.signIn() }) { Text("Sign in with Google") }
            DriveState.Working, is DriveState.NeedsConsent -> CircularProgressIndicator()
            is DriveState.SignedIn -> Text("Signed in to Google Drive")
            is DriveState.Error -> {
                Text(
                    "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { viewModel.signIn() }) { Text("Try Again") }
            }
        }
    }
}
