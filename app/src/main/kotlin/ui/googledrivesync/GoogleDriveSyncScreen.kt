@file:Suppress("FunctionName")

package io.github.joshkeilder.musicsync.ui.googledrivesync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.joshkeilder.musicsync.data.googledrive.GoogleDriveFile

@Composable
fun GoogleDriveSyncScreen(modifier: Modifier = Modifier, viewModel: GoogleDriveSyncViewModel = viewModel()) {
    val state = viewModel.googleDriveSyncState

    val consentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            viewModel.onConsentResult(result.data)
        }

    LaunchedEffect(state) {
        if (state is GoogleDriveSyncState.NeedsConsent) {
            consentLauncher.launch(IntentSenderRequest.Builder(state.intent).build())
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            GoogleDriveSyncState.SignedOut -> Button(onClick = { viewModel.signIn() }) { Text("Sign in with Google") }
            GoogleDriveSyncState.Working, is GoogleDriveSyncState.NeedsConsent -> CircularProgressIndicator()
            GoogleDriveSyncState.FolderNotFound -> Text("No Songs folder found")
            is GoogleDriveSyncState.Loaded -> GoogleDriveSongList(
                state = state,
                googleDriveDownloadState = viewModel.googleDriveDownloadState,
                onDownloadMissingSongsClick = { viewModel.downloadMissingSongs() },
                onRefreshClick = { viewModel.refreshGoogleDriveSongs() },
            )
            is GoogleDriveSyncState.Error -> {
                Text(
                    "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { viewModel.signIn() }) { Text("Try Again") }
            }
        }
    }
}

@Composable
fun GoogleDriveSongList(
    state: GoogleDriveSyncState.Loaded,
    googleDriveDownloadState: GoogleDriveDownloadState,
    onDownloadMissingSongsClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${state.songs.size} songs on Google Drive", style = MaterialTheme.typography.titleLarge)
                Text("${state.missingCount} not on your device", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onRefreshClick, enabled = !state.isRefreshing) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        }

        if (state.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when (googleDriveDownloadState) {
            GoogleDriveDownloadState.Idle ->
                if (state.missingCount > 0) {
                    Button(onClick = onDownloadMissingSongsClick) {
                        Text("Download ${state.missingCount} missing songs")
                    }
                }

            is GoogleDriveDownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = { googleDriveDownloadState.current.toFloat() / googleDriveDownloadState.total },
                    modifier = Modifier.fillMaxWidth(),
                )

                val songTitle = googleDriveDownloadState.songName.removeSuffix(".mp3")
                Text("Downloading ${googleDriveDownloadState.current} of ${googleDriveDownloadState.total}: $songTitle")
            }

            is GoogleDriveDownloadState.Finished -> {
                val failedText = if (googleDriveDownloadState.failedCount > 0) {
                    ", ${googleDriveDownloadState.failedCount} failed"
                } else {
                    ""
                }
                Text("Downloaded ${googleDriveDownloadState.downloadedCount} songs$failedText")

                if (state.missingCount > 0) {
                    Button(onClick = onDownloadMissingSongsClick) { Text("Retry ${state.missingCount} missing songs") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.songs, key = { it.id }) { song ->
                GoogleDriveSongRow(song = song, isOnDevice = song.name !in state.missingSongNames)
            }
        }
    }
}

@Composable
fun GoogleDriveSongRow(song: GoogleDriveFile, isOnDevice: Boolean) {
    val sizeText = song.sizeBytes?.let { bytes ->
        val sizeMb = bytes / (1024.0 * 1024.0)
        " · %.1f MB".format(sizeMb)
    } ?: ""
    ListItem(
        headlineContent = { Text(song.name.removeSuffix(".mp3")) },
        supportingContent = { Text(if (isOnDevice) "On your device$sizeText" else "Not on your device$sizeText") },
        leadingContent = {
            Icon(
                if (isOnDevice) Icons.Outlined.CheckCircle else Icons.Outlined.CloudDownload,
                contentDescription = null,
            )
        },
    )
}
