package com.vexmusic.player.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.vexmusic.player.data.database.HistoryEntity
import com.vexmusic.player.data.database.LyricsEntity
import com.vexmusic.player.data.database.PlaylistEntity
import com.vexmusic.player.data.database.PlaylistTrackEntity
import com.vexmusic.player.data.database.TrackEntity
import com.vexmusic.player.data.database.TranslationEntity
import com.vexmusic.player.data.database.VexDatabase
import com.vexmusic.player.data.preferences.SettingsRepository
import com.vexmusic.player.lyrics.repository.LyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupTrack(
    val id: String, val uri: String, val title: String, val artist: String, val album: String,
    val durationMs: Long, val mimeType: String, val sizeBytes: Long, val year: String? = null,
    val trackNumber: Int? = null, val dateAdded: Long = 0, val isFavorite: Boolean = false,
    val playCount: Int = 0, val lastPlayedAt: Long? = null, val lastPositionMs: Long = 0,
    val matchedProvider: String? = null, val matchedProviderId: String? = null,
    val originalTitle: String? = null, val originalArtist: String? = null,
    val originalAlbum: String? = null, val versionInfo: String? = null, val matchedAt: Long? = null
)

@Serializable
data class BackupPlaylist(
    val id: Long, val name: String, val createdAt: Long, val trackIds: List<String>
)

