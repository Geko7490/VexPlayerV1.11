package com.vexmusic.player.domain.model

/** A playable local audio track. Audio data itself always stays on the device. */
data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String,
    val sizeBytes: Long,
    val year: String?,
    val trackNumber: Int?,
    val artworkPath: String?,
    val dateAdded: Long,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long?,
    val lastPositionMs: Long,
    val matchedProvider: String? = null,
    val matchedProviderId: String? = null,
    val originalTitle: String? = null,
    val originalArtist: String? = null,
    val originalAlbum: String? = null,
    val versionInfo: String? = null,
    val matchedAt: Long? = null
)

data class RecordingCandidate(
    val providerId: String,
    val providerName: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val versionInfo: String?,
    val confidence: Int,
    val reason: String
)

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int = 0,
    val artworkPath: String? = null
)

enum class SortOrder { TITLE, ARTIST, ALBUM, RECENTLY_ADDED, DURATION }

enum class RepeatMode { OFF, ONE, ALL }

/** How a lyrics payload is synchronised. Plain text is never presented as synced. */
enum class SyncType { PLAIN, LINE_SYNCED, WORD_SYNCED }

enum class LyricsFormat { PLAIN, LRC, ENHANCED_LRC, TTML }

/**
 * A single normalised result coming from one lyrics provider.
 * Nothing is auto-applied: the user must explicitly pick one of these.
 */
data class LyricsSearchResult(
    val providerId: String,
    val providerName: String,
    val resultId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val language: String? = null,
    val syncType: SyncType,
    val format: LyricsFormat,
    val content: String,
    val matchScore: Int? = null,
    val matchReason: String? = null
) {
    val preview: String
        get() = content.lineSequence()
            .map { it.replace(Regex("\\[[^\\]]*\\]"), "").trim() }
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString("\n")
}

data class WordTiming(val startMs: Long, val endMs: Long?, val text: String)

data class LyricsLine(
    val timeMs: Long?,
    val text: String,
    val words: List<WordTiming> = emptyList(),
    val translation: String? = null
)

data class ParsedLyrics(
    val lines: List<LyricsLine>,
    val syncType: SyncType,
    val language: String? = null,
    val offsetMs: Long = 0L
) {
    val isSynced: Boolean get() = syncType != SyncType.PLAIN
    val plainText: String get() = lines.joinToString("\n") { it.text }
}

enum class LyricsDisplayMode { ORIGINAL, TRANSLATION, BOTH }

sealed interface ProviderOutcome {
    data class Success(val results: List<LyricsSearchResult>) : ProviderOutcome
    data class Empty(val providerId: String) : ProviderOutcome
    data class Failure(val providerId: String, val message: String) : ProviderOutcome
}

data class HistoryStat(val key: String, val count: Int, val totalMs: Long)
