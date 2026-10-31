package com.vexmusic.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BandInfo(val index: Int, val centerFreqHz: Int, val levelMb: Int)

data class EqualizerCapabilities(
    val supported: Boolean,
    val reason: String? = null,
    val bands: List<BandInfo> = emptyList(),
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val bassBoostSupported: Boolean = false,
    val virtualizerSupported: Boolean = false,
    val devicePresets: List<String> = emptyList()
)

/**
 * Real Android AudioEffect equalizer attached to the ExoPlayer audio session.
 * Device capability is detected at runtime; unsupported hardware is reported honestly.
 */
class EqualizerController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var sessionId: Int = 0

    private val _capabilities = MutableStateFlow(EqualizerCapabilities(false, "Not initialised"))
    val capabilities: StateFlow<EqualizerCapabilities> = _capabilities.asStateFlow()

    private val _levels = MutableStateFlow<List<Int>>(emptyList())
    val levels: StateFlow<List<Int>> = _levels.asStateFlow()

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) {
            releaseInternal()
            sessionId = 0
            _levels.value = emptyList()
            _capabilities.value = EqualizerCapabilities(false, "No active audio session yet")
            return
        }
        if (audioSessionId == sessionId && equalizer != null) return
        releaseInternal()
        sessionId = audioSessionId
        try {
            val eq = Equalizer(0, audioSessionId)
            val count = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val bands = (0 until count).map { i ->
                BandInfo(i, eq.getCenterFreq(i.toShort()) / 1000, eq.getBandLevel(i.toShort()).toInt())
            }
            val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            equalizer = eq
            bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
            virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
            _capabilities.value = EqualizerCapabilities(
                supported = true,
                bands = bands,
                minLevelMb = range[0].toInt(),
                maxLevelMb = range[1].toInt(),
                bassBoostSupported = bassBoost?.strengthSupported == true,
                virtualizerSupported = virtualizer?.strengthSupported == true,
                devicePresets = presets
            )
            _levels.value = bands.map { it.levelMb }
        } catch (t: Throwable) {
            Log.w("EqualizerController", "Equalizer unavailable", t)
            _capabilities.value = EqualizerCapabilities(
                false,
                t.message ?: "AudioEffect not supported on this device"
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled }
        runCatching { virtualizer?.enabled = enabled }
    }

    fun setBandLevel(index: Int, levelMb: Int) {
        val eq = equalizer ?: return
        if (index !in 0 until eq.numberOfBands.toInt()) return
        val range = _capabilities.value
        val boundedLevel = levelMb.coerceIn(range.minLevelMb, range.maxLevelMb)
        runCatching {
            eq.setBandLevel(index.toShort(), boundedLevel.toShort())
            _levels.value = _levels.value.toMutableList().also {
                if (index in it.indices) it[index] = boundedLevel
            }
        }
    }

    fun applyLevels(levelsMb: List<Int>) {
        levelsMb.forEachIndexed { i, level -> setBandLevel(i, level) }
    }

    fun setBassBoost(strength: Int) {
        runCatching {
            bassBoost?.takeIf { it.strengthSupported }?.setStrength(
                strength.coerceIn(0, 1000).toShort()
            )
        }
    }

    fun setVirtualizer(strength: Int) {
        runCatching {
            virtualizer?.takeIf { it.strengthSupported }?.setStrength(
                strength.coerceIn(0, 1000).toShort()
            )
        }
    }

    @Synchronized
    fun release() {
        releaseInternal()
        sessionId = 0
        _levels.value = emptyList()
        _capabilities.value = EqualizerCapabilities(false, "No active audio session")
    }

    private fun releaseInternal() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null; bassBoost = null; virtualizer = null
    }

    companion object {
        /** Gain shape per preset, expressed as fractions of the device band range. */
        val presets: Map<String, List<Float>> = mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
            "Bass Boost" to listOf(0.85f, 0.55f, 0.1f, 0f, 0.05f),
            "Treble Boost" to listOf(-0.1f, 0f, 0.1f, 0.55f, 0.8f),
            "Rock" to listOf(0.6f, 0.25f, -0.1f, 0.3f, 0.55f),
            "Pop" to listOf(0.2f, 0.45f, 0.5f, 0.2f, 0.05f),
            "Hip-Hop" to listOf(0.8f, 0.4f, 0.05f, 0.25f, 0.4f),
            "Classical" to listOf(0.35f, 0.15f, -0.05f, 0.25f, 0.45f),
            "Electronic" to listOf(0.7f, 0.2f, -0.15f, 0.3f, 0.6f),
            "Vocal" to listOf(-0.25f, 0.1f, 0.65f, 0.4f, -0.05f)
        )

        /** Resamples a 5-point preset curve onto the device's actual band count. */
        fun levelsFor(preset: String, bandCount: Int, minMb: Int, maxMb: Int): List<Int> {
            val curve = presets[preset] ?: presets.getValue("Flat")
            if (bandCount <= 0) return emptyList()
            return (0 until bandCount).map { i ->
                val pos = if (bandCount == 1) 0f else i.toFloat() / (bandCount - 1) * (curve.size - 1)
                val low = pos.toInt().coerceIn(0, curve.size - 1)
                val high = (low + 1).coerceAtMost(curve.size - 1)
                val frac = pos - low
                val gain = curve[low] * (1 - frac) + curve[high] * frac
                val mb = if (gain >= 0) gain * maxMb else gain * -minMb
                mb.toInt().coerceIn(minMb, maxMb)
            }
        }
    }
}