@Serializable
data class BackupLyrics(
    val trackId: String, val providerId: String, val providerName: String, val resultId: String,
    val content: String, val format: String, val syncType: String, val language: String? = null,
    val userSelected: Boolean = true, val userEdited: Boolean = false, val offsetMs: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class BackupTranslation(
    val trackId: String, val targetLanguage: String, val sourceHash: Int,
    val providerName: String, val translatedLines: String, val updatedAt: Long
)

@Serializable
data class BackupHistory(
    val trackId: String, val title: String, val artist: String,
    val playedAt: Long, val listenedMs: Long
)

@Serializable
data class VexBackup(
    val formatVersion: Int = BACKUP_VERSION,
    val app: String = "VEX MUSIC",
    val createdAt: Long = 0,
    val tracks: List<BackupTrack> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val favorites: List<String> = emptyList(),
    val lyrics: List<BackupLyrics> = emptyList(),
    val translations: List<BackupTranslation> = emptyList(),
    val history: List<BackupHistory> = emptyList()
)

const val BACKUP_VERSION = 1

data class StorageUsage(
    val trackCount: Int = 0,
    val musicBytes: Long = 0,
    val artworkBytes: Long = 0,
    val lyricsCount: Int = 0,
    val lyricsBytes: Long = 0,
    val translationCount: Int = 0,
    val translationBytes: Long = 0,
    val playlistCount: Int = 0,
    val historyCount: Int = 0,
    val databaseBytes: Long = 0,
    val freeDeviceBytes: Long = 0
)

class BackupRepository(
    private val context: Context,
    private val db: VexDatabase,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("vex_music.db")
        val stat = runCatching {
            StatFs(Environment.getDataDirectory().path).availableBytes
        }.getOrDefault(0L)
        StorageUsage(
            trackCount = musicRepository.trackCount(),
            musicBytes = musicRepository.libraryBytes(),
            artworkBytes = musicRepository.artworkBytes(),
            lyricsCount = lyricsRepository.lyricsCount(),
            lyricsBytes = lyricsRepository.lyricsCacheBytes(),
            translationCount = lyricsRepository.translationCount(),
            translationBytes = lyricsRepository.translationCacheBytes(),
            playlistCount = db.playlistDao().playlistCount(),
            historyCount = db.historyDao().count(),
            databaseBytes = if (dbFile.exists()) dbFile.length() else 0L,
            freeDeviceBytes = stat
        )
    }

    suspend fun export(target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = db.trackDao().getAllOnce()
            val playlists = db.playlistDao().allPlaylists()
            val items = db.playlistDao().allItems().groupBy { it.playlistId }
            val (lyrics, translations) = lyricsRepository.exportAll()
            val backup = VexBackup(
                createdAt = System.currentTimeMillis(),
                tracks = tracks.map { it.toBackup() },
                playlists = playlists.map { p ->
                    BackupPlaylist(
                        p.id, p.name, p.createdAt,
                        items[p.id].orEmpty().sortedBy { it.position }.map { it.trackId }
                    )
                },
                favorites = tracks.filter { it.isFavorite }.map { it.id },
                lyrics = lyrics.map { it.toBackup() },
                translations = translations.map { it.toBackup() },
                history = db.historyDao().all().map {
                    BackupHistory(it.trackId, it.title, it.artist, it.playedAt, it.listenedMs)
                }
            )
            val payload = json.encodeToString(backup)
            context.contentResolver.openOutputStream(target, "wt")?.use {
                it.write(payload.toByteArray())
            } ?: error("Could not open backup destination")
            payload.length
        }
    }

    /** Validates the backup envelope before touching any existing data. */
    suspend fun import(source: Uri, mergeOnly: Boolean = true): Result<VexBackup> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(source)?.use {
                    it.readBytes().decodeToString()
                } ?: error("Could not read backup file")
                val backup = json.decodeFromString(VexBackup.serializer(), text)
                require(backup.app == "VEX MUSIC") { "Not a VEX MUSIC backup file" }
                require(backup.formatVersion in 1..BACKUP_VERSION) {
                    "Unsupported backup version ${backup.formatVersion}"
                }

                if (!mergeOnly) {
                    db.trackDao().deleteAll()
                }
                val existing = db.trackDao().getAllOnce().associateBy { it.id }
                val restoredTracks = backup.tracks.map { t ->
                    TrackEntity(
                        id = t.id, uri = t.uri, title = t.title, artist = t.artist, album = t.album,
                        durationMs = t.durationMs, mimeType = t.mimeType, sizeBytes = t.sizeBytes,
                        year = t.year, trackNumber = t.trackNumber,
                        artworkPath = existing[t.id]?.artworkPath,
                        dateAdded = t.dateAdded, isFavorite = t.isFavorite || backup.favorites.contains(t.id),
                        playCount = t.playCount, lastPlayedAt = t.lastPlayedAt,
                        lastPositionMs = t.lastPositionMs,
                        matchedProvider = t.matchedProvider,
                        matchedProviderId = t.matchedProviderId,
                        originalTitle = t.originalTitle,
                        originalArtist = t.originalArtist,
                        originalAlbum = t.originalAlbum,
                        versionInfo = t.versionInfo,
                        matchedAt = t.matchedAt
                    )
                }
                if (restoredTracks.isNotEmpty()) db.trackDao().upsertAll(restoredTracks)

                backup.playlists.forEach { p ->
                    val id = db.playlistDao().insert(
                        PlaylistEntity(name = p.name, createdAt = p.createdAt)
                    )
                    val now = System.currentTimeMillis()
                    db.playlistDao().insertItems(
                        p.trackIds.mapIndexed { i, tid -> PlaylistTrackEntity(id, tid, i, now) }
                    )
                }
                lyricsRepository.restore(
                    backup.lyrics.map {
                        LyricsEntity(
                            it.trackId, it.providerId, it.providerName, it.resultId, it.content,
                            it.format, it.syncType, it.language, it.userSelected, it.userEdited,
                            it.offsetMs, it.updatedAt
                        )
                    },
                    backup.translations.map {
                        TranslationEntity(
                            it.trackId, it.targetLanguage, it.sourceHash, it.providerName,
                            it.translatedLines, it.updatedAt
                        )
                    }
                )
                if (backup.history.isNotEmpty()) {
                    db.historyDao().insertAll(
                        backup.history.map {
                            HistoryEntity(
                                trackId = it.trackId, title = it.title, artist = it.artist,
                                playedAt = it.playedAt, listenedMs = it.listenedMs
                            )
                        }
                    )
                }
                backup
            }
        }

    /** Wipes every VEX MUSIC record. Audio files on the device are never deleted. */
    suspend fun clearAllAppData() = withContext(Dispatchers.IO) {
        db.historyDao().clear()
        db.searchHistoryDao().clear()
        lyricsRepository.clearLyricsCache()
        lyricsRepository.clearTranslationCache()
        db.playlistDao().allPlaylists().forEach {
            db.playlistDao().clearItems(it.id)
            db.playlistDao().delete(it.id)
        }
        musicRepository.deleteAll()
        settingsRepository.clearAll()
    }
}

private fun TrackEntity.toBackup() = BackupTrack(
    id, uri, title, artist, album, durationMs, mimeType, sizeBytes, year, trackNumber,
    dateAdded, isFavorite, playCount, lastPlayedAt, lastPositionMs, matchedProvider,
    matchedProviderId, originalTitle, originalArtist, originalAlbum, versionInfo, matchedAt
)

private fun LyricsEntity.toBackup() = BackupLyrics(
    trackId, providerId, providerName, resultId, content, format, syncType, language,
    userSelected, userEdited, offsetMs, updatedAt
)

private fun TranslationEntity.toBackup() = BackupTranslation(
    trackId, targetLanguage, sourceHash, providerName, translatedLines, updatedAt
)


