package com.vexmusic.player.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vexmusic.player.AppContainer
import com.vexmusic.player.data.preferences.VexSettings
import com.vexmusic.player.data.repository.MusicRepository
import com.vexmusic.player.data.repository.StorageUsage
import com.vexmusic.player.domain.model.HistoryStat
import com.vexmusic.player.domain.model.Playlist
import com.vexmusic.player.domain.model.SortOrder
import com.vexmusic.player.domain.model.Track
import com.vexmusic.player.player.controller.PlaybackProgress
import com.vexmusic.player.player.controller.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportFeedback(val message: String, val isError: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val music = container.musicRepository
    private val playlistsRepo = container.playlistRepository
    private val historyRepo = container.historyRepository
    private val settingsRepo = container.settingsRepository
    val player = container.playerController

    val settings: StateFlow<VexSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, VexSettings())

    val playbackState: StateFlow<PlaybackState> = player.state
    val playbackProgress: StateFlow<PlaybackProgress> = player.progress

    val tracks: StateFlow<List<Track>> = combine(music.tracks, settings) { list, s ->
        MusicRepository.sort(list, s.sortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Track>> = music.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = music.recentlyPlayed(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<Track>> = music.mostPlayed(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = playlistsRepo.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = historyRepo.recent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topArtists: StateFlow<List<HistoryStat>> = historyRepo.topArtists(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topTracks: StateFlow<List<HistoryStat>> = historyRepo.topTracks(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalListenedMs: StateFlow<Long> = historyRepo.totalListenedMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val searchHistory = historyRepo.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val searchResults: StateFlow<List<Track>> = _query
        .flatMapLatest { q ->
            if (q.isBlank()) music.tracks.map { emptyList() } else music.search(q.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistSearchResults: StateFlow<List<Playlist>> =
        combine(_query, playlistsRepo.playlists) { q, list ->
            if (q.isBlank()) emptyList()
            else list.filter { it.name.contains(q.trim(), ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _feedback = MutableStateFlow<ImportFeedback?>(null)
    val feedback: StateFlow<ImportFeedback?> = _feedback

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    private val _usage = MutableStateFlow(StorageUsage())
    val usage: StateFlow<StorageUsage> = _usage

    val currentTrack: StateFlow<Track?> = combine(playbackState, music.tracks) { state, list ->
        list.firstOrNull { it.id == state.currentTrackId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val queueTracks: StateFlow<List<Track>> = combine(playbackState, music.tracks) { state, list ->
        val byId = list.associateBy { it.id }
        state.queueIds.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- library ----
    fun importUris(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        _importing.value = true
        val result = music.importFromUris(uris)
        _importing.value = false
        _feedback.value = ImportFeedback(
            "Imported ${result.imported} · duplicates ${result.duplicates} · failed ${result.failed}",
            isError = result.imported == 0 && result.failed > 0
        )
    }

    fun scanDevice() = viewModelScope.launch {
        _importing.value = true
        val result = music.scanDeviceAudio()
        _importing.value = false
        _feedback.value = ImportFeedback(
            "Device scan: ${result.imported} added, ${result.duplicates} already in library",
            isError = result.imported == 0 && result.failed > 0
        )
    }

    fun clearFeedback() { _feedback.value = null }

    fun deleteTracks(ids: List<String>) = viewModelScope.launch {
        music.delete(ids)
        _feedback.value = ImportFeedback("Removed ${ids.size} entries from the library")
    }

    fun updateMetadata(track: Track) = viewModelScope.launch { music.updateMetadata(track) }

    fun toggleFavorite(track: Track) = viewModelScope.launch {
        music.setFavorite(track.id, !track.isFavorite)
    }

    fun setSortOrder(order: SortOrder) = viewModelScope.launch { settingsRepo.setSortOrder(order) }

    // ---- playback ----
    fun playAll(list: List<Track>, index: Int = 0) {
        if (list.isEmpty()) return
        val resume = list.getOrNull(index)?.lastPositionMs ?: 0L
        val start = if (resume > 5000 && resume < (list[index].durationMs - 5000)) resume else 0L
        player.playTracks(list, index, start)
    }

    fun shufflePlay(list: List<Track>) {
        if (list.isEmpty()) return
        player.setShuffle(true)
        player.playTracks(list.shuffled(), 0)
    }

    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun cycleRepeat() = player.cycleRepeat()
    fun toggleShuffle() = player.setShuffle(!playbackState.value.shuffle)
    fun setSpeed(speed: Float) {
        player.setSpeed(speed)
        viewModelScope.launch { settingsRepo.setPlaybackSpeed(speed) }
    }
    fun setVolume(v: Float) = player.setVolume(v)
    fun addToQueue(track: Track) = player.addToQueue(listOf(track))
    fun playNext(track: Track) = player.playNext(track)
    fun removeFromQueue(index: Int) = player.removeFromQueue(index)
    fun moveQueueItem(from: Int, to: Int) = player.moveQueueItem(from, to)
    fun clearQueue() = player.clearQueue()
    fun jumpToQueueIndex(index: Int) = player.seekToIndex(index)

    // ---- playlists ----
    fun createPlaylist(name: String, trackIds: List<String> = emptyList()) = viewModelScope.launch {
        val id = playlistsRepo.create(name)
        if (trackIds.isNotEmpty()) playlistsRepo.addTracks(id, trackIds)
    }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { playlistsRepo.rename(id, name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { playlistsRepo.delete(id) }
    fun addToPlaylist(playlistId: Long, trackIds: List<String>) =
        viewModelScope.launch { playlistsRepo.addTracks(playlistId, trackIds) }
    fun removeFromPlaylist(playlistId: Long, trackId: String) =
        viewModelScope.launch { playlistsRepo.removeTrack(playlistId, trackId) }
    fun reorderPlaylist(playlistId: Long, orderedIds: List<String>) =
        viewModelScope.launch { playlistsRepo.reorder(playlistId, orderedIds) }
    fun playlistTracks(playlistId: Long) = playlistsRepo.observeTracks(playlistId)

    // ---- search ----
    fun setQuery(value: String) { _query.value = value }
    fun commitSearch() = viewModelScope.launch { historyRepo.recordSearch(_query.value) }
    fun clearSearchHistory() = viewModelScope.launch { historyRepo.clearSearchHistory() }

    // ---- history ----
    fun clearHistory() = viewModelScope.launch { historyRepo.clear() }

    // ---- storage / backup ----
    fun refreshUsage() = viewModelScope.launch { _usage.value = container.backupRepository.usage() }

    fun clearLyricsCache() = viewModelScope.launch {
        container.lyricsRepository.clearLyricsCache()
        refreshUsage()
        _feedback.value = ImportFeedback("Lyrics cache cleared")
    }

    fun clearTranslationCache() = viewModelScope.launch {
        container.lyricsRepository.clearTranslationCache()
        refreshUsage()
        _feedback.value = ImportFeedback("Translation cache cleared")
    }

    fun clearAllData() = viewModelScope.launch {
        container.playerController.clearQueue()
        container.backupRepository.clearAllAppData()
        refreshUsage()
        _feedback.value = ImportFeedback("All VEX MUSIC data cleared (audio files untouched)")
    }

    fun exportBackup(target: Uri) = viewModelScope.launch {
        container.backupRepository.export(target).fold(
            onSuccess = { _feedback.value = ImportFeedback("Backup exported (${it / 1024} KB)") },
            onFailure = { _feedback.value = ImportFeedback("Backup failed: ${it.message}", true) }
        )
    }

    fun importBackup(source: Uri) = viewModelScope.launch {
        container.backupRepository.import(source).fold(
            onSuccess = {
                refreshUsage()
                _feedback.value = ImportFeedback(
                    "Restored ${it.tracks.size} tracks, ${it.playlists.size} playlists, ${it.lyrics.size} lyrics"
                )
            },
            onFailure = { _feedback.value = ImportFeedback("Restore failed: ${it.message}", true) }
        )
    }

    // ---- settings ----
    fun settingsRepository() = settingsRepo

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                MainViewModel(container) as T
        }
    }
}
