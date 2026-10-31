package com.vexmusic.player.util

import java.io.InputStream

/**
 * Minimal ID3v2 reader that extracts embedded unsynchronised lyrics (USLT frames).
 * Only the tag header area is read — the audio payload is never loaded into memory.
 */
object Id3LyricsExtractor {

    fun extract(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.read(header) != 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) return null

        val major = header[3].toInt()
        val size = syncSafe(header, 6)
        if (size <= 0 || size > 8 * 1024 * 1024) return null
        val body = ByteArray(size)
        var read = 0
        while (read < size) {
            val r = input.read(body, read, size - read)
            if (r <= 0) break
            read += r
        }
        if (read <= 0) return null

        var pos = 0
        val frameIdLen = if (major >= 3) 4 else 3
        while (pos + frameIdLen + 4 < read) {
            val id = String(body, pos, frameIdLen, Charsets.ISO_8859_1)
            if (id.isBlank() || id[0] == '\u0000') break
            val frameSize = when {
                major >= 4 -> syncSafe(body, pos + 4)
                major == 3 -> intAt(body, pos + 4)
                else -> ((body[pos + 3].toInt() and 0xFF) shl 16) or
                    ((body[pos + 4].toInt() and 0xFF) shl 8) or
                    (body[pos + 5].toInt() and 0xFF)
            }
            val headerSize = if (major >= 3) 10 else 6
            if (frameSize <= 0 || pos + headerSize + frameSize > read) break
            if (id == "USLT" || id == "ULT") {
                val frame = body.copyOfRange(pos + headerSize, pos + headerSize + frameSize)
                return parseUslt(frame)
            }
            pos += headerSize + frameSize
        }
        return null
    }

    private fun parseUslt(frame: ByteArray): String? {
        if (frame.size < 5) return null
        val encoding = frame[0].toInt()
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        // bytes 1..3 are the language code, then a content descriptor terminated by null
        var idx = 4
        if (encoding == 1 || encoding == 2) {
            while (idx + 1 < frame.size) {
                if (frame[idx].toInt() == 0 && frame[idx + 1].toInt() == 0) { idx += 2; break }
                idx += 2
            }
        } else {
            while (idx < frame.size && frame[idx].toInt() != 0) idx++
            idx++
        }
        if (idx >= frame.size) return null
        return String(frame, idx, frame.size - idx, charset)
            .trim('\u0000', ' ', '\n')
            .takeIf { it.isNotBlank() }
    }

    private fun syncSafe(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0x7F) shl 21) or
            ((b[offset + 1].toInt() and 0x7F) shl 14) or
            ((b[offset + 2].toInt() and 0x7F) shl 7) or
            (b[offset + 3].toInt() and 0x7F)

    private fun intAt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
}
