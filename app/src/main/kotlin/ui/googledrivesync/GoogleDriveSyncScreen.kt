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
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
                googleDriveUploadState = viewModel.googleDriveUploadState,
                onDownloadMissingSongsClick = { viewModel.downloadMissingSongs() },
                onUploadMissingSongsClick = { viewModel.uploadMissingSongs() },
                onSyncTabSelected = { viewModel.clearFinishedTransferMessages() },
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

enum class GoogleDriveSyncTab(val label: String) {
    AllSongs("All Songs"),
    NotOnDevice("Not on Device"),
    NotOnGoogleDrive("Not on Google Drive"),
}

@Composable
fun GoogleDriveSongList(
    state: GoogleDriveSyncState.Loaded,
    googleDriveDownloadState: GoogleDriveDownloadState,
    googleDriveUploadState: GoogleDriveUploadState,
    onDownloadMissingSongsClick: () -> Unit,
    onUploadMissingSongsClick: () -> Unit,
    onSyncTabSelected: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    var selectedGoogleDriveSyncTab by rememberSaveable { mutableStateOf(GoogleDriveSyncTab.AllSongs) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedGoogleDriveSyncTab.ordinal) {
            GoogleDriveSyncTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedGoogleDriveSyncTab,
                    onClick = {
                        selectedGoogleDriveSyncTab = tab
                        onSyncTabSelected()
                    },
                    text = { Text(tab.label) },
                )
            }
        }

        val songsForSelectedTab = when (selectedGoogleDriveSyncTab) {
            GoogleDriveSyncTab.AllSongs -> state.syncSongs
            GoogleDriveSyncTab.NotOnDevice -> state.notOnDeviceSongs
            GoogleDriveSyncTab.NotOnGoogleDrive -> state.notOnGoogleDriveSongs
        }

        val summaryHeading = songCountText(songsForSelectedTab.size)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summaryHeading, style = MaterialTheme.typography.titleLarge)
                if (selectedGoogleDriveSyncTab == GoogleDriveSyncTab.AllSongs) {
                    Text(
                        "${state.notOnDeviceSongs.size} not on your device · ${state.notOnGoogleDriveSongs.size} not on Google Drive",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            IconButton(onClick = onRefreshClick, enabled = !state.isRefreshing) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        }

        if (state.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (selectedGoogleDriveSyncTab == GoogleDriveSyncTab.NotOnDevice) {
            when (googleDriveDownloadState) {
                GoogleDriveDownloadState.Idle ->
                    if (state.notOnDeviceSongs.isNotEmpty()) {
                        Button(onClick = onDownloadMissingSongsClick) {
                            Text("Download ${songCountText(state.notOnDeviceSongs.size)}")
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
                    Text("Downloaded ${songCountText(googleDriveDownloadState.downloadedCount)}$failedText")

                    if (state.notOnDeviceSongs.isNotEmpty()) {
                        Button(onClick = onDownloadMissingSongsClick) {
                            Text("Retry ${songCountText(state.notOnDeviceSongs.size)}")
                        }
                    }
                }
            }
        }

        if (selectedGoogleDriveSyncTab == GoogleDriveSyncTab.NotOnGoogleDrive) {
            when (googleDriveUploadState) {
                GoogleDriveUploadState.Idle ->
                    if (state.notOnGoogleDriveSongs.isNotEmpty()) {
                        Button(onClick = onUploadMissingSongsClick) {
                            Text("Upload ${songCountText(state.notOnGoogleDriveSongs.size)}")
                        }
                    }

                is GoogleDriveUploadState.Uploading -> {
                    LinearProgressIndicator(
                        progress = { googleDriveUploadState.current.toFloat() / googleDriveUploadState.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val songTitle = googleDriveUploadState.songName.removeSuffix(".mp3")
                    Text("Uploading ${googleDriveUploadState.current} of ${googleDriveUploadState.total}: $songTitle")
                }

                is GoogleDriveUploadState.Finished -> {
                    val failedText = if (googleDriveUploadState.failedCount > 0) {
                        ", ${googleDriveUploadState.failedCount} failed"
                    } else {
                        ""
                    }
                    Text("Uploaded ${songCountText(googleDriveUploadState.uploadedCount)}$failedText")

                    if (state.notOnGoogleDriveSongs.isNotEmpty()) {
                        Button(onClick = onUploadMissingSongsClick) {
                            Text("Retry ${songCountText(state.notOnGoogleDriveSongs.size)}")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (songsForSelectedTab.isEmpty()) {
            val emptyTabMessage = when (selectedGoogleDriveSyncTab) {
                GoogleDriveSyncTab.AllSongs -> "No songs yet"
                GoogleDriveSyncTab.NotOnDevice -> "Everything on Google Drive is on your device"
                GoogleDriveSyncTab.NotOnGoogleDrive -> "Everything on your device is on Google Drive"
            }
            Text(emptyTabMessage, style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(
                    songsForSelectedTab,
                    key = { it.googleDriveFile?.id ?: it.deviceSong?.uri.toString() },
                ) { syncSong ->
                    GoogleDriveSongRow(syncSong = syncSong)
                }
            }
        }
    }
}

@Composable
fun GoogleDriveSongRow(syncSong: GoogleDriveSyncSong) {
    val sizeBytes = syncSong.googleDriveFile?.sizeBytes ?: syncSong.deviceSong?.sizeBytes

    val sizeText = sizeBytes?.let { bytes ->
        val sizeMb = bytes / (1024.0 * 1024.0)
        " · %.1f MB".format(sizeMb)
    } ?: ""

    val statusText = when {
        syncSong.isOnDevice && syncSong.isOnGoogleDrive -> "On device and Google Drive"
        syncSong.isOnGoogleDrive -> "Not on your device"
        else -> "Not on Google Drive"
    }
    val statusIcon = when {
        syncSong.isOnDevice && syncSong.isOnGoogleDrive -> Icons.Outlined.CheckCircle
        syncSong.isOnGoogleDrive -> Icons.Outlined.CloudDownload
        else -> Icons.Outlined.CloudUpload
    }
    ListItem(
        headlineContent = { Text(syncSong.name.removeSuffix(".mp3")) },
        supportingContent = { Text("$statusText$sizeText") },
        leadingContent = { Icon(statusIcon, contentDescription = null) },
    )
}

private fun songCountText(count: Int): String = if (count == 1) "1 song" else "$count songs"
