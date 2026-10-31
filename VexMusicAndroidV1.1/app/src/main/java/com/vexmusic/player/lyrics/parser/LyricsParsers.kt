package com.vexmusic.player.lyrics.parser

import com.vexmusic.player.domain.model.LyricsFormat
import com.vexmusic.player.domain.model.LyricsLine
import com.vexmusic.player.domain.model.ParsedLyrics
import com.vexmusic.player.domain.model.SyncType
import com.vexmusic.player.domain.model.WordTiming

/**
 * Parses standard LRC, enhanced (word level) LRC and plain text.
 * Nothing is truncated — long rap / drill lyrics are kept in full.
 */
object LrcParser {

    private val LINE_TIME = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val WORD_TIME = Regex("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>")
    private val META = Regex("^\\[(ar|ti|al|by|offset|length|re|ve|la|lang):(.*)]$", RegexOption.IGNORE_CASE)

    fun looksSynced(raw: String): Boolean = LINE_TIME.containsMatchIn(raw)

    fun hasWordTimings(raw: String): Boolean = WORD_TIME.containsMatchIn(raw)

    fun parse(raw: String, language: String? = null): ParsedLyrics {
        if (!looksSynced(raw)) return parsePlain(raw, language)

        var offset = 0L
        var lang = language
        val out = mutableListOf<LyricsLine>()

        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@forEach
            META.find(line.trim())?.let { m ->
                val key = m.groupValues[1].lowercase()
                val value = m.groupValues[2].trim()
                when (key) {
                    "offset" -> offset = value.toLongOrNull() ?: 0L
                    "la", "lang" -> lang = value.ifBlank { null }
                }
                return@forEach
            }
            val stamps = LINE_TIME.findAll(line).toList()
            if (stamps.isEmpty()) {
                val text = line.trim()
                if (text.isNotEmpty()) out += LyricsLine(null, text)
                return@forEach
            }
            val contentStart = stamps.last().range.last + 1
            val content = line.substring(contentStart)
            val words = parseWords(content)
            val text = content.replace(WORD_TIME, "").trim()
            stamps.forEach { st ->
                out += LyricsLine(timeMs = toMillis(st.groupValues), text = text, words = words)
            }
        }

        val sorted = out.sortedWith(compareBy(nullsLast()) { it.timeMs })
        val syncType = if (sorted.any { it.words.isNotEmpty() }) SyncType.WORD_SYNCED else SyncType.LINE_SYNCED
        return ParsedLyrics(sorted, syncType, lang, offset)
    }

    fun parsePlain(raw: String, language: String? = null): ParsedLyrics {
        val lines = raw.lines()
            .map { it.replace(LINE_TIME, "").trimEnd() }
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .map { LyricsLine(null, it) }
        return ParsedLyrics(lines, SyncType.PLAIN, language, 0L)
    }

    private fun parseWords(content: String): List<WordTiming> {
        val matches = WORD_TIME.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()
        val words = mutableListOf<WordTiming>()
        matches.forEachIndexed { i, m ->
            val start = toMillis(m.groupValues)
            val textStart = m.range.last + 1
            val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
            val text = content.substring(textStart, textEnd)
            val end = if (i + 1 < matches.size) toMillis(matches[i + 1].groupValues) else null
            if (text.isNotBlank()) words += WordTiming(start, end, text)
        }
        return words
    }

    private fun toMillis(groups: List<String>): Long {
        val min = groups[1].toLongOrNull() ?: 0L
        val sec = groups[2].toLongOrNull() ?: 0L
        val fracRaw = groups.getOrNull(3).orEmpty()
        val frac = when (fracRaw.length) {
            0 -> 0L
            1 -> (fracRaw.toLongOrNull() ?: 0L) * 100
            2 -> (fracRaw.toLongOrNull() ?: 0L) * 10
            else -> fracRaw.take(3).toLongOrNull() ?: 0L
        }
        return min * 60_000 + sec * 1000 + frac
    }

    /** Serialises parsed lyrics back to LRC so caches stay human readable. */
    fun toLrc(parsed: ParsedLyrics): String = parsed.lines.joinToString("\n") { line ->
        val t = line.timeMs
        if (t == null) line.text else {
            val m = t / 60_000
            val s = (t % 60_000) / 1000
            val cs = (t % 1000) / 10
            "[%02d:%02d.%02d]%s".format(m, s, cs, line.text)
        }
    }
}

