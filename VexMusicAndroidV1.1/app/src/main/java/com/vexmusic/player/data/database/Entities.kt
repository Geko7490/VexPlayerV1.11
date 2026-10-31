package com.vexmusic.player.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vexmusic.player.domain.model.Track

@Entity(tableName = "tracks", indices = [Index(value = ["uri"], unique = true)])
data class TrackEntity(
    @PrimaryKey val id: String,
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
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val lastPositionMs: Long = 0L,
    val matchedProvider: String? = null,
    val matchedProviderId: String? = null,
    val originalTitle: String? = null,
    val originalArtist: String? = null,
    val originalAlbum: String? = null,
    val versionInfo: String? = null,
    val matchedAt: Long? = null
) {
    fun toDomain() = Track(
        id, uri, title, artist, album, durationMs, mimeType, sizeBytes, year, trackNumber,
        artworkPath, dateAdded, isFavorite, playCount, lastPlayedAt, lastPositionMs,
        matchedProvider, matchedProviderId, originalTitle, originalArtist, originalAlbum,
        versionInfo, matchedAt
    )
}

fun Track.toEntity() = TrackEntity(
    id, uri, title, artist, album, durationMs, mimeType, sizeBytes, year, trackNumber,
    artworkPath, dateAdded, isFavorite, playCount, lastPlayedAt, lastPositionMs,
    matchedProvider, matchedProviderId, originalTitle, originalArtist, originalAlbum,
    versionInfo, matchedAt
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val artworkPath: String? = null
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long
)

/**
 * Locally cached lyrics for one track. The user's explicit source selection
 * is persisted here and reused on the next playback.
 */
@Entity(tableName = "lyrics", indices = [Index(value = ["trackId"], unique = true)])
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val providerId: String,
    val providerName: String,
    val resultId: String,
    val content: String,
    val format: String,
    val syncType: String,
    val language: String?,
    val userSelected: Boolean,
    val userEdited: Boolean,
    val offsetMs: Long = 0L,
    val updatedAt: Long
)

@Entity(
    tableName = "translations",
    primaryKeys = ["trackId", "targetLanguage"],
    indices = [Index("trackId")]
)
data class TranslationEntity(
    val trackId: String,
    val targetLanguage: String,
    val sourceHash: Int,
    val providerName: String,
    val translatedLines: String,
    val updatedAt: Long
)

@Entity(tableName = "history", indices = [Index("trackId"), Index("playedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val playedAt: Long,
    val listenedMs: Long
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long
)
