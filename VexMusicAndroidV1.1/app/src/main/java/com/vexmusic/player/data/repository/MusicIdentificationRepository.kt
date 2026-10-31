package com.vexmusic.player.data.repository

import com.vexmusic.player.domain.model.RecordingCandidate
import com.vexmusic.player.lyrics.provider.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

/** Search-only MusicBrainz adapter. Results are never selected without an explicit user action. */
class MusicIdentificationRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val requestMutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun search(
        title: String,
        artist: String,
        durationMs: Long
    ): List<RecordingCandidate> = requestMutex.withLock {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        require(cleanTitle.isNotBlank() || cleanArtist.isNotBlank()) {
            "Enter a title or artist before searching."
        }

        val remaining = 1_000L - (System.currentTimeMillis() - lastRequestAt)
        if (lastRequestAt > 0 && remaining > 0) delay(remaining)

        val clauses = buildList {
            if (cleanTitle.isNotBlank()) add("recording:${lucenePhrase(cleanTitle)}")
            if (cleanArtist.isNotBlank()) add("artist:${lucenePhrase(cleanArtist)}")
        }
        val encodedQuery = URLEncoder.encode(clauses.joinToString(" AND "), "UTF-8")
        val url = "https://musicbrainz.org/ws/2/recording?query=$encodedQuery&fmt=json&limit=15"
        val body = withContext(Dispatchers.IO) {
            Http.get(
                url,
                mapOf("User-Agent" to "VEX-MUSIC/1.1.0 (https://github.com/Geko7490/VexMusicAndroidV1.1)")
            )
        }
        lastRequestAt = System.currentTimeMillis()
        if (body.isBlank()) return@withLock emptyList()

        val recordings = runCatching {
            json.parseToJsonElement(body).jsonObject["recordings"]?.jsonArray.orEmpty()
        }.getOrElse { throw IllegalStateException("MusicBrainz returned an unreadable response", it) }

        recordings.mapNotNull { element ->
            val item = element.jsonObject
            val id = item.text("id") ?: return@mapNotNull null
            val candidateTitle = item.text("title") ?: return@mapNotNull null
            val candidateArtist = parseArtistCredit(item["artist-credit"]?.jsonArray.orEmpty())
            val candidateDuration = item["length"]?.jsonPrimitive?.longOrNull
            val album = item["releases"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.text("title")
            val disambiguation = item.text("disambiguation")?.takeIf { it.isNotBlank() }
            val (confidence, reason) = RecordingMatchScorer.score(
                queryTitle = cleanTitle,
                queryArtist = cleanArtist,
                queryDurationMs = durationMs,
                candidateTitle = candidateTitle,
                candidateArtist = candidateArtist,
                candidateDurationMs = candidateDuration,
                candidateVersion = disambiguation
            )
            RecordingCandidate(
                providerId = id,
                providerName = "MusicBrainz",
                title = candidateTitle,
                artist = candidateArtist.ifBlank { "Unknown artist" },
                album = album,
                durationMs = candidateDuration,
                versionInfo = disambiguation,
                confidence = confidence,
                reason = reason
            )
        }.sortedByDescending { it.confidence }
    }

    private fun lucenePhrase(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun parseArtistCredit(credit: JsonArray): String = credit.mapNotNull { element ->
        val item = element.jsonObject
        val name = item.text("name") ?: item["artist"]?.jsonObject?.text("name")
            ?: return@mapNotNull null
        name + item.text("joinphrase").orEmpty()
    }.joinToString("").trim()

    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}

/** A transparent metadata score with explicit duration and version warnings. */
object RecordingMatchScorer {
    private val versionMarkers = listOf(
        "remix", "live", "instrumental", "acoustic", "slowed", "speed up", "sped up",
        "nightcore", "extended", "radio edit", "remaster", "demo", "karaoke", "cover"
    )

    private fun normalise(value: String): String = value.lowercase()
        .replace(Regex("\\((feat|ft|with)[^)]*\\)"), " ")
        .replace(Regex("\\[[^]]*]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun similarity(left: String, right: String): Double {
        val a = normalise(left).split(" ").filter { it.isNotBlank() }.toSet()
        val b = normalise(right).split(" ").filter { it.isNotBlank() }.toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / maxOf(a.size, b.size)
    }

    private fun versions(value: String): Set<String> {
        val normalised = normalise(value)
        return versionMarkers.filterTo(mutableSetOf()) { marker ->
            Regex("(^|\\s)${Regex.escape(marker)}($|\\s)").containsMatchIn(normalised)
        }
    }

    fun score(
        queryTitle: String,
        queryArtist: String,
        queryDurationMs: Long,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationMs: Long?,
        candidateVersion: String?
    ): Pair<Int, String> {
        val details = mutableListOf<String>()
        var weightedScore = 0.0
        var weight = 0.0
        if (queryTitle.isNotBlank()) {
            val value = similarity(queryTitle, candidateTitle)
            weightedScore += value * 55
            weight += 55
            details += "title ${(value * 100).toInt()}%"
        }
        if (queryArtist.isNotBlank()) {
            val value = similarity(queryArtist, candidateArtist)
            weightedScore += value * 35
            weight += 35
            details += "artist ${(value * 100).toInt()}%"
        }
        if (candidateDurationMs != null && queryDurationMs > 0) {
            val delta = kotlin.math.abs(candidateDurationMs - queryDurationMs)
            val value = when {
                delta <= 1_500 -> 1.0
                delta <= 4_000 -> 0.75
                delta <= 10_000 -> 0.4
                else -> 0.0
            }
            weightedScore += value * 10
            weight += 10
            details += "duration ±${delta / 1000}s"
        } else {
            details += "duration unknown"
        }

        val requestedVersions = versions("$queryTitle $queryArtist")
        val candidateVersions = versions("$candidateTitle ${candidateVersion.orEmpty()}")
        val conflicts = requestedVersions.symmetricDifference(candidateVersions)
        val versionPenalty = if (conflicts.isEmpty()) 0 else 18
        if (conflicts.isNotEmpty()) details += "version differs: ${conflicts.joinToString()}"

        val confidence = if (weight == 0.0) 0 else
            ((weightedScore / weight) * 100).toInt().minus(versionPenalty).coerceIn(0, 100)
        return confidence to details.joinToString(", ")
    }

    private fun <T> Set<T>.symmetricDifference(other: Set<T>): Set<T> =
        (this - other) + (other - this)
}