/** TTML (Timed Text Markup Language) — the format used by Apple-style / BetterLyrics payloads. */
object TtmlParser {

    private val P_TAG = Regex("<p\\b([^>]*)>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val SPAN_TAG = Regex("<span\\b([^>]*)>(.*?)</span>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val ATTR = Regex("(\\w+(?::\\w+)?)\\s*=\\s*\"([^\"]*)\"")

    fun isTtml(raw: String): Boolean =
        raw.contains("<tt", ignoreCase = true) || raw.contains("</p>", ignoreCase = true)

    fun parse(raw: String, language: String? = null): ParsedLyrics {
        val lines = mutableListOf<LyricsLine>()
        var hasWords = false
        P_TAG.findAll(raw).forEach { p ->
            val attrs = attrs(p.groupValues[1])
            val begin = parseClock(attrs["begin"])
            val inner = p.groupValues[2]
            val words = mutableListOf<WordTiming>()
            SPAN_TAG.findAll(inner).forEach { s ->
                val sa = attrs(s.groupValues[1])
                val sb = parseClock(sa["begin"])
                val se = parseClock(sa["end"])
                val txt = stripTags(s.groupValues[2])
                if (sb != null && txt.isNotBlank()) words += WordTiming(sb, se, txt)
            }
            if (words.isNotEmpty()) hasWords = true
            val text = if (words.isNotEmpty()) words.joinToString("") { it.text }.trim()
            else stripTags(inner).trim()
            if (text.isNotBlank() || begin != null) {
                lines += LyricsLine(begin, text, words)
            }
        }
        if (lines.isEmpty()) return LrcParser.parsePlain(stripTags(raw), language)
        val type = when {
            hasWords -> SyncType.WORD_SYNCED
            lines.any { it.timeMs != null } -> SyncType.LINE_SYNCED
            else -> SyncType.PLAIN
        }
        return ParsedLyrics(lines.sortedWith(compareBy(nullsLast()) { it.timeMs }), type, language)
    }

    private fun attrs(s: String): Map<String, String> =
        ATTR.findAll(s).associate { it.groupValues[1].substringAfter(':') to it.groupValues[2] }

    private fun stripTags(s: String): String = s
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("\\s+"), " ")

    /** Supports 00:01:02.500, 01:02.500 and 62.5s clock values. */
    fun parseClock(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        if (v.endsWith("s")) return ((v.dropLast(1).toDoubleOrNull() ?: return null) * 1000).toLong()
        val parts = v.split(":")
        return try {
            when (parts.size) {
                3 -> (parts[0].toLong() * 3_600_000) + (parts[1].toLong() * 60_000) +
                    (parts[2].toDouble() * 1000).toLong()
                2 -> (parts[0].toLong() * 60_000) + (parts[1].toDouble() * 1000).toLong()
                1 -> (parts[0].toDouble() * 1000).toLong()
                else -> null
            }
        } catch (t: Throwable) {
            null
        }
    }
}

object LyricsParser {
    fun parse(content: String, format: LyricsFormat, language: String? = null): ParsedLyrics =
        when (format) {
            LyricsFormat.TTML -> TtmlParser.parse(content, language)
            LyricsFormat.PLAIN -> LrcParser.parsePlain(content, language)
            LyricsFormat.LRC, LyricsFormat.ENHANCED_LRC -> LrcParser.parse(content, language)
        }

    fun detectFormat(content: String): LyricsFormat = when {
        TtmlParser.isTtml(content) -> LyricsFormat.TTML
        LrcParser.hasWordTimings(content) -> LyricsFormat.ENHANCED_LRC
        LrcParser.looksSynced(content) -> LyricsFormat.LRC
        else -> LyricsFormat.PLAIN
    }

    fun detectSyncType(content: String): SyncType = when (detectFormat(content)) {
        LyricsFormat.ENHANCED_LRC -> SyncType.WORD_SYNCED
        LyricsFormat.LRC -> SyncType.LINE_SYNCED
        LyricsFormat.TTML -> TtmlParser.parse(content).syncType
        LyricsFormat.PLAIN -> SyncType.PLAIN
    }
}
