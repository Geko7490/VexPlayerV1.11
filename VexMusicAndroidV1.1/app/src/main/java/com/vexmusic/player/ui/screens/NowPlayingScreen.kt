package com.vexmusic.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vexmusic.player.AppContainer
import com.vexmusic.player.domain.model.RepeatMode
import com.vexmusic.player.player.service.PlaybackService
import com.vexmusic.player.ui.MainViewModel
import com.vexmusic.player.ui.Routes
import com.vexmusic.player.ui.components.Artwork
import com.vexmusic.player.ui.components.AudioVisualizer
import com.vexmusic.player.ui.components.EmptyState
import com.vexmusic.player.ui.components.VisualizerMode
import com.vexmusic.player.ui.components.formatDuration
import com.vexmusic.player.ui.theme.VexPurple
import com.vexmusic.player.ui.theme.VexTextSecondary

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(vm: MainViewModel, container: AppContainer, nav: NavHostController) {
    val track by vm.currentTrack.collectAsStateWithLifecycle()
    val playback by vm.playbackState.collectAsStateWithLifecycle()
    val progressState by vm.playbackProgress.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val queue by vm.queueTracks.collectAsStateWithLifecycle()
    val sessionId by PlaybackService.audioSessionIdFlow.collectAsStateWithLifecycle()
    val frame by container.visualizerController.frame.collectAsStateWithLifecycle()
    val visError by container.visualizerController.error.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    var volume by remember { mutableStateOf(1f) }

    // Visualizer is only running while this screen is visible and enabled in Settings.
    DisposableEffect(settings.visualizerEnabled, sessionId, playback.isPlaying) {
        if (settings.visualizerEnabled && sessionId != 0 && playback.isPlaying) {
            container.visualizerController.start(sessionId)
        } else {
            container.visualizerController.stop()
        }
        onDispose { container.visualizerController.stop() }
    }

    if (track == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Now playing", onBack = { nav.popBackStack() })
            EmptyState("Nothing is playing", "Pick a track from your library to start listening.")
        }
        return
    }

    val current = track!!
    val duration = progressState.durationMs.takeIf { it > 0 } ?: current.durationMs
    val progress = if (scrubbing) scrubValue else {
        if (duration > 0) (progressState.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ExpandMore, "Close") }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = VexTextSecondary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showQueue = true }) { Icon(Icons.Filled.QueueMusic, "Queue") }
        }

        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Artwork(current.artworkPath, maxWidth.coerceAtMost(360.dp), corner = 12.dp)
        }

        Spacer(Modifier.height(18.dp))
        Text(
            current.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${current.artist} · ${current.album}",
            style = MaterialTheme.typography.bodyMedium,
            color = VexTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (settings.visualizerEnabled) {
            Spacer(Modifier.height(8.dp))
            AudioVisualizer(
                frame = frame,
                mode = runCatching { VisualizerMode.valueOf(settings.visualizerMode) }
                    .getOrDefault(VisualizerMode.BARS),
                message = visError?.let { "Visualizer unavailable: $it" }
            )
        }

        Spacer(Modifier.height(10.dp))
        Slider(
            value = progress,
            onValueChange = { scrubbing = true; scrubValue = it },
            onValueChangeFinished = {
                vm.seekTo((scrubValue * duration).toLong())
                scrubbing = false
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration((progress * duration).toLong()),
                style = MaterialTheme.typography.bodySmall, color = VexTextSecondary
            )
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.bodySmall, color = VexTextSecondary
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle, "Shuffle",
                    tint = if (playback.shuffle) VexPurple else VexTextSecondary
                )
            }
            IconButton(onClick = vm::previous) {
                Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(38.dp))
            }
            IconButton(
                onClick = vm::togglePlayPause,
                modifier = Modifier.size(70.dp)
            ) {
                AnimatedContent(targetState = playback.isPlaying, label = "play-pause") { playing ->
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Play/Pause",
                        tint = VexPurple,
                        modifier = Modifier.size(58.dp)
                    )
                }
            }
            IconButton(onClick = vm::next) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(38.dp))
            }
            IconButton(onClick = vm::cycleRepeat) {
                Icon(
                    if (playback.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat",
                    tint = if (playback.repeatMode == RepeatMode.OFF) VexTextSecondary else VexPurple
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.VolumeUp, null, tint = VexTextSecondary, modifier = Modifier.size(18.dp))
            Slider(
                value = volume,
                onValueChange = { volume = it; vm.setVolume(it) },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            IconButton(onClick = { vm.toggleFavorite(current) }) {
                Icon(
                    if (current.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    "Favorite",
                    tint = VexPurple
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { nav.navigate(Routes.LYRICS) },
                label = { Text("Lyrics") },
                leadingIcon = { Icon(Icons.Filled.Lyrics, null, Modifier.size(18.dp)) }
            )
            listOf(0.75f, 1f, 1.25f, 1.5f).forEach { speed ->
                AssistChip(
                    onClick = { vm.setSpeed(speed) },
                    label = {
                        Text(
                            "${speed}×",
                            color = if (playback.speed == speed) VexPurple else VexTextSecondary
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Queue · ${queue.size}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.clearQueue(); showQueue = false }) { Text("Clear") }
            }
            LazyColumn(Modifier.fillMaxWidth().height(420.dp).padding(horizontal = 12.dp)) {
                items(queue.size) { index ->
                    val item = queue[index]
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.jumpToQueueIndex(index) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(item.artworkPath, 40.dp, corner = 8.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (index == playback.queueIndex) VexPurple
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = VexTextSecondary,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = { vm.moveQueueItem(index, index - 1) }
                        ) { Icon(Icons.Filled.ArrowUpward, "Up", Modifier.size(16.dp)) }
                        IconButton(
                            enabled = index < queue.lastIndex,
                            onClick = { vm.moveQueueItem(index, index + 1) }
                        ) { Icon(Icons.Filled.ArrowDownward, "Down", Modifier.size(16.dp)) }
                        IconButton(onClick = { vm.removeFromQueue(index) }) {
                            Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
