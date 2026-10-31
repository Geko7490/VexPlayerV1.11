package com.vexmusic.player.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vexmusic.player.domain.model.LyricsDisplayMode
import com.vexmusic.player.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("vex_settings")

data class VexSettings(
    val language: String = "system",
    val autoLyricsSearch: Boolean = true,
    val enabledProviders: Set<String> = emptySet(),
    val providersInitialised: Boolean = false,
    val translationTargetLanguage: String = "tr",
    val translationEnabled: Boolean = false,
    val lyricsDisplayMode: LyricsDisplayMode = LyricsDisplayMode.ORIGINAL,
    val lyricsFontScale: Float = 1f,
    val visualizerEnabled: Boolean = false,
    val visualizerMode: String = "BARS",
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "Flat",
    val equalizerBands: List<Int> = emptyList(),
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val playbackSpeed: Float = 1f,
    val resumeOnStart: Boolean = true,
    val sortOrder: SortOrder = SortOrder.RECENTLY_ADDED,
    val gaplessCrossfade: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_LYRICS = booleanPreferencesKey("auto_lyrics")
        val PROVIDERS = stringSetPreferencesKey("providers")
        val PROVIDERS_INIT = booleanPreferencesKey("providers_init")
        val TRANSLATION_LANG = stringPreferencesKey("translation_lang")
        val TRANSLATION_ON = booleanPreferencesKey("translation_on")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val FONT_SCALE = floatPreferencesKey("lyrics_font_scale")
        val VIS_ON = booleanPreferencesKey("vis_on")
        val VIS_MODE = stringPreferencesKey("vis_mode")
        val EQ_ON = booleanPreferencesKey("eq_on")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val BASS = intPreferencesKey("bass")
        val VIRT = intPreferencesKey("virt")
        val SPEED = floatPreferencesKey("speed")
        val RESUME = booleanPreferencesKey("resume")
        val SORT = stringPreferencesKey("sort")
        val CROSSFADE = booleanPreferencesKey("crossfade")
    }

    val settings: Flow<VexSettings> = context.dataStore.data.map { p ->
        VexSettings(
            language = p[Keys.LANGUAGE] ?: "system",
            autoLyricsSearch = p[Keys.AUTO_LYRICS] ?: true,
            enabledProviders = p[Keys.PROVIDERS] ?: emptySet(),
            providersInitialised = p[Keys.PROVIDERS_INIT] ?: false,
            translationTargetLanguage = p[Keys.TRANSLATION_LANG] ?: "tr",
            translationEnabled = p[Keys.TRANSLATION_ON] ?: false,
            lyricsDisplayMode = runCatching {
                LyricsDisplayMode.valueOf(p[Keys.DISPLAY_MODE] ?: "ORIGINAL")
            }.getOrDefault(LyricsDisplayMode.ORIGINAL),
            lyricsFontScale = p[Keys.FONT_SCALE] ?: 1f,
            visualizerEnabled = p[Keys.VIS_ON] ?: false,
            visualizerMode = p[Keys.VIS_MODE] ?: "BARS",
            equalizerEnabled = p[Keys.EQ_ON] ?: false,
            equalizerPreset = p[Keys.EQ_PRESET] ?: "Flat",
            equalizerBands = p[Keys.EQ_BANDS]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                ?: emptyList(),
            bassBoost = p[Keys.BASS] ?: 0,
            virtualizer = p[Keys.VIRT] ?: 0,
            playbackSpeed = p[Keys.SPEED] ?: 1f,
            resumeOnStart = p[Keys.RESUME] ?: true,
            sortOrder = runCatching { SortOrder.valueOf(p[Keys.SORT] ?: "RECENTLY_ADDED") }
                .getOrDefault(SortOrder.RECENTLY_ADDED),
            gaplessCrossfade = p[Keys.CROSSFADE] ?: false
        )
    }

    suspend fun setLanguage(v: String) = edit { it[Keys.LANGUAGE] = v }
    suspend fun setAutoLyricsSearch(v: Boolean) = edit { it[Keys.AUTO_LYRICS] = v }
    suspend fun setEnabledProviders(v: Set<String>) = edit {
        it[Keys.PROVIDERS] = v
        it[Keys.PROVIDERS_INIT] = true
    }
    suspend fun setTranslationLanguage(v: String) = edit { it[Keys.TRANSLATION_LANG] = v }
    suspend fun setTranslationEnabled(v: Boolean) = edit { it[Keys.TRANSLATION_ON] = v }
    suspend fun setDisplayMode(v: LyricsDisplayMode) = edit { it[Keys.DISPLAY_MODE] = v.name }
    suspend fun setFontScale(v: Float) = edit { it[Keys.FONT_SCALE] = v }
    suspend fun setVisualizerEnabled(v: Boolean) = edit { it[Keys.VIS_ON] = v }
    suspend fun setVisualizerMode(v: String) = edit { it[Keys.VIS_MODE] = v }
    suspend fun setEqualizerEnabled(v: Boolean) = edit { it[Keys.EQ_ON] = v }
    suspend fun setEqualizerPreset(v: String) = edit { it[Keys.EQ_PRESET] = v }
    suspend fun setEqualizerBands(v: List<Int>) = edit { it[Keys.EQ_BANDS] = v.joinToString(",") }
    suspend fun setBassBoost(v: Int) = edit { it[Keys.BASS] = v }
    suspend fun setVirtualizer(v: Int) = edit { it[Keys.VIRT] = v }
    suspend fun setPlaybackSpeed(v: Float) = edit { it[Keys.SPEED] = v }
    suspend fun setResumeOnStart(v: Boolean) = edit { it[Keys.RESUME] = v }
    suspend fun setSortOrder(v: SortOrder) = edit { it[Keys.SORT] = v.name }
    suspend fun setCrossfade(v: Boolean) = edit { it[Keys.CROSSFADE] = v }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
