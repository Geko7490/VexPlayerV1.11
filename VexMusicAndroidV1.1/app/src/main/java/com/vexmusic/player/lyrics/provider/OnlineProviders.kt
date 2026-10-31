package com.vexmusic.player.lyrics.provider

import android.util.Base64
import com.vexmusic.player.domain.model.LyricsFormat
import com.vexmusic.player.domain.model.LyricsSearchResult
import com.vexmusic.player.domain.model.SyncType
import com.vexmusic.player.lyrics.parser.LyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.num(key: String): Double? = runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()

/**
 * LRCLIB — primary provider. Open, documented and key-free: https://lrclib.net/docs
 * Only track metadata is sent; audio never leaves the device.
 */
class LrclibProvider : LyricsProvider {
    override val id = "lrclib"
    override val displayName = "LRCLIB"

    override suspend fun search(query: LyricsQuery): List<LyricsSearchResult> =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("https://lrclib.net/api/search?track_name=").append(enc(query.title))
                if (query.artist.isNotBlank() && !query.artist.startsWith("Unknown")) {
                    append("&artist_name=").append(enc(query.artist))
                }
            }
            var body = Http.get(url)
            if (body.isBlank() || body == "[]") {
                // Fall back to a looser free-text query.
                body = Http.get("https://lrclib.net/api/search?q=" + enc("${query.artist} ${query.title}"))
            }
            if (body.isBlank()) return@withContext emptyList()
            val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull()
                ?: return@withContext emptyList()
            buildResults(arr, query)
        }

    private fun buildResults(arr: JsonArray, query: LyricsQuery): List<LyricsSearchResult> {
        val out = mutableListOf<LyricsSearchResult>()
        arr.take(8).forEach { element ->
            val o = element.jsonObject
            val title = o.str("trackName") ?: o.str("name") ?: return@forEach
            val artist = o.str("artistName").orEmpty()
            val album = o.str("albumName")
            val durationMs = (o.num("duration") ?: 0.0).times(1000).toLong().takeIf { it > 0 }
            val resultId = o["id"]?.jsonPrimitive?.intOrNull?.toString() ?: "$title-$artist"
            val (score, reason) = MatchScorer.score(query, title, artist, durationMs)
            val synced = o.str("syncedLyrics")?.takeIf { it.isNotBlank() }
            val plain = o.str("plainLyrics")?.takeIf { it.isNotBlank() }
            if (synced != null) {
                val format = LyricsParser.detectFormat(synced)
                out += LyricsSearchResult(
                    providerId = id, providerName = displayName, resultId = resultId,
                    title = title, artist = artist, album = album, durationMs = durationMs,
                    language = null,
                    syncType = if (format == LyricsFormat.ENHANCED_LRC) SyncType.WORD_SYNCED else SyncType.LINE_SYNCED,
                    format = format, content = synced, matchScore = score, matchReason = reason
                )
            }
            if (plain != null) {
                out += LyricsSearchResult(
                    providerId = id, providerName = displayName, resultId = "$resultId-plain",
                    title = title, artist = artist, album = album, durationMs = durationMs,
                    language = null, syncType = SyncType.PLAIN, format = LyricsFormat.PLAIN,
                    content = plain, matchScore = (score - 5).coerceAtLeast(0),
                    matchReason = "$reason, plain text"
                )
            }
        }
        return out
    }
}

/**
 * KuGou public lyrics search (krcs.kugou.com + lyrics.kugou.com). No authentication is
 * bypassed: these are the open endpoints the KuGou web player itself calls.
 */
class KugouProvider : LyricsProvider {
    override val id = "kugou"
    override val displayName = "KuGou"

