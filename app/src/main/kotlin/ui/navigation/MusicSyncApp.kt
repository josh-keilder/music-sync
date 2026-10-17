@file:Suppress("FunctionName")

package io.github.joshkeilder.musicsync.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.joshkeilder.musicsync.ui.googledrivesync.GoogleDriveSyncScreen
import io.github.joshkeilder.musicsync.ui.randomsong.RandomSongScreen

@Composable
fun MusicSyncApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = RandomSongRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<RandomSongRoute> { RandomSongScreen() }
            composable<GoogleDriveSyncRoute> { GoogleDriveSyncScreen() }
        }
    }
}
