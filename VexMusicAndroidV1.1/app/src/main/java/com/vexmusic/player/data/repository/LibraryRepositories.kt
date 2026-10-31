package com.vexmusic.player.data.repository

import com.vexmusic.player.data.database.HistoryDao
import com.vexmusic.player.data.database.HistoryEntity
import com.vexmusic.player.data.database.PlaylistDao
import com.vexmusic.player.data.database.PlaylistEntity
import com.vexmusic.player.data.database.PlaylistTrackEntity
import com.vexmusic.player.data.database.SearchHistoryDao
import com.vexmusic.player.data.database.SearchHistoryEntity
import com.vexmusic.player.data.database.TrackDao
import com.vexmusic.player.domain.model.HistoryStat
import com.vexmusic.player.domain.model.Playlist
import com.vexmusic.player.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlaylistRepository(
    private val dao: PlaylistDao,
    private val trackDao: TrackDao
) {
    val playlists: Flow<List<Playlist>> = combine(
        dao.observePlaylists(),
        trackDao.observeAll()
    ) { lists, _ -> lists }.map { entities ->
        entities.map { e ->
            Playlist(e.id, e.name, e.createdAt, dao.trackCount(e.id), e.artworkPath)
        }
    }

    fun observeTracks(playlistId: Long): Flow<List<Track>> =
        combine(dao.observeItems(playlistId), trackDao.observeAll()) { items, tracks ->
            val byId = tracks.associateBy { it.id }
            items.sortedBy { it.position }.mapNotNull { byId[it.trackId]?.toDomain() }
        }

    suspend fun create(name: String): Long =
        dao.insert(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    suspend fun delete(id: Long) {
        dao.clearItems(id)
        dao.delete(id)
    }

    suspend fun addTracks(playlistId: Long, trackIds: List<String>) {
        val current = dao.itemsOnce(playlistId)
        val existing = current.map { it.trackId }.toSet()
        val now = System.currentTimeMillis()
        var pos = current.size
        val additions = trackIds.filterNot { existing.contains(it) }.map {
            PlaylistTrackEntity(playlistId, it, pos++, now)
        }
        if (additions.isNotEmpty()) dao.insertItems(additions)
    }

    suspend fun removeTrack(playlistId: Long, trackId: String) {
        dao.removeItem(playlistId, trackId)
        normalise(playlistId)
    }

    suspend fun reorder(playlistId: Long, orderedTrackIds: List<String>) =
        dao.replaceItems(playlistId, orderedTrackIds)

    suspend fun get(id: Long): Playlist? = dao.getPlaylist(id)?.let {
        Playlist(it.id, it.name, it.createdAt, dao.trackCount(it.id), it.artworkPath)
    }

    suspend fun count(): Int = dao.playlistCount()

    private suspend fun normalise(playlistId: Long) {
        val items = dao.itemsOnce(playlistId).sortedBy { it.position }
        dao.replaceItems(playlistId, items.map { it.trackId })
    }
}

class HistoryRepository(
    private val dao: HistoryDao,
    private val searchDao: SearchHistoryDao
) {
    fun recent(limit: Int = 100): Flow<List<HistoryEntity>> = dao.observeRecent(limit)

    val totalListenedMs: Flow<Long> = dao.observeTotalListened()

    fun topArtists(limit: Int = 10): Flow<List<HistoryStat>> =
        dao.observeTopArtists(limit).map { rows -> rows.map { HistoryStat(it.statKey, it.statCount, it.totalMs) } }

    fun topTracks(limit: Int = 10): Flow<List<HistoryStat>> =
        dao.observeTopTracks(limit).map { rows -> rows.map { HistoryStat(it.statKey, it.statCount, it.totalMs) } }

    suspend fun record(track: Track, listenedMs: Long) {
        dao.insert(
            HistoryEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                playedAt = System.currentTimeMillis(),
                listenedMs = listenedMs.coerceAtLeast(0)
            )
        )
    }

    suspend fun clear() = dao.clear()
    suspend fun count(): Int = dao.count()

    val searchHistory: Flow<List<SearchHistoryEntity>> = searchDao.observe()

    suspend fun recordSearch(query: String) {
        if (query.isBlank()) return
        searchDao.insert(SearchHistoryEntity(query.trim(), System.currentTimeMillis()))
    }

    suspend fun clearSearchHistory() = searchDao.clear()
}
