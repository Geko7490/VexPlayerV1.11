package com.vexmusic.player

import com.vexmusic.player.domain.model.LyricsFormat
import com.vexmusic.player.domain.model.SyncType
import com.vexmusic.player.lyrics.parser.LrcParser
import com.vexmusic.player.lyrics.parser.LyricsParser
import com.vexmusic.player.lyrics.parser.TtmlParser
import com.vexmusic.player.lyrics.provider.LyricsQuery
import com.vexmusic.player.lyrics.provider.MatchScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {

    @Test
    fun `parses standard lrc timestamps`() {
        val raw = """
            [ar:King Von]
            [00:12.50]Known to let that MAC fly
            [00:15.00]I'ma let this MAC fly
        """.trimIndent()
        val parsed = LrcParser.parse(raw)
        assertEquals(SyncType.LINE_SYNCED, parsed.syncType)
        assertEquals(2, parsed.lines.size)
        assertEquals(12_500L, parsed.lines[0].timeMs)
        assertEquals(15_000L, parsed.lines[1].timeMs)
    }

    @Test
    fun `expands multiple timestamps on one line`() {
        val parsed = LrcParser.parse("[00:10.00][01:10.00]Repeated hook line")
        assertEquals(2, parsed.lines.size)
        assertEquals(10_000L, parsed.lines[0].timeMs)
        assertEquals(70_000L, parsed.lines[1].timeMs)
    }

    @Test
    fun `reads enhanced word level timings`() {
        val parsed = LrcParser.parse("[00:05.00]<00:05.00>Hello <00:05.60>world")
        assertEquals(SyncType.WORD_SYNCED, parsed.syncType)
        assertEquals(2, parsed.lines[0].words.size)
        assertEquals(5_000L, parsed.lines[0].words[0].startMs)
        assertEquals("Hello ", parsed.lines[0].words[0].text)
    }

    @Test
    fun `plain text is never reported as synced`() {
        val parsed = LyricsParser.parse("just a line\nanother line", LyricsFormat.PLAIN)
        assertEquals(SyncType.PLAIN, parsed.syncType)
        assertTrue(parsed.lines.all { it.timeMs == null })
    }

    @Test
    fun `long lyrics are not truncated`() {
        val raw = (1..600).joinToString("\n") { "[00:%02d.00]line %d".format(it % 60, it) }
        val parsed = LrcParser.parse(raw)
        assertEquals(600, parsed.lines.size)
    }

    @Test
    fun `parses ttml with word spans`() {
        val ttml = """
            <tt><body><div>
            <p begin="00:00:01.000" end="00:00:04.000">
              <span begin="00:00:01.000" end="00:00:02.000">Hello </span>
              <span begin="00:00:02.000" end="00:00:04.000">world</span>
            </p>
            </div></body></tt>
        """.trimIndent()
        val parsed = TtmlParser.parse(ttml)
        assertEquals(SyncType.WORD_SYNCED, parsed.syncType)
        assertEquals(1000L, parsed.lines[0].timeMs)
        assertEquals("Hello world", parsed.lines[0].text)
    }

    @Test
    fun `detects format correctly`() {
        assertEquals(LyricsFormat.LRC, LyricsParser.detectFormat("[00:01.00]hey"))
        assertEquals(LyricsFormat.ENHANCED_LRC, LyricsParser.detectFormat("[00:01.00]<00:01.00>hey"))
        assertEquals(LyricsFormat.PLAIN, LyricsParser.detectFormat("hey"))
    }

    @Test
    fun `match scorer prefers exact metadata`() {
        val query = LyricsQuery("id", "Crazy Story", "King Von", durationMs = 364_000)
        val (good, _) = MatchScorer.score(query, "Crazy Story", "King Von", 364_000)
        val (bad, _) = MatchScorer.score(query, "Another Song", "Someone Else", 120_000)
        assertTrue(good > bad)
        assertTrue(good >= 90)
    }

    @Test
    fun `round trips parsed lyrics back to lrc`() {
        val raw = "[00:01.50]line one\n[00:03.00]line two"
        val lrc = LrcParser.toLrc(LrcParser.parse(raw))
        assertTrue(lrc.contains("[00:01.50]line one"))
        assertTrue(lrc.contains("[00:03.00]line two"))
    }
}