    override suspend fun search(query: LyricsQuery): List<LyricsSearchResult> =
        withContext(Dispatchers.IO) {
            val keyword = "${query.artist} ${query.title}".trim()
            val searchUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=" +
                enc(keyword) + (if (query.durationMs > 0) "&duration=${query.durationMs}" else "") + "&hash="
            val body = Http.get(searchUrl)
            if (body.isBlank()) return@withContext emptyList()
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return@withContext emptyList()
            val candidates = root["candidates"]?.jsonArray ?: return@withContext emptyList()
            val out = mutableListOf<LyricsSearchResult>()
            candidates.take(4).forEach { element ->
                val c = element.jsonObject
                val cid = c.str("id") ?: return@forEach
                val key = c.str("accesskey") ?: return@forEach
                val song = c.str("song") ?: query.title
                val singer = c.str("singer") ?: query.artist
                val duration = c.num("duration")?.toLong()
                val lyricBody = runCatching {
                    Http.get(
                        "https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8" +
                            "&id=$cid&accesskey=$key"
                    )
                }.getOrNull() ?: return@forEach
                val lyricJson = runCatching { json.parseToJsonElement(lyricBody).jsonObject }.getOrNull()
                    ?: return@forEach
                val encoded = lyricJson.str("content") ?: return@forEach
                val decoded = runCatching {
                    String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
                val format = LyricsParser.detectFormat(decoded)
                val (score, reason) = MatchScorer.score(query, song, singer, duration)
                out += LyricsSearchResult(
                    providerId = id, providerName = displayName, resultId = cid,
                    title = song, artist = singer, album = null, durationMs = duration,
                    language = null, syncType = LyricsParser.detectSyncType(decoded),
                    format = format, content = decoded, matchScore = score, matchReason = reason
                )
            }
            out
        }
}

/** NetEase Cloud Music public lyric API — synced LRC plus community translations. */
class NetEaseProvider : LyricsProvider {
    override val id = "netease"
    override val displayName = "NetEase"

    private val headers = mapOf(
        "Referer" to "https://music.163.com",
        "Cookie" to "appver=2.0.2"
    )

    override suspend fun search(query: LyricsQuery): List<LyricsSearchResult> =
        withContext(Dispatchers.IO) {
            val keyword = "${query.artist} ${query.title}".trim()
            val body = Http.get(
                "https://music.163.com/api/search/get?type=1&limit=5&s=" + enc(keyword), headers
            )
            if (body.isBlank()) return@withContext emptyList()
            val songs = runCatching {
                json.parseToJsonElement(body).jsonObject["result"]?.jsonObject
                    ?.get("songs")?.jsonArray
            }.getOrNull() ?: return@withContext emptyList()

            val out = mutableListOf<LyricsSearchResult>()
            songs.take(4).forEach { element ->
                val s = element.jsonObject
                val songId = s["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val name = s.str("name") ?: query.title
                val artists = s["artists"]?.jsonArray?.mapNotNull { it.jsonObject.str("name") }
                    ?.joinToString(", ") ?: query.artist
                val duration = s.num("duration")?.toLong()
                val lyricBody = runCatching {
                    Http.get(
                        "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1", headers
                    )
                }.getOrNull() ?: return@forEach
                val root = runCatching { json.parseToJsonElement(lyricBody).jsonObject }.getOrNull()
                    ?: return@forEach
                val lrc = root["lrc"]?.jsonObject?.str("lyric")?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                val (score, reason) = MatchScorer.score(query, name, artists, duration)
                out += LyricsSearchResult(
                    providerId = id, providerName = displayName, resultId = songId,
                    title = name, artist = artists, album = s["album"]?.jsonObject?.str("name"),
                    durationMs = duration, language = null,
                    syncType = LyricsParser.detectSyncType(lrc),
                    format = LyricsParser.detectFormat(lrc),
                    content = lrc, matchScore = score, matchReason = reason
                )
            }
            out
        }
}

/** lyrics.ovh — simple, key-free plain-text lyrics endpoint. */
class LyricsOvhProvider : LyricsProvider {
    override val id = "lyricsovh"
    override val displayName = "Lyrics.ovh"

