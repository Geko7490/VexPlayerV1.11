package com.vexmusic.player.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks")
    suspend fun getAllOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): TrackEntity?

    @Query(
        "SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' " +
            "OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%' " +
            "ORDER BY title COLLATE NOCASE ASC"
    )
    fun search(q: String): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Update
    suspend fun update(track: TrackEntity)

    @Query("UPDATE tracks SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :at WHERE id = :id")
    suspend fun markPlayed(id: String, at: Long)

    @Query("UPDATE tracks SET lastPositionMs = :pos WHERE id = :id")
    suspend fun savePosition(id: String, pos: Long)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM tracks")
    suspend fun totalBytes(): Long
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :id")
    suspend fun trackCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun playlistCount(): Int

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearItems(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeItem(playlistId: Long, trackId: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position ASC")
    fun observeItems(id: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position ASC")
    suspend fun itemsOnce(id: Long): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlist_tracks")
    suspend fun allItems(): List<PlaylistTrackEntity>

    @Query("SELECT * FROM playlists")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Transaction
    suspend fun replaceItems(playlistId: Long, trackIds: List<String>) {
        clearItems(playlistId)
        val now = System.currentTimeMillis()
        insertItems(trackIds.mapIndexed { i, t -> PlaylistTrackEntity(playlistId, t, i, now) })
    }
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun get(trackId: String): LyricsEntity?

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    fun observe(trackId: String): Flow<LyricsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM lyrics")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM lyrics")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(content)), 0) FROM lyrics")
    suspend fun totalChars(): Long

    @Query("SELECT * FROM lyrics")
    suspend fun all(): List<LyricsEntity>
}

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations WHERE trackId = :trackId AND targetLanguage = :lang")
    suspend fun get(trackId: String, lang: String): TranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TranslationEntity)

    @Query("DELETE FROM translations WHERE trackId = :trackId")
    suspend fun deleteForTrack(trackId: String)

    @Query("DELETE FROM translations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM translations")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(translatedLines)), 0) FROM translations")
    suspend fun totalChars(): Long

    @Query("SELECT * FROM translations")
    suspend fun all(): List<TranslationEntity>
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history")
    suspend fun all(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HistoryEntity>)

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM history")
    fun observeTotalListened(): Flow<Long>

    @Query("SELECT artist AS statKey, COUNT(*) AS statCount, COALESCE(SUM(listenedMs),0) AS totalMs FROM history GROUP BY artist ORDER BY statCount DESC LIMIT :limit")
    fun observeTopArtists(limit: Int): Flow<List<StatRow>>

    @Query("SELECT title AS statKey, COUNT(*) AS statCount, COALESCE(SUM(listenedMs),0) AS totalMs FROM history GROUP BY trackId ORDER BY statCount DESC LIMIT :limit")
    fun observeTopTracks(limit: Int): Flow<List<StatRow>>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}

data class StatRow(val statKey: String, val statCount: Int, val totalMs: Long)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 12")
    fun observe(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    @Delete
    suspend fun delete(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
