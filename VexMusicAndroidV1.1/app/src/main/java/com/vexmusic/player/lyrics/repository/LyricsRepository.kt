package com.vexmusic.player.lyrics.repository

import com.vexmusic.player.data.database.LyricsDao
import com.vexmusic.player.data.database.LyricsEntity
import com.vexmusic.player.data.database.TranslationDao
import com.vexmusic.player.data.database.TranslationEntity
import com.vexmusic.player.domain.model.LyricsFormat
import com.vexmusic.player.domain.model.LyricsSearchResult
import com.vexmusic.player.domain.model.ParsedLyrics
import com.vexmusic.player.domain.model.SyncType
import com.vexmusic.player.domain.model.Track
import com.vexmusic.player.lyrics.parser.LyricsParser
import com.vexmusic.player.lyrics.provider.LyricsProvider
import com.vexmusic.player.lyrics.provider.LyricsQuery
import com.vexmusic.player.lyrics.translation.LanguageDetector
import com.vexmusic.player.lyrics.translation.TranslationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class ProviderFailure(val providerId: String, val providerName: String, val reason: String)

data class MultiSearchOutcome(
    val results: List<LyricsSearchResult> = emptyList(),
    val failures: List<ProviderFailure> = emptyList(),
    val emptyProviders: List<String> = emptyList(),
    val searchedAt: Long = System.currentTimeMillis()
)

data class SavedLyrics(
    val trackId: String,
    val providerId: String,
    val providerName: String,
    val resultId: String,
    val content: String,
    val format: LyricsFormat,
    val syncType: SyncType,
    val language: String?,
    val userSelected: Boolean,
    val userEdited: Boolean,
    val offsetMs: Long,
    val updatedAt: Long
) {
    fun parse(): ParsedLyrics = LyricsParser.parse(content, format, language)
}

/**
 * Multi-provider lyrics engine.
 *
 * Nothing is auto-applied: [searchAll] only *offers* results. A result becomes the
 * active lyrics for a track exclusively through [selectResult], which persists the
 * user's choice so later playbacks reuse it without hitting the network.
 */
