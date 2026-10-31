package com.vexmusic.player.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vexmusic.player.AppContainer
import com.vexmusic.player.domain.model.LyricsDisplayMode
import com.vexmusic.player.domain.model.LyricsSearchResult
import com.vexmusic.player.domain.model.ParsedLyrics
import com.vexmusic.player.domain.model.SyncType
import com.vexmusic.player.domain.model.Track
import com.vexmusic.player.lyrics.repository.MultiSearchOutcome
import com.vexmusic.player.lyrics.repository.SavedLyrics
import com.vexmusic.player.lyrics.translation.LanguageDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LyricsUiState(
    val track: Track? = null,
    val saved: SavedLyrics? = null,
    val parsed: ParsedLyrics? = null,
    val searching: Boolean = false,
    val outcome: MultiSearchOutcome? = null,
    val preview: LyricsSearchResult? = null,
    val message: String? = null,
    val translating: Boolean = false,
    val translatedLines: List<String> = emptyList(),
    val translationError: String? = null,
    val detectedLanguage: String? = null,
    val displayMode: LyricsDisplayMode = LyricsDisplayMode.ORIGINAL,
    val editorOpen: Boolean = false
)

class LyricsViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.lyricsRepository
    private val settingsRepo = container.settingsRepository

    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var translateJob: Job? = null

    val providers = repo.allProviders()

    /**
     * Loads the lyrics the user previously chose for this track. Online providers are
     * only queried when nothing has been selected yet and automatic search is enabled —
     * and even then results are only *offered*, never applied automatically.
     */
    fun loadForTrack(track: Track) {
        val current = _state.value
        val currentTrack = current.track
        if (currentTrack?.id == track.id) {
            val identityChanged = currentTrack.title != track.title ||
                currentTrack.artist != track.artist || currentTrack.album != track.album
            _state.value = current.copy(track = track)
            if (!identityChanged || current.saved != null) return
            searchJob?.cancel()
            translateJob?.cancel()
            _state.value = _state.value.copy(
                outcome = null,
                preview = null,
                message = null,
                translatedLines = emptyList(),
                translationError = null
            )
            search(track)
            return
        }

        searchJob?.cancel()
        translateJob?.cancel()
        _state.value = LyricsUiState(track = track, displayMode = _state.value.displayMode)
        viewModelScope.launch {
            val saved = repo.getSaved(track.id)
            if (_state.value.track?.id != track.id) return@launch
            val settings = settingsRepo.settings.first()
            if (saved != null) {
                applySaved(saved)
            } else if (settings.autoLyricsSearch) {
                search(track)
            }
            _state.value = _state.value.copy(displayMode = settings.lyricsDisplayMode)
            if (saved != null && settings.translationEnabled &&
                settings.lyricsDisplayMode != LyricsDisplayMode.ORIGINAL
            ) {
                translate()
            }
        }
    }

    private fun applySaved(saved: SavedLyrics) {
        val parsed = saved.parse()
        _state.value = _state.value.copy(
            saved = saved,
            parsed = parsed,
            detectedLanguage = saved.language ?: LanguageDetector.detect(parsed.plainText),
            translatedLines = emptyList()
        )
    }

    fun search(track: Track? = _state.value.track) {
        val t = track ?: return
        searchJob?.cancel()
        _state.value = _state.value.copy(searching = true, message = null, track = t)
        searchJob = viewModelScope.launch {
            val enabled = settingsRepo.settings.first().enabledProviders
                .ifEmpty { container.defaultEnabledProviders }
            val outcome = repo.searchAll(t, enabled)
            if (_state.value.track?.id != t.id) return@launch
            _state.value = _state.value.copy(
                searching = false,
                outcome = outcome,
                message = when {
                    outcome.results.isEmpty() && outcome.failures.isNotEmpty() ->
                        "No results. " + outcome.failures.joinToString { "${it.providerName}: ${it.reason}" }
                    outcome.results.isEmpty() ->
                        "No lyrics found for this track. You can import an .lrc/.txt file or write them yourself."
                    else -> null
                }
            )
        }
    }

    fun preview(result: LyricsSearchResult?) {
        _state.value = _state.value.copy(preview = result)
    }

    /** The only path that makes a result the active lyrics — always user initiated. */
    fun select(result: LyricsSearchResult) = viewModelScope.launch {
        val track = _state.value.track ?: return@launch
        val saved = repo.selectResult(track.id, result)
        if (_state.value.track?.id != track.id) return@launch
        applySaved(saved)
        _state.value = _state.value.copy(preview = null, message = "Using ${result.providerName}")
        val settings = settingsRepo.settings.first()
        if (settings.translationEnabled && settings.lyricsDisplayMode != LyricsDisplayMode.ORIGINAL) {
            translate()
        }
    }

    fun clearSelection() = viewModelScope.launch {
        val track = _state.value.track ?: return@launch
        repo.clearSelection(track.id)
        _state.value = _state.value.copy(
            saved = null, parsed = null, translatedLines = emptyList(),
            message = "Back to automatic search"
        )
        search(track)
    }

    fun importLyricsFile(context: Context, uri: Uri) = viewModelScope.launch {
        val track = _state.value.track ?: return@launch
        val text = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
        }
        if (text.isNullOrBlank()) {
            _state.value = _state.value.copy(message = "Could not read that file")
            return@launch
        }
        val label = if (text.contains("[")) "Imported LRC" else "Imported TXT"
        applySaved(repo.saveManualLyrics(track.id, text, label, userEdited = false))
        _state.value = _state.value.copy(message = "Imported local lyrics")
    }

    fun openEditor(open: Boolean) { _state.value = _state.value.copy(editorOpen = open) }

    fun saveEdited(content: String) = viewModelScope.launch {
        val track = _state.value.track ?: return@launch
        applySaved(repo.saveManualLyrics(track.id, content, "Edited by you", userEdited = true))
        _state.value = _state.value.copy(editorOpen = false, message = "Lyrics saved")
    }

    fun setOffset(offsetMs: Long) = viewModelScope.launch {
        val track = _state.value.track ?: return@launch
        repo.setOffset(track.id, offsetMs)
        _state.value.saved?.let { applySaved(it.copy(offsetMs = offsetMs)) }
    }

    fun setDisplayMode(mode: LyricsDisplayMode) = viewModelScope.launch {
        settingsRepo.setDisplayMode(mode)
        _state.value = _state.value.copy(displayMode = mode)
        val enabled = settingsRepo.settings.first().translationEnabled
        if (mode != LyricsDisplayMode.ORIGINAL && enabled && _state.value.translatedLines.isEmpty()) {
            translate()
        } else if (mode != LyricsDisplayMode.ORIGINAL && !enabled) {
            _state.value = _state.value.copy(translationError = "Enable translation in Settings to continue.")
        }
    }

    fun translate(force: Boolean = false) {
        val current = _state.value
        val track = current.track ?: return
        val parsed = current.parsed ?: return
        translateJob?.cancel()
        _state.value = current.copy(translating = true, translationError = null)
        translateJob = viewModelScope.launch {
            val settings = settingsRepo.settings.first()
            if (!settings.translationEnabled) {
                if (_state.value.track?.id == track.id) {
                    _state.value = _state.value.copy(
                        translating = false,
                        translationError = "Enable translation in Settings to continue."
                    )
                }
                return@launch
            }
            val target = settings.translationTargetLanguage
            val source = current.detectedLanguage ?: LanguageDetector.detect(parsed.plainText)
            try {
                val translated = repo.translate(track.id, parsed.lines.map { it.text }, target, source, force)
                if (_state.value.track?.id == track.id) {
                    _state.value = _state.value.copy(translating = false, translatedLines = translated)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_state.value.track?.id == track.id) {
                    _state.value = _state.value.copy(
                        translating = false,
                        translationError = error.message ?: "Translation failed"
                    )
                }
            }
        }
    }

    fun syncTypeLabel(type: SyncType): String = when (type) {
        SyncType.PLAIN -> "Plain text"
        SyncType.LINE_SYNCED -> "Synchronized (line)"
        SyncType.WORD_SYNCED -> "Synchronized (word)"
    }

    /** Index of the active line for a playback position, honouring the user offset. */
    fun activeLineIndex(positionMs: Long): Int {
        val parsed = _state.value.parsed ?: return -1
        if (!parsed.isSynced) return -1
        val offset = (_state.value.saved?.offsetMs ?: 0L) + parsed.offsetMs
        val target = positionMs - offset
        var low = 0
        var high = parsed.lines.lastIndex
        var active = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val time = parsed.lines[mid].timeMs
            if (time == null || time > target) {
                high = mid - 1
            } else {
                active = mid
                low = mid + 1
            }
        }
        return active
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                LyricsViewModel(container) as T
        }
    }
}
