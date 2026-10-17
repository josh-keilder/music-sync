@file:Suppress("FunctionName")

package io.github.joshkeilder.musicsync.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.joshkeilder.musicsync.data.RadioSong

@Composable
fun RandomSongScreen(
    modifier: Modifier = Modifier,
    viewModel: RandomSongViewModel = viewModel(),
) {
    val state = viewModel.state

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            RandomSongState.Idle -> Text("Tap the button to get a random song")
            RandomSongState.Loading -> CircularProgressIndicator()
            is RandomSongState.Success -> SongCard(
                state.song,
                viewModel.downloadState,
                onDownloadClick = { viewModel.downloadCurrentSong() })

            is RandomSongState.Error -> Text(
                "Error: ${state.message}",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.loadRandomSong() },
            enabled = state !is RandomSongState.Loading,
        ) {
            Text("Random Song")
        }
    }
}

@Composable
fun SongCard(radioSong: RadioSong, downloadState: DownloadState, onDownloadClick: () -> Unit) {
    val song = radioSong.song
    val imageUrl = song?.fullImageUrl

    Card(modifier = Modifier.fillMaxWidth()) {
        Box {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = song.era?.description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            }
            if (song != null && song.category.isNotBlank()) {
                CategoryBadge(
                    text = song.category.replaceFirstChar { it.uppercase() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                )
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                radioSong.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (song != null) {
                song.era?.let { InfoRow(Icons.Outlined.Schedule, "Era: ${it.name}") }
                song.era?.let {
                    if (it.timeFrame.isNotBlank()) InfoRow(
                        Icons.Outlined.CalendarMonth,
                        "Time Frame: ${it.timeFrame}"
                    )
                }
                if (song.creditedArtists.isNotBlank()) InfoRow(Icons.Outlined.Person, song.creditedArtists)
                if (song.producers.isNotBlank()) InfoRow(Icons.Outlined.MusicNote, "Produced by: ${song.producers}")
                if (song.length.isNotBlank()) InfoRow(Icons.Outlined.Timer, song.length)
            }

            val label = when (downloadState) {
                DownloadState.Idle -> "Download"
                DownloadState.Downloading -> "Downloading..."
                DownloadState.Saved -> "Saved to Music/Songs"
                DownloadState.AlreadyHave -> "Already downloaded"
                is DownloadState.Failed -> "Retry download"
            }

            Button(
                onClick = onDownloadClick,
                enabled = downloadState is DownloadState.Idle || downloadState is DownloadState.Failed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label)
            }

            if (downloadState is DownloadState.Failed) {
                Text(downloadState.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CategoryBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color(0xFFF7A21B),
        contentColor = Color.White,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