class LyricsRepository(
    private val lyricsDao: LyricsDao,
    private val translationDao: TranslationDao,
    private val providers: List<LyricsProvider>,
    private val translationProvider: TranslationProvider
) {
    fun allProviders(): List<LyricsProvider> = providers

    fun providerName(id: String): String =
        providers.firstOrNull { it.id == id }?.displayName ?: id

    suspend fun getSaved(trackId: String): SavedLyrics? = lyricsDao.get(trackId)?.toSaved()

    suspend fun searchAll(track: Track, enabledProviderIds: Set<String>): MultiSearchOutcome =
        coroutineScope {
            val query = LyricsQuery(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs
            )
            val active = providers.filter { enabledProviderIds.contains(it.id) }
            val jobs = active.map { provider ->
                async(Dispatchers.IO) {
                    try {
                        provider to Result.success(provider.search(query))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        provider to Result.failure<List<LyricsSearchResult>>(t)
                    }
                }
            }
            val results = mutableListOf<LyricsSearchResult>()
            val failures = mutableListOf<ProviderFailure>()
            val empties = mutableListOf<String>()
            jobs.awaitAll().forEach { (provider, outcome) ->
                outcome.fold(
                    onSuccess = { list ->
                        if (list.isEmpty()) empties += provider.displayName else results += list
                    },
                    onFailure = { error ->
                        failures += ProviderFailure(
                            provider.id,
                            provider.displayName,
                            error.message ?: error::class.java.simpleName
                        )
                    }
                )
            }
            // Rank, but never silently discard similar alternatives.
            val ranked = results.sortedWith(
                compareByDescending<LyricsSearchResult> { it.matchScore ?: 0 }
                    .thenByDescending { it.syncType.ordinal }
            )
            MultiSearchOutcome(ranked, failures, empties)
        }

    /** Persists the user's explicit choice for this track. */
    suspend fun selectResult(trackId: String, result: LyricsSearchResult): SavedLyrics {
        val entity = LyricsEntity(
            trackId = trackId,
            providerId = result.providerId,
            providerName = result.providerName,
            resultId = result.resultId,
            content = result.content,
            format = result.format.name,
            syncType = result.syncType.name,
            language = result.language ?: LanguageDetector.detect(result.content),
            userSelected = true,
            userEdited = false,
            offsetMs = lyricsDao.get(trackId)?.offsetMs ?: 0L,
            updatedAt = System.currentTimeMillis()
        )
        lyricsDao.upsert(entity)
        translationDao.deleteForTrack(trackId)
        return entity.toSaved()
    }

    /** Imports a user supplied .lrc / .txt file or manually typed lyrics. */
    suspend fun saveManualLyrics(
        trackId: String,
        content: String,
        sourceLabel: String,
        userEdited: Boolean = true
    ): SavedLyrics {
        val format = LyricsParser.detectFormat(content)
        val entity = LyricsEntity(
            trackId = trackId,
            providerId = "manual",
            providerName = sourceLabel,
            resultId = "manual-${System.currentTimeMillis()}",
            content = content,
            format = format.name,
            syncType = LyricsParser.detectSyncType(content).name,
            language = LanguageDetector.detect(content),
            userSelected = true,
            userEdited = userEdited,
            offsetMs = lyricsDao.get(trackId)?.offsetMs ?: 0L,
            updatedAt = System.currentTimeMillis()
        )
        lyricsDao.upsert(entity)
        translationDao.deleteForTrack(trackId)
        return entity.toSaved()
    }

    suspend fun setOffset(trackId: String, offsetMs: Long) {
        lyricsDao.get(trackId)?.let { lyricsDao.upsert(it.copy(offsetMs = offsetMs)) }
    }

    /** Returns to automatic search by forgetting the stored selection for this track. */
    suspend fun clearSelection(trackId: String) {
        lyricsDao.delete(trackId)
        translationDao.deleteForTrack(trackId)
    }

    suspend fun clearLyricsCache() = lyricsDao.deleteAll()
    suspend fun clearTranslationCache() = translationDao.deleteAll()
    suspend fun lyricsCacheBytes(): Long = lyricsDao.totalChars() * 2
    suspend fun translationCacheBytes(): Long = translationDao.totalChars() * 2
    suspend fun lyricsCount(): Int = lyricsDao.count()
    suspend fun translationCount(): Int = translationDao.count()

    suspend fun cachedTranslation(trackId: String, target: String, sourceHash: Int): List<String>? {
        val row = translationDao.get(trackId, target) ?: return null
        if (row.sourceHash != sourceHash) return null
        return row.translatedLines.split("\n")
    }

    /**
     * Translates line by line preserving order and timestamps. The original lyrics
     * are never overwritten — translations live in their own table.
     */
    suspend fun translate(
        trackId: String,
        lines: List<String>,
        targetLanguage: String,
        sourceLanguage: String?,
        force: Boolean = false
    ): List<String> = withContext(Dispatchers.IO) {
        val hash = lines.joinToString("\n").hashCode()
        if (!force) cachedTranslation(trackId, targetLanguage, hash)?.let { return@withContext it }
        val translated = translationProvider.translate(lines, targetLanguage, sourceLanguage)
        translationDao.upsert(
            TranslationEntity(
                trackId = trackId,
                targetLanguage = targetLanguage,
                sourceHash = hash,
                providerName = translationProvider.name,
                translatedLines = translated.joinToString("\n") { it.replace("\n", " ") },
                updatedAt = System.currentTimeMillis()
            )
        )
        translated
    }

    suspend fun exportAll(): Pair<List<LyricsEntity>, List<TranslationEntity>> =
        lyricsDao.all() to translationDao.all()

    suspend fun restore(lyrics: List<LyricsEntity>, translations: List<TranslationEntity>) {
        lyrics.forEach { lyricsDao.upsert(it) }
        translations.forEach { translationDao.upsert(it) }
    }
}

private fun LyricsEntity.toSaved() = SavedLyrics(
    trackId = trackId,
    providerId = providerId,
    providerName = providerName,
    resultId = resultId,
    content = content,
    format = runCatching { LyricsFormat.valueOf(format) }.getOrDefault(LyricsFormat.PLAIN),
    syncType = runCatching { SyncType.valueOf(syncType) }.getOrDefault(SyncType.PLAIN),
    language = language,
    userSelected = userSelected,
    userEdited = userEdited,
    offsetMs = offsetMs,
    updatedAt = updatedAt
)
