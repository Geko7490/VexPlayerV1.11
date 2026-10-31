package com.vexmusic.player.lyrics.provider

import com.vexmusic.player.domain.model.LyricsSearchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LyricsQuery(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L
)

/** Thrown when a provider is reachable but cannot serve results for a documented reason. */
class ProviderUnavailableException(message: String) : Exception(message)

interface LyricsProvider {
    val id: String
    val displayName: String
    val requiresNetwork: Boolean get() = true
    /** Returns normalised results. Throws on transport/provider errors so the UI can explain. */
    suspend fun search(query: LyricsQuery): List<LyricsSearchResult>
}

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    const val USER_AGENT = "VexMusic/1.0.0 (Android; https://github.com/vexmusic/vex-music)"

    @Throws(Exception::class)
    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 404) return ""
            if (response.code == 429) throw ProviderUnavailableException("Rate limited (HTTP 429)")
            if (!response.isSuccessful) throw ProviderUnavailableException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }
}

/**
 * Relevance ranking. Documented method: normalised token overlap of title and artist
 * (Jaccard-style), plus a duration proximity bonus when both durations are known.
 * No invented "AI confidence" numbers are used.
 */
object MatchScorer {

    fun normalise(value: String): String = value.lowercase()
        .replace(Regex("\\((feat|ft|with)[^)]*\\)"), " ")
        .replace(Regex("\\[[^]]*]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun tokens(value: String) = normalise(value).split(" ").filter { it.isNotBlank() }.toSet()

    private fun overlap(a: String, b: String): Double {
        val ta = tokens(a); val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size.toDouble()
        return inter / maxOf(ta.size, tb.size)
    }

    /** 0..100 relevance together with a human readable explanation of how it was derived. */
    fun score(
        query: LyricsQuery,
        title: String,
        artist: String,
        durationMs: Long?
    ): Pair<Int, String> {
        val titleScore = overlap(query.title, title)
        val artistScore = overlap(query.artist, artist)
        var score = (titleScore * 55) + (artistScore * 30)
        val reasons = mutableListOf(
            "title ${(titleScore * 100).toInt()}%",
            "artist ${(artistScore * 100).toInt()}%"
        )
        if (durationMs != null && durationMs > 0 && query.durationMs > 0) {
            val delta = kotlin.math.abs(durationMs - query.durationMs)
            val bonus = when {
                delta <= 1500 -> 15.0
                delta <= 4000 -> 10.0
                delta <= 10000 -> 4.0
                else -> 0.0
            }
            score += bonus
            reasons += "duration ±${delta / 1000}s"
        } else {
            reasons += "duration unknown"
        }
        return score.toInt().coerceIn(0, 100) to reasons.joinToString(", ")
    }
}
