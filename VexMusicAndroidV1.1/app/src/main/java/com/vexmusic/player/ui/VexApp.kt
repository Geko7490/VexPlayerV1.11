package com.vexmusic.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vexmusic.player.AppContainer
import com.vexmusic.player.ui.components.MiniPlayer
import com.vexmusic.player.ui.screens.EqualizerScreen
import com.vexmusic.player.ui.screens.FavoritesScreen
import com.vexmusic.player.ui.screens.HistoryScreen
import com.vexmusic.player.ui.screens.HomeScreen
import com.vexmusic.player.ui.screens.LibraryScreen
import com.vexmusic.player.ui.screens.LyricsScreen
import com.vexmusic.player.ui.screens.LyricsSourceScreen
import com.vexmusic.player.ui.screens.NowPlayingScreen
import com.vexmusic.player.ui.screens.PlaylistDetailScreen
import com.vexmusic.player.ui.screens.PlaylistsScreen
import com.vexmusic.player.ui.screens.SearchScreen
import com.vexmusic.player.ui.screens.SettingsScreen
import com.vexmusic.player.ui.screens.StorageScreen
import com.vexmusic.player.ui.theme.VexBackground
import com.vexmusic.player.ui.theme.VexGradient
import com.vexmusic.player.ui.theme.VexPurple
import com.vexmusic.player.ui.theme.VexSurface
import com.vexmusic.player.ui.theme.VexTextSecondary

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist/{id}"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val NOW_PLAYING = "now_playing"
    const val LYRICS = "lyrics"
    const val LYRICS_SOURCES = "lyrics_sources"
    const val EQUALIZER = "equalizer"
    const val STORAGE = "storage"
    const val SETTINGS = "settings"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomItem(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    BottomItem(Routes.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    BottomItem(Routes.PLAYLISTS, "Playlists", Icons.Filled.QueueMusic, Icons.Outlined.QueueMusic),
    BottomItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun VexApp(container: AppContainer) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val lyricsViewModel: LyricsViewModel = viewModel(factory = LyricsViewModel.factory(container))

    val snackbarHostState = remember { SnackbarHostState() }
    val feedback by mainViewModel.feedback.collectAsStateWithLifecycle()
    val playback by mainViewModel.playbackState.collectAsStateWithLifecycle()
    val currentTrack by mainViewModel.currentTrack.collectAsStateWithLifecycle()

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it.message)
            mainViewModel.clearFeedback()
        }
    }

    LaunchedEffect(currentTrack?.id) {
        currentTrack?.let { lyricsViewModel.loadForTrack(it) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val fullScreenRoutes = setOf(Routes.NOW_PLAYING, Routes.LYRICS, Routes.LYRICS_SOURCES)
    val showChrome = currentRoute !in fullScreenRoutes

    Scaffold(
        containerColor = VexBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showChrome) {
                Column(Modifier.navigationBarsPadding()) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = playback.isPlaying,
                        progress = mainViewModel.playbackProgress,
                        onExpand = { navController.navigateSingle(Routes.NOW_PLAYING) },
                        onLyrics = { navController.navigateSingle(Routes.LYRICS) },
                        onPlayPause = mainViewModel::togglePlayPause,
                        onNext = mainViewModel::next
                    )
                    VexBottomBar(navController, currentRoute)
                }
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(VexGradient)
                .padding(innerPadding)
        ) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(mainViewModel, navController) }
                composable(Routes.LIBRARY) { LibraryScreen(mainViewModel, navController) }
                composable(Routes.SEARCH) { SearchScreen(mainViewModel, navController) }
                composable(Routes.PLAYLISTS) { PlaylistsScreen(mainViewModel, navController) }
                composable(Routes.PLAYLIST_DETAIL) { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                    PlaylistDetailScreen(id, mainViewModel, navController)
                }
                composable(Routes.FAVORITES) { FavoritesScreen(mainViewModel, navController) }
                composable(Routes.HISTORY) { HistoryScreen(mainViewModel, navController) }
                composable(Routes.NOW_PLAYING) {
                    NowPlayingScreen(mainViewModel, container, navController)
                }
                composable(Routes.LYRICS) {
                    LyricsScreen(mainViewModel, lyricsViewModel, navController)
                }
                composable(Routes.LYRICS_SOURCES) {
                    LyricsSourceScreen(lyricsViewModel, navController)
                }
                composable(Routes.EQUALIZER) { EqualizerScreen(container, navController) }
                composable(Routes.STORAGE) { StorageScreen(mainViewModel, navController) }
                composable(Routes.SETTINGS) { SettingsScreen(mainViewModel, container, navController) }
            }
        }
    }
}

@Composable
private fun VexBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = VexSurface, tonalElevation = 0.dp) {
        bottomItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateSingle(item.route) },
                icon = {
                    Icon(
                        if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VexPurple,
                    selectedTextColor = VexPurple,
                    unselectedIconColor = VexTextSecondary,
                    unselectedTextColor = VexTextSecondary,
                    indicatorColor = VexPurple.copy(alpha = 0.15f)
                )
            )
        }
    }
}

fun NavHostController.navigateSingle(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
