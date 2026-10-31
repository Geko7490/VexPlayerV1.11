package com.vexmusic.player.lyrics.translation

import com.vexmusic.player.lyrics.provider.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class SupportedLanguage(val code: String, val englishName: String, val nativeName: String)

object Languages {
    val targets = listOf(
        SupportedLanguage("tr", "Turkish", "Türkçe"),
        SupportedLanguage("en", "English", "English"),
        SupportedLanguage("es", "Spanish", "Español"),
        SupportedLanguage("de", "German", "Deutsch"),
        SupportedLanguage("fr", "French", "Français"),
        SupportedLanguage("pt", "Portuguese", "Português"),
        SupportedLanguage("it", "Italian", "Italiano"),
        SupportedLanguage("ja", "Japanese", "日本語"),
        SupportedLanguage("ko", "Korean", "한국어"),
        SupportedLanguage("ar", "Arabic", "العربية"),
        SupportedLanguage("ru", "Russian", "Русский")
    )

    fun name(code: String): String =
        targets.firstOrNull { it.code == code }?.let { "${it.nativeName} (${it.englishName})" } ?: code
}

interface TranslationProvider {
    val name: String
    /** True when the provider can be used without a private API key. */
    val availableWithoutKey: Boolean
    suspend fun translate(lines: List<String>, targetLanguage: String, sourceLanguage: String?): List<String>
}

/**
 * MyMemory translation (https://mymemory.translated.net) — usable anonymously, no API key
 * is embedded in the APK. Requests are chunked to respect the documented 500 character limit.
 */
class MyMemoryTranslationProvider : TranslationProvider {
    override val name = "MyMemory"
    override val availableWithoutKey = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val separator = " ||| "

    override suspend fun translate(
        lines: List<String>,
        targetLanguage: String,
        sourceLanguage: String?
    ): List<String> = withContext(Dispatchers.IO) {
        val source = sourceLanguage ?: "en"
        if (source.equals(targetLanguage, ignoreCase = true)) return@withContext lines
        val out = MutableList(lines.size) { "" }
        var index = 0
        while (index < lines.size) {
            val chunk = mutableListOf<Pair<Int, String>>()
            var length = 0
            while (index < lines.size && length < 420) {
                val text = lines[index]
                if (text.isBlank()) { out[index] = ""; index++; continue }
                if (text.length > 450) {
                    chunk += index to text.take(450)
                    index++
                    break
                }
                chunk += index to text
                length += text.length + separator.length
                index++
            }
            if (chunk.isEmpty()) continue
            val joined = chunk.joinToString(separator) { it.second }
            val translated = translateOnce(joined, source, targetLanguage)
            val parts = translated.split("|||").map { it.trim() }
            if (parts.size == chunk.size) {
                chunk.forEachIndexed { i, (lineIndex, _) -> out[lineIndex] = parts[i] }
            } else {
                // Separator was not preserved — fall back to per-line translation.
                chunk.forEach { (lineIndex, text) ->
                    out[lineIndex] = runCatching { translateOnce(text, source, targetLanguage) }
                        .getOrDefault("")
                }
            }
        }
        out
    }

    private fun translateOnce(text: String, source: String, target: String): String {
        val url = "https://api.mymemory.translated.net/get?q=" +
            URLEncoder.encode(text, "UTF-8") + "&langpair=$source|$target"
        val body = Http.get(url)
        if (body.isBlank()) return ""
        val root = json.parseToJsonElement(body).jsonObject
        val status = root["responseStatus"]?.jsonPrimitive?.contentOrNull
        if (status != null && status != "200") {
            throw IllegalStateException(
                root["responseDetails"]?.jsonPrimitive?.contentOrNull ?: "Translation failed ($status)"
            )
        }
        return root["responseData"]?.jsonObject?.get("translatedText")
            ?.jsonPrimitive?.contentOrNull.orEmpty()
    }
}

/**
 * Lightweight script/stop-word language guess. Clearly a heuristic — the UI always lets
 * the user override the detected source language.
 */
object LanguageDetector {
    private val stopWords = mapOf(
        "tr" to listOf(" bir ", " ve ", " bu ", " için ", " değil ", " gibi "),
        "es" to listOf(" que ", " los ", " para ", " con ", " una "),
        "de" to listOf(" und ", " nicht ", " ist ", " der ", " ich "),
        "fr" to listOf(" les ", " est ", " pour ", " dans ", " pas "),
        "pt" to listOf(" não ", " uma ", " com ", " para ", " você "),
        "it" to listOf(" che ", " per ", " non ", " sono ", " una "),
        "en" to listOf(" the ", " you ", " and ", " that ", " with ")
    )

    fun detect(text: String): String? {
        if (text.isBlank()) return null
        val sample = text.take(4000)
        val counts = mapOf(
            "ja" to Regex("[\\u3040-\\u30ff]").findAll(sample).count(),
            "ko" to Regex("[\\uac00-\\ud7af]").findAll(sample).count(),
            "ru" to Regex("[\\u0400-\\u04FF]").findAll(sample).count(),
            "ar" to Regex("[\\u0600-\\u06FF]").findAll(sample).count(),
            "zh" to Regex("[\\u4e00-\\u9fff]").findAll(sample).count()
        )
        counts.maxByOrNull { it.value }?.let { if (it.value > 12) return it.key }
        val lower = " ${sample.lowercase()} "
        val best = stopWords.mapValues { (_, words) -> words.sumOf { w ->
            Regex(Regex.escape(w)).findAll(lower).count()
        } }.maxByOrNull { it.value }
        return best?.takeIf { it.value >= 2 }?.key
    }
}
