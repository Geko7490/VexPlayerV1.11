package com.vexmusic.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vexmusic.player.domain.model.Track
import com.vexmusic.player.player.controller.PlaybackProgress
import com.vexmusic.player.ui.theme.VexCard
import com.vexmusic.player.ui.theme.VexPurple
import com.vexmusic.player.ui.theme.VexTextSecondary
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    progress: StateFlow<PlaybackProgress>,
    onExpand: () -> Unit,
    onLyrics: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    AnimatedVisibility(
        visible = track != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        val current = track ?: return@AnimatedVisibility
        val playbackProgress by progress.collectAsStateWithLifecycle()
        val fraction = if (current.durationMs > 0) {
            (playbackProgress.positionMs.toFloat() / current.durationMs).coerceIn(0f, 1f)
        } else 0f

        Surface(
            color = VexCard,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { onExpand() }
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Artwork(current.artworkPath, 44.dp, corner = 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            current.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = VexTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onLyrics) {
                        Icon(Icons.Filled.Lyrics, "Lyrics", tint = VexTextSecondary)
                    }
                    IconButton(onClick = onPlayPause) {
                        AnimatedContent(targetState = isPlaying, label = "mini-play-pause") { playing ->
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "Play/Pause",
                                tint = VexPurple
                            )
                        }
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Filled.SkipNext, "Next")
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = VexPurple,
                        trackColor = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
