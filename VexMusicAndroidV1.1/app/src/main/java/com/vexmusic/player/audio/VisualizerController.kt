package com.vexmusic.player.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.log10

data class VisualizerFrame(
    val magnitudes: FloatArray = FloatArray(0),
    val waveform: FloatArray = FloatArray(0)
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Real audio visualizer built on android.media.audiofx.Visualizer, attached to the
 * ExoPlayer audio session. Capture is stopped whenever visualisation is disabled or
 * playback pauses, so it does not drain the battery.
 */
class VisualizerController {

    private var visualizer: Visualizer? = null
    private var sessionId = 0

    private val _frame = MutableStateFlow(VisualizerFrame())
    val frame: StateFlow<VisualizerFrame> = _frame.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    @Synchronized
    fun start(audioSessionId: Int, bands: Int = 48) {
        if (audioSessionId == 0) {
            _error.value = "No active audio session"
            return
        }
        if (_active.value && sessionId == audioSessionId) return
        stop()
        sessionId = audioSessionId
        try {
            val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            val v = Visualizer(audioSessionId).apply {
                this.captureSize = captureSize
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            }
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int
                    ) {
                        waveform ?: return
                        val step = (waveform.size / 128).coerceAtLeast(1)
                        val out = FloatArray(waveform.size / step)
                        for (i in out.indices) {
                            out[i] = ((waveform[i * step].toInt() and 0xFF) - 128) / 128f
                        }
                        _frame.value = _frame.value.copy(waveform = out)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int
                    ) {
                        fft ?: return
                        val n = (fft.size / 2).coerceAtLeast(2)
                        val groups = bands.coerceAtMost(n / 2)
                        val mags = FloatArray(groups)
                        val perGroup = (n / 2) / groups
                        for (g in 0 until groups) {
                            var sum = 0f
                            for (k in 0 until perGroup) {
                                val idx = (g * perGroup + k) * 2
                                if (idx + 1 >= fft.size) break
                                val re = fft[idx].toFloat()
                                val im = fft[idx + 1].toFloat()
                                sum += hypot(re, im)
                            }
                            val avg = sum / perGroup
                            mags[g] = (log10(1f + avg) / 2.2f).coerceIn(0f, 1f)
                        }
                        _frame.value = _frame.value.copy(magnitudes = mags)
                    }
                },
                (Visualizer.getMaxCaptureRate() / 2).coerceAtMost(20000),
                true,
                true
            )
            v.enabled = true
            visualizer = v
            _active.value = true
            _error.value = null
        } catch (t: Throwable) {
            Log.w("VisualizerController", "Visualizer unavailable", t)
            _error.value = t.message
                ?: "Visualizer unavailable (RECORD_AUDIO permission or device restriction)"
            _active.value = false
        }
    }

    @Synchronized
    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        _active.value = false
        _frame.value = VisualizerFrame()
    }
}
