package com.vexmusic.player.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vexmusic.player.ui.MainViewModel
import com.vexmusic.player.ui.components.Artwork
import com.vexmusic.player.ui.components.EmptyState
import com.vexmusic.player.ui.components.TrackRow
import com.vexmusic.player.ui.components.formatDuration
import com.vexmusic.player.ui.theme.VexCard
import com.vexmusic.player.ui.theme.VexPurple
import com.vexmusic.player.ui.theme.VexTextSecondary
import kotlinx.coroutines.flow.Flow

@Composable
fun PlaylistsScreen(vm: MainViewModel, nav: NavHostController) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<Long?>(null) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Playlists", "${playlists.size} playlists") {
            IconButton(onClick = { newName = ""; showCreate = true }) {
                Icon(Icons.Filled.Add, "New playlist")
            }
        }
        if (playlists.isEmpty()) {
            EmptyState(
                "No playlists", "Create a playlist and add tracks from your library.",
                "Create playlist"
            ) { newName = ""; showCreate = true }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VexCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        .clickable { nav.navigate("playlist/${playlist.id}") }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(playlist.artworkPath, 52.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = VexTextSecondary
                            )
                        }
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, "Options")
                            }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                    renameId = playlist.id; newName = playlist.name; menu = false
                                })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                    vm.deletePlaylist(playlist.id); menu = false
                                })
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(140.dp)) }
        }
    }

    if (showCreate || renameId != null) {
        val isRename = renameId != null
        AlertDialog(
            onDismissRequest = { showCreate = false; renameId = null },
            title = { Text(if (isRename) "Rename playlist" else "New playlist") },
            text = {
                OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        if (isRename) vm.renamePlaylist(renameId!!, newName)
                        else vm.createPlaylist(newName)
                        showCreate = false; renameId = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false; renameId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PlaylistDetailScreen(playlistId: Long, vm: MainViewModel, nav: NavHostController) {
    val tracksFlow: Flow<List<com.vexmusic.player.domain.model.Track>> =
        remember(playlistId) { vm.playlistTracks(playlistId) }
    val tracks by tracksFlow.collectAsStateWithLifecycle(initial = emptyList())
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val library by vm.tracks.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    var showAdd by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            playlist?.name ?: "Playlist",
            "${tracks.size} tracks · ${formatDuration(tracks.sumOf { it.durationMs })}",
            onBack = { nav.popBackStack() }
        ) {
            IconButton(onClick = { picked = emptySet(); showAdd = true }) {
                Icon(Icons.Filled.Add, "Add tracks")
            }
            IconButton(onClick = { vm.shufflePlay(tracks) }) { Icon(Icons.Filled.Shuffle, "Shuffle") }
            IconButton(onClick = { vm.playAll(tracks, 0) }) { Icon(Icons.Filled.PlayArrow, "Play") }
        }
        if (tracks.isEmpty()) {
            EmptyState("Empty playlist", "Add tracks from your library.", "Add tracks") {
                picked = emptySet(); showAdd = true
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(tracks, key = { it.id }) { track ->
                val index = tracks.indexOf(track)
                TrackRow(
                    track = track,
                    isPlaying = playback.currentTrackId == track.id,
                    onClick = { vm.playAll(tracks, index) },
                    trailing = {
                        Row {
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val ids = tracks.map { it.id }.toMutableList()
                                    ids.removeAt(index); ids.add(index - 1, track.id)
                                    vm.reorderPlaylist(playlistId, ids)
                                }
                            ) { Icon(Icons.Filled.ArrowUpward, "Up", Modifier.size(18.dp)) }
                            IconButton(
                                enabled = index < tracks.lastIndex,
                                onClick = {
                                    val ids = tracks.map { it.id }.toMutableList()
                                    ids.removeAt(index); ids.add(index + 1, track.id)
                                    vm.reorderPlaylist(playlistId, ids)
                                }
                            ) { Icon(Icons.Filled.ArrowDownward, "Down", Modifier.size(18.dp)) }
                            IconButton(onClick = { vm.removeFromPlaylist(playlistId, track.id) }) {
                                Icon(Icons.Filled.Delete, "Remove", Modifier.size(18.dp))
                            }
                        }
                    }
                )
            }
            item { Spacer(Modifier.height(140.dp)) }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add tracks") },
            text = {
                LazyColumn(Modifier.height(380.dp)) {
                    items(library, key = { it.id }) { track ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                picked = if (picked.contains(track.id)) picked - track.id
                                else picked + track.id
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (picked.contains(track.id)) "✓" else "○",
                                color = VexPurple,
                                modifier = Modifier.width(24.dp)
                            )
                            Column {
                                Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VexTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addToPlaylist(playlistId, picked.toList()); showAdd = false
                }) { Text("Add ${picked.size}") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}
