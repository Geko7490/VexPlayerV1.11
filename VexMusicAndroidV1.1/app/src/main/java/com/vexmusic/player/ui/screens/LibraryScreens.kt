package com.vexmusic.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vexmusic.player.domain.model.SortOrder
import com.vexmusic.player.domain.model.Track
import com.vexmusic.player.ui.MainViewModel
import com.vexmusic.player.ui.Routes
import com.vexmusic.player.ui.components.Artwork
import com.vexmusic.player.ui.components.EmptyState
import com.vexmusic.player.ui.components.SectionHeader
import com.vexmusic.player.ui.components.TrackRow
import com.vexmusic.player.ui.components.VexCardBox
import com.vexmusic.player.ui.components.formatDuration
import com.vexmusic.player.ui.components.formatListening
import com.vexmusic.player.ui.navigateSingle
import com.vexmusic.player.ui.theme.VexCard
import com.vexmusic.player.ui.theme.VexPurple
import com.vexmusic.player.ui.theme.VexTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val AUDIO_MIME_TYPES = arrayOf(
    "audio/mpeg", "audio/mp4", "audio/aac", "audio/flac", "audio/x-flac",
    "audio/wav", "audio/x-wav", "audio/ogg", "audio/opus", "audio/*"
)

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VexTextSecondary)
            }
        }
        actions?.invoke()
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavHostController) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val recent by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val most by vm.mostPlayed.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importUris(uris) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                Text(
                    "VEX MUSIC",
                    style = MaterialTheme.typography.displaySmall,
                    color = VexPurple,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Your music. Your lyrics. Your control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VexTextSecondary
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { picker.launch(AUDIO_MIME_TYPES) },
                    label = { Text("Import files") },
                    leadingIcon = { Icon(Icons.Filled.LibraryAdd, null, Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = { vm.scanDevice() },
                    label = { Text("Scan device") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = { nav.navigateSingle(Routes.FAVORITES) },
                    label = { Text("Favorites") },
                    leadingIcon = { Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = { nav.navigateSingle(Routes.HISTORY) },
                    label = { Text("History") },
                    leadingIcon = { Icon(Icons.Filled.History, null, Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = { nav.navigateSingle(Routes.EQUALIZER) },
                    label = { Text("Equalizer") },
                    leadingIcon = { Icon(Icons.Filled.Equalizer, null, Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = { nav.navigateSingle(Routes.STORAGE) },
                    label = { Text("Storage") },
                    leadingIcon = { Icon(Icons.Filled.Storage, null, Modifier.size(18.dp)) }
                )
            }
        }
        if (importing) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = VexPurple)
                    Spacer(Modifier.width(10.dp))
                    Text("Reading metadata…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (tracks.isEmpty()) {
            item {
                EmptyState(
                    title = "No music imported yet",
                    subtitle = "Import audio files from your device. Files stay on your phone — " +
                        "VEX MUSIC never uploads audio anywhere.",
                    actionLabel = "Import music",
                    onAction = { picker.launch(AUDIO_MIME_TYPES) }
                )
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { vm.playAll(tracks, 0) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play all")
                    }
                    OutlinedButton(
                        onClick = { vm.shufflePlay(tracks) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionHeader("Recently played") }
                item { TrackCarousel(recent) { index -> vm.playAll(recent, index) } }
            }
            if (most.isNotEmpty()) {
                item { SectionHeader("Most played") }
                item { TrackCarousel(most) { index -> vm.playAll(most, index) } }
            }
            item { SectionHeader("Recently added", "See all") { nav.navigateSingle(Routes.LIBRARY) } }
            items(tracks.take(12), key = { it.id }) { track ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    TrackRow(
                        track = track,
                        isPlaying = playback.currentTrackId == track.id,
                        onClick = { vm.playAll(tracks, tracks.indexOf(track)) }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(130.dp)) }
    }
}

@Composable
private fun TrackCarousel(tracks: List<Track>, onClick: (Int) -> Unit) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            Column(
                Modifier.width(132.dp).clickable { onClick(tracks.indexOf(track)) }
            ) {
                Artwork(track.artworkPath, 132.dp, corner = 16.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = VexTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(vm: MainViewModel, nav: NavHostController) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editTrack by remember { mutableStateOf<Track?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importUris(uris) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Library",
            subtitle = "${tracks.size} tracks · sorted by ${settings.sortOrder.name.lowercase().replace('_', ' ')}"
        ) {
            IconButton(onClick = { picker.launch(AUDIO_MIME_TYPES) }) {
                Icon(Icons.Filled.Add, "Import")
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, "Sort") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.name.lowercase().replace('_', ' ')) },
                            onClick = { vm.setSortOrder(order); sortMenu = false }
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Songs", "Artists", "Albums").forEachIndexed { index, label ->
                FilterChip(
                    selected = tab == index,
                    onClick = { tab = index },
                    label = { Text(label) }
                )
            }
        }

        if (selection.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${selection.size} selected", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showPlaylistPicker = true }) { Text("Add to playlist") }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, "Clear") }
            }
        }

        if (tracks.isEmpty()) {
            EmptyState(
                "Library is empty",
                "Use the + button to import audio files (MP3, WAV, FLAC, M4A, AAC, OGG, OPUS).",
                "Import music"
            ) { picker.launch(AUDIO_MIME_TYPES) }
            return@Column
        }

        when (tab) {
            0 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = playback.currentTrackId == track.id,
                        selected = selection.contains(track.id),
                        onClick = {
                            if (selection.isNotEmpty()) {
                                selection = if (selection.contains(track.id)) {
                                    selection - track.id
                                } else selection + track.id
                            } else {
                                vm.playAll(tracks, tracks.indexOf(track))
                            }
                        },
                        onLongClick = { selection = selection + track.id },
                        trailing = {
                            Box {
                                var menu by remember { mutableStateOf(false) }
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Filled.Sort, "Options", Modifier.size(18.dp))
                                }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (track.isFavorite) "Remove favorite" else "Add to favorites") },
                                        onClick = { vm.toggleFavorite(track); menu = false })
                                    DropdownMenuItem(text = { Text("Play next") },
                                        onClick = { vm.playNext(track); menu = false })
                                    DropdownMenuItem(text = { Text("Add to queue") },
                                        onClick = { vm.addToQueue(track); menu = false })
                                    DropdownMenuItem(text = { Text("Edit metadata") },
                                        onClick = { editTrack = track; menu = false })
                                    DropdownMenuItem(text = { Text("Remove from library") },
                                        onClick = { vm.deleteTracks(listOf(track.id)); menu = false })
                                }
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(140.dp)) }
            }

            1 -> GroupedList(
                groups = tracks.groupBy { it.artist },
                onPlay = { list -> vm.playAll(list, 0) }
            )

            2 -> GroupedList(
                groups = tracks.groupBy { it.album },
                onPlay = { list -> vm.playAll(list, 0) }
            )
        }
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add ${selection.size} tracks to") },
            text = {
                Column {
                    if (playlists.isEmpty()) Text("No playlists yet. Create one from the Playlists tab.")
                    playlists.forEach { p ->
                        TextButton(onClick = {
                            vm.addToPlaylist(p.id, selection.toList())
                            showPlaylistPicker = false
                            selection = emptySet()
                        }) { Text("${p.name} (${p.trackCount})") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlaylistPicker = false }) { Text("Close") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove from library?") },
            text = {
                Text(
                    "${selection.size} entries will be removed from VEX MUSIC. " +
                        "The audio files on your device are NOT deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTracks(selection.toList()); selection = emptySet(); confirmDelete = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    editTrack?.let { track ->
        var title by remember { mutableStateOf(track.title) }
        var artist by remember { mutableStateOf(track.artist) }
        var album by remember { mutableStateOf(track.album) }
        AlertDialog(
            onDismissRequest = { editTrack = null },
            title = { Text("Edit metadata") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(artist, { artist = it }, label = { Text("Artist") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(album, { album = it }, label = { Text("Album") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateMetadata(track.copy(title = title, artist = artist, album = album))
                    editTrack = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editTrack = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GroupedList(groups: Map<String, List<Track>>, onPlay: (List<Track>) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(groups.keys.sorted(), key = { it }) { key ->
            val list = groups[key].orEmpty()
            Card(
                colors = CardDefaults.cardColors(containerColor = VexCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    .clickable { onPlay(list) }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(list.firstOrNull()?.artworkPath, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(key, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            "${list.size} tracks · ${formatDuration(list.sumOf { it.durationMs })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VexTextSecondary
                        )
                    }
                    Icon(Icons.Filled.PlayArrow, null, tint = VexPurple)
                }
            }
        }
        item { Spacer(Modifier.height(140.dp)) }
    }
}

@Composable
fun SearchScreen(vm: MainViewModel, nav: NavHostController) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val playlistResults by vm.playlistSearchResults.collectAsStateWithLifecycle()
    val history by vm.searchHistory.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Search", "Songs, artists, albums and playlists")
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.commitSearch() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        if (query.isBlank()) {
            if (history.isNotEmpty()) {
                SectionHeader("Recent searches", "Clear") { vm.clearSearchHistory() }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { entry ->
                        AssistChip(onClick = { vm.setQuery(entry.query) }, label = { Text(entry.query) })
                    }
                }
            } else {
                EmptyState("Search your library", "Find songs, artists, albums or playlists instantly.")
            }
            return@Column
        }

        if (results.isEmpty() && playlistResults.isEmpty()) {
            EmptyState("No results", "Nothing in your library matches \"$query\".")
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            if (playlistResults.isNotEmpty()) {
                item { SectionHeader("Playlists") }
                items(playlistResults, key = { it.id }) { p ->
                    Text(
                        "${p.name} · ${p.trackCount} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { nav.navigate("playlist/${p.id}") }
                            .padding(16.dp)
                    )
                }
            }
            if (results.isNotEmpty()) {
                item { SectionHeader("Songs") }
                items(results, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = playback.currentTrackId == track.id,
                        onClick = { vm.commitSearch(); vm.playAll(results, results.indexOf(track)) }
                    )
                }
            }
            item { Spacer(Modifier.height(140.dp)) }
        }
    }
}

@Composable
fun FavoritesScreen(vm: MainViewModel, nav: NavHostController) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Favorites", "${favorites.size} tracks", onBack = { nav.popBackStack() }) {
            if (favorites.isNotEmpty()) {
                IconButton(onClick = { vm.shufflePlay(favorites) }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle")
                }
                IconButton(onClick = { vm.playAll(favorites, 0) }) {
                    Icon(Icons.Filled.PlayArrow, "Play")
                }
            }
        }
        if (favorites.isEmpty()) {
            EmptyState("No favorites yet", "Long-press or use the track menu to mark songs as favorites.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(favorites, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isPlaying = playback.currentTrackId == track.id,
                    onClick = { vm.playAll(favorites, favorites.indexOf(track)) },
                    trailing = {
                        IconButton(onClick = { vm.toggleFavorite(track) }) {
                            Icon(Icons.Filled.Favorite, "Unfavorite", tint = VexPurple)
                        }
                    }
                )
            }
            item { Spacer(Modifier.height(140.dp)) }
        }
    }
}

@Composable
fun HistoryScreen(vm: MainViewModel, nav: NavHostController) {
    val history by vm.history.collectAsStateWithLifecycle()
    val topArtists by vm.topArtists.collectAsStateWithLifecycle()
    val topTracks by vm.topTracks.collectAsStateWithLifecycle()
    val totalMs by vm.totalListenedMs.collectAsStateWithLifecycle()
    val formatter = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Listening history", "${history.size} plays · ${formatListening(totalMs)} listened",
            onBack = { nav.popBackStack() }
        ) {
            if (history.isNotEmpty()) {
                TextButton(onClick = { vm.clearHistory() }) { Text("Clear") }
            }
        }
        if (history.isEmpty()) {
            EmptyState("No listening history", "Play something and your history will build up here.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                VexCardBox {
                    Column {
                        Text("Most played tracks", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        topTracks.forEach {
                            Text(
                                "${it.key} — ${it.count}×",
                                style = MaterialTheme.typography.bodySmall,
                                color = VexTextSecondary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Most played artists", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        topArtists.forEach {
                            Text(
                                "${it.key} — ${it.count}× · ${formatListening(it.totalMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VexTextSecondary
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Recently played") }
            items(history, key = { it.id }) { entry ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        "${entry.artist} · ${formatter.format(Date(entry.playedAt))} · " +
                            formatListening(entry.listenedMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = VexTextSecondary
                    )
                }
            }
            item { Spacer(Modifier.height(140.dp)) }
        }
    }
}
