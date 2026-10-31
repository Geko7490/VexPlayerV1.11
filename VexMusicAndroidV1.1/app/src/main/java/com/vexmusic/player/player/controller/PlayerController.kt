
package com.vexmusic.player.player.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vexmusic.player.domain.model.RepeatMode
import com.vexmusic.player.domain.model.Track
import com.vexmusic.player.player.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f,
    val queueIds: List<String> = emptyList(),
    val queueIndex: Int = -1,
    val isConnected: Boolean = false,
    val error: String? = null
)

data class PlaybackProgress(
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L
)

/**
 * Thin wrapper around a Media3 MediaController bound to PlaybackService.
 * All UI state is exposed as a single StateFlow.
 */
class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var controller: MediaController? = null
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    /** Invoked when playback moves to a different track. */
    var onTrackChanged: ((previousId: String?, listenedMs: Long, newId: String?) -> Unit)? = null

    private var lastTrackId: String? = null
    private var lastTrackStartElapsed: Long = 0L

    fun connect() {
        if (controller != null) return

        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        val future = MediaController.Builder(context, token).buildAsync()

        // Ensure the controller is initialized on the main thread.
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                syncState()
                startTicker()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        tickerJob?.cancel()
        tickerJob = null

        controller?.removeListener(listener)
        controller?.release()
        controller = null

        _state.value = PlaybackState()
        _progress.value = PlaybackProgress()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState()
        }

        override fun onPlayerErrorChanged(
            error: androidx.media3.common.PlaybackException?
        ) {
            _state.value = _state.value.copy(
                error = error?.errorCodeName
            )
        }
    }

    /**
     * MediaController must be accessed on its application thread.
     * Dispatch the ticker to Main instead of using the app's Default dispatcher.
     */
    private fun startTicker() {
        tickerJob?.cancel()

        tickerJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                delay(500)

                val c = controller ?: continue
                if (c.isPlaying) publishProgress(c)
            }
        }
    }

    private fun syncState() {
        val c = controller ?: return

        val ids = (0 until c.mediaItemCount).map {
            c.getMediaItemAt(it).mediaId
        }

        val currentId = c.currentMediaItem?.mediaId
        publishProgress(c)

        if (currentId != lastTrackId) {
            val listened = if (lastTrackStartElapsed > 0) {
                System.currentTimeMillis() - lastTrackStartElapsed
            } else {
                0L
            }

            onTrackChanged?.invoke(
                lastTrackId,
                listened,
                currentId
            )

            lastTrackId = currentId
            lastTrackStartElapsed = System.currentTimeMillis()
        }

        _state.value = PlaybackState(
            currentTrackId = currentId,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0L,
            bufferedMs = c.bufferedPosition.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            speed = c.playbackParameters.speed,
            queueIds = ids,
            queueIndex = c.currentMediaItemIndex,
            isConnected = true,
            error = _state.value.error
        )
    }

    private fun publishProgress(player: Player) {
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        _progress.value = PlaybackProgress(
            currentTrackId = player.currentMediaItem?.mediaId,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            bufferedMs = player.bufferedPosition.coerceAtLeast(0)
        )
    }

    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L
    ) {
        val c = controller ?: return
        if (tracks.isEmpty()) return

        c.setMediaItems(
            tracks.map { it.toMediaItem() },
            startIndex.coerceIn(0, tracks.size - 1),
            startPositionMs
        )

        c.prepare()
        c.play()
        syncState()
    }

    fun togglePlayPause() {
        val c = controller ?: return

        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) {
                c.prepare()
            }
            c.play()
        }

        syncState()
    }

    fun pause() {
        controller?.pause()
        syncState()
    }

    fun next() {
        controller?.seekToNextMediaItem()
        syncState()
    }

    fun previous() {
        val c = controller ?: return

        if (c.currentPosition > 4000) {
            c.seekTo(0)
        } else {
            c.seekToPreviousMediaItem()
        }

        syncState()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        syncState()
    }

    fun seekToIndex(index: Int) {
        controller?.seekTo(index, 0)
        controller?.play()
        syncState()
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        syncState()
    }

    fun cycleRepeat() {
        val c = controller ?: return

        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }

        syncState()
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(
            speed.coerceIn(0.25f, 3f)
        )
        syncState()
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
    }

    fun addToQueue(tracks: List<Track>) {
        controller?.addMediaItems(
            tracks.map { it.toMediaItem() }
        )
        syncState()
    }

    fun playNext(track: Track) {
        val c = controller ?: return

        val index = (c.currentMediaItemIndex + 1)
            .coerceAtMost(c.mediaItemCount)

        c.addMediaItem(index, track.toMediaItem())
        syncState()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return

        if (index in 0 until c.mediaItemCount) {
            c.removeMediaItem(index)
        }

        syncState()
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return

        if (
            from in 0 until c.mediaItemCount &&
            to in 0 until c.mediaItemCount
        ) {
            c.moveMediaItem(from, to)
        }

        syncState()
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        syncState()
    }

    fun currentPosition(): Long {
        return controller?.currentPosition ?: 0L
    }
}

fun Track.toMediaItem(): MediaItem {
    val artwork: Uri? = artworkPath?.let {
        runCatching {
            Uri.fromFile(File(it))
        }.getOrNull()
    }

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artwork)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .build()

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri.toUri())
        .setMediaMetadata(metadata)
        .build()
}