    override suspend fun search(query: LyricsQuery): List<LyricsSearchResult> =
        withContext(Dispatchers.IO) {
            if (query.artist.isBlank() || query.artist.startsWith("Unknown")) return@withContext emptyList()
            val body = Http.get(
                "https://api.lyrics.ovh/v1/${enc(query.artist)}/${enc(query.title)}"
            )
            if (body.isBlank()) return@withContext emptyList()
            val lyrics = runCatching {
                json.parseToJsonElement(body).jsonObject.str("lyrics")
            }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            val (score, reason) = MatchScorer.score(query, query.title, query.artist, null)
            listOf(
                LyricsSearchResult(
                    providerId = id, providerName = displayName,
                    resultId = MatchScorer.normalise("${query.artist}-${query.title}"),
                    title = query.title, artist = query.artist, album = null, durationMs = null,
                    language = null, syncType = SyncType.PLAIN, format = LyricsFormat.PLAIN,
                    content = lyrics.replace("\r\n", "\n"),
                    matchScore = (score - 5).coerceAtLeast(0),
                    matchReason = "$reason, exact artist/title endpoint"
                )
            )
        }
}

/**
 * Paxsenix "Lyrically" API adapter.
 *
 * Status (verified against https://lyrics.paxsenix.org/openapi.json): every search
 * endpoint (/musixmatch, /netease, /qq, /spotify, /kugou, /youtube) now answers
 * HTTP 403 "Forbidden – endpoint no longer available". The only open endpoint,
 * /apple-music/lyrics, requires an Apple Music catalogue id which cannot be derived
 * from a local file's metadata. The adapter therefore reports the real reason instead
 * of pretending to work, and is disabled by default in Settings.
 */
class PaxsenixProvider : LyricsProvider {
    override val id = "paxsenix"
    override val displayName = "Paxsenix Lyrically"

    override suspend fun search(query: LyricsQuery): List<LyricsSearchResult> =
        withContext(Dispatchers.IO) {
            val body = runCatching {
                Http.get("https://lyrics.paxsenix.org/musixmatch/lyrics?type=track&t=" +
                    enc(query.title) + "&a=" + enc(query.artist))
            }.getOrElse { throw ProviderUnavailableException(it.message ?: "Network error") }
            if (body.contains("\"error\"") || body.contains("Forbidden")) {
                throw ProviderUnavailableException(
                    "Search endpoints are disabled upstream (HTTP 403). Only /apple-music/lyrics " +
                        "with a known Apple Music id remains open."
                )
            }
            val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return@withContext emptyList()
            val content = obj.str("lyrics") ?: obj.str("plain") ?: obj.str("syncedLyrics")
                ?: return@withContext emptyList()
            val (score, reason) = MatchScorer.score(query, query.title, query.artist, null)
            listOf(
                LyricsSearchResult(
                    providerId = id, providerName = displayName, resultId = "paxsenix",
                    title = query.title, artist = query.artist,
                    syncType = LyricsParser.detectSyncType(content),
                    format = LyricsParser.detectFormat(content),
                    content = content, matchScore = score, matchReason = reason
                )
            )
        }
}

/** Reads lyrics embedded in the audio file itself (ID3 USLT). Works fully offline. */
class EmbeddedLyricsProvider(
    private val loader: suspend (LyricsQuery) -> String?
) : LyricsProvider {
    override val id = "embedded"
    override val displayName = "Embedded (file tag)"
    override val requiresNetwork = false

    override suspend fun search(query: LyricsQuery): List<LyricsSearchResult> {
        val content = loader(query)?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return listOf(
            LyricsSearchResult(
                providerId = id, providerName = displayName, resultId = "embedded-${query.trackId}",
                title = query.title, artist = query.artist, album = query.album,
                durationMs = query.durationMs.takeIf { it > 0 }, language = null,
                syncType = LyricsParser.detectSyncType(content),
                format = LyricsParser.detectFormat(content),
                content = content, matchScore = 100,
                matchReason = "Embedded in this exact file"
            )
        )
    }
}
