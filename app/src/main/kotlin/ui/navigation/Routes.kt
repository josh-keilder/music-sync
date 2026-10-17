package io.github.joshkeilder.musicsync.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
data object RandomSongRoute

@Serializable
data object GoogleDriveSyncRoute

enum class TopLevelTab(val route: Any, val label: String, val icon: ImageVector) {
    RandomSong(RandomSongRoute, "Random Song", Icons.Outlined.Shuffle),
    GoogleDriveSync(GoogleDriveSyncRoute, "Sync", Icons.Outlined.Sync),
}
