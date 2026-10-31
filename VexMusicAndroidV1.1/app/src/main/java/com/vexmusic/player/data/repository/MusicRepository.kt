package com.vexmusic.player.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.vexmusic.player.data.database.TrackDao
import com.vexmusic.player.data.database.TrackEntity
import com.vexmusic.player.domain.model.SortOrder
import com.vexmusic.player.domain.model.RecordingCandidate
import com.vexmusic.player.domain.model.Track
import com.vexmusic.player.util.Id3LyricsExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class ImportResult(
    val imported: Int,
    val duplicates: Int,
    val failed: Int,
    val failures: List<String> = emptyList()
)

/**
 * Owns the local music library. Audio files are never copied to a server and never
 * converted to Base64 — only a stable content URI reference plus metadata is stored.
 */
class MusicRepository(
    private val context: Context,
    private val trackDao: TrackDao
) {
    private val artworkDir: File by lazy {
        File(context.filesDir, "artwork").apply { mkdirs() }
    }

    val tracks: Flow<List<Track>> = trackDao.observeAll().map { list -> list.map { it.toDomain() } }
    val favorites: Flow<List<Track>> = trackDao.observeFavorites().map { l -> l.map { it.toDomain() } }

    fun recentlyPlayed(limit: Int = 20): Flow<List<Track>> =
        trackDao.observeRecentlyPlayed(limit).map { l -> l.mapNotNull { it.takeIf { t -> t.lastPlayedAt != null }?.toDomain() } }

    fun mostPlayed(limit: Int = 20): Flow<List<Track>> =
        trackDao.observeMostPlayed(limit).map { l -> l.map { it.toDomain() } }

    fun search(query: String): Flow<List<Track>> =
        trackDao.search(query).map { l -> l.map { it.toDomain() } }

    suspend fun getTrack(id: String): Track? = trackDao.getById(id)?.toDomain()

    suspend fun getTracks(ids: List<String>): List<Track> =
        trackDao.getByIds(ids).map { it.toDomain() }

    suspend fun allTracksOnce(): List<Track> = trackDao.getAllOnce().map { it.toDomain() }

    suspend fun setFavorite(id: String, fav: Boolean) = trackDao.setFavorite(id, fav)

    suspend fun markPlayed(id: String) = trackDao.markPlayed(id, System.currentTimeMillis())

    suspend fun savePosition(id: String, positionMs: Long) = trackDao.savePosition(id, positionMs)

    suspend fun delete(ids: List<String>) = withContext(Dispatchers.IO) {
        ids.forEach { File(artworkDir, "$it.jpg").delete() }
        trackDao.deleteByIds(ids)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        artworkDir.listFiles()?.forEach { it.delete() }
        trackDao.deleteAll()
    }

    suspend fun updateMetadata(track: Track) = withContext(Dispatchers.IO) {
        trackDao.getById(track.id)?.let { existing ->
            trackDao.update(
                existing.copy(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    year = track.year,
                    trackNumber = track.trackNumber
                )
            )
        }
    }

    /** Applies only a user-confirmed identity; the audio file and embedded tags are untouched. */
    suspend fun applyConfirmedMatch(trackId: String, candidate: RecordingCandidate) =
        withContext(Dispatchers.IO) {
            trackDao.getById(trackId)?.let { existing ->
                trackDao.update(
                    existing.copy(
                        title = candidate.title,
                        artist = candidate.artist,
                        album = candidate.album ?: existing.album,
                        matchedProvider = candidate.providerName,
                        matchedProviderId = candidate.providerId,
                        originalTitle = existing.originalTitle ?: existing.title,
                        originalArtist = existing.originalArtist ?: existing.artist,
                        originalAlbum = existing.originalAlbum ?: existing.album,
                        versionInfo = candidate.versionInfo,
                        matchedAt = System.currentTimeMillis()
                    )
                )
            }
        }

    /** Removes the remote match and restores the metadata captured before the first match. */
    suspend fun restoreOriginalMetadata(trackId: String) = withContext(Dispatchers.IO) {
        trackDao.getById(trackId)?.let { existing ->
            if (existing.matchedProvider != null) {
                trackDao.update(
                    existing.copy(
                        title = existing.originalTitle ?: existing.title,
                        artist = existing.originalArtist ?: existing.artist,
                        album = existing.originalAlbum ?: existing.album,
                        matchedProvider = null,
                        matchedProviderId = null,
                        originalTitle = null,
                        originalArtist = null,
                        originalAlbum = null,
                        versionInfo = null,
                        matchedAt = null
                    )
                )
            }
        }
    }

    suspend fun libraryBytes(): Long = trackDao.totalBytes()
    suspend fun trackCount(): Int = trackDao.count()

    suspend fun artworkBytes(): Long = withContext(Dispatchers.IO) {
        artworkDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /** Imports one or more audio files chosen through the Storage Access Framework. */
    suspend fun importFromUris(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var duplicates = 0
        val failures = mutableListOf<String>()
        val newTracks = mutableListOf<TrackEntity>()
        val existing = trackDao.getAllOnce()
        val knownUris = existing.mapTo(mutableSetOf()) { it.uri }
        val durationsByMetadata = HashMap<String, MutableList<Long>>()
        existing.forEach { track ->
            durationsByMetadata.getOrPut(metadataKey(track.title, track.artist)) { mutableListOf() }
                .add(track.durationMs)
        }

        for (uri in uris) {
            try {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                if (!knownUris.add(uri.toString())) {
                    duplicates++
                    continue
                }
                val entity = readMetadata(uri)
                if (entity == null) {
                    failures += uri.lastPathSegment ?: uri.toString()
                    continue
                }
                val key = metadataKey(entity.title, entity.artist)
                val fuzzyDuplicate = durationsByMetadata[key]
                    ?.any { kotlin.math.abs(it - entity.durationMs) < 2000 } == true
                if (fuzzyDuplicate) {
                    duplicates++
                    continue
                }
                newTracks += entity
                durationsByMetadata.getOrPut(key) { mutableListOf() }.add(entity.durationMs)
                imported++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                failures += (uri.lastPathSegment ?: uri.toString()) + ": " + (t.message ?: "error")
            }
        }
        if (newTracks.isNotEmpty()) trackDao.upsertAll(newTracks)
        ImportResult(imported, duplicates, failures.size, failures)
    }

    /** Optional MediaStore scan for audio already indexed by Android. */
    suspend fun scanDeviceAudio(): ImportResult = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val uris = mutableListOf<Uri>()
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )
            val idCol = cursor?.getColumnIndex(MediaStore.Audio.Media._ID) ?: -1
            while (cursor != null && cursor.moveToNext() && idCol >= 0) {
                val id = cursor.getLong(idCol)
                uris += Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        } catch (t: Throwable) {
            return@withContext ImportResult(0, 0, 1, listOf(t.message ?: "MediaStore query failed"))
        } finally {
            cursor?.close()
        }
        importFromUris(uris)
    }

    /** Reads any embedded ID3 (USLT/SYLT) lyrics from the audio file itself. */
    suspend fun readEmbeddedLyrics(track: Track): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(track.uri.toUri())?.use { input ->
                Id3LyricsExtractor.extract(input)
            }
        }.getOrNull()
    }

    private fun readMetadata(uri: Uri): TrackEntity? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val displayName = queryDisplayName(context.contentResolver, uri)
            val size = querySize(context.contentResolver, uri)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val id = stableId(uri.toString())
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: displayName?.substringBeforeLast('.')
                ?: "Unknown title"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Unknown artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Unknown album"
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: context.contentResolver.getType(uri) ?: "audio/*"
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val trackNo = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()
            val artworkPath = retriever.embeddedPicture?.let { bytes ->
                val f = File(artworkDir, "$id.jpg")
                f.writeBytes(bytes)
                f.absolutePath
            }
            if (duration <= 0L) return null
            TrackEntity(
                id = id,
                uri = uri.toString(),
                title = title.trim(),
                artist = artist.trim(),
                album = album.trim(),
                durationMs = duration,
                mimeType = mime,
                sizeBytes = size,
                year = year,
                trackNumber = trackNo,
                artworkPath = artworkPath,
                dateAdded = System.currentTimeMillis()
            )
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? = runCatching {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun querySize(cr: ContentResolver, uri: Uri): Long = runCatching {
        cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private fun metadataKey(title: String, artist: String): String =
        title.trim().lowercase(Locale.ROOT) + "\u0000" + artist.trim().lowercase(Locale.ROOT)

    companion object {
        fun stableId(value: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun sort(list: List<Track>, order: SortOrder): List<Track> = when (order) {
            SortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> list.sortedBy { it.artist.lowercase() }
            SortOrder.ALBUM -> list.sortedBy { it.album.lowercase() }
            SortOrder.DURATION -> list.sortedBy { it.durationMs }
            SortOrder.RECENTLY_ADDED -> list.sortedByDescending { it.dateAdded }
        }
    }
}
