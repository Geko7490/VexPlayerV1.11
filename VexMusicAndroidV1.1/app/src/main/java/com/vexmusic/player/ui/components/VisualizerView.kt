package com.vexmusic.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.vexmusic.player.audio.VisualizerFrame
import com.vexmusic.player.ui.theme.VexPurple
import com.vexmusic.player.ui.theme.VexPurpleSoft
import com.vexmusic.player.ui.theme.VexTextSecondary
import kotlin.math.cos
import kotlin.math.sin

enum class VisualizerMode { BARS, WAVEFORM, CIRCULAR, MINIMAL, SPECTRUM }

@Composable
fun AudioVisualizer(
    frame: VisualizerFrame,
    mode: VisualizerMode,
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Box(modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = VexTextSecondary
            )
            return@Box
        }
        val gradient = Brush.verticalGradient(listOf(VexPurpleSoft, VexPurple))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val mags = frame.magnitudes
            val wave = frame.waveform
            when (mode) {
                VisualizerMode.BARS, VisualizerMode.SPECTRUM -> {
                    if (mags.isEmpty()) return@Canvas
                    val count = mags.size
                    val barWidth = size.width / (count * 1.5f)
                    for (i in 0 until count) {
                        val h = (mags[i] * size.height).coerceAtLeast(2f)
                        val x = i * (size.width / count) + barWidth * 0.25f
                        if (mode == VisualizerMode.BARS) {
                            drawRoundRect(
                                brush = gradient,
                                topLeft = Offset(x, size.height - h),
                                size = Size(barWidth, h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                            )
                        } else {
                            drawRoundRect(
                                brush = gradient,
                                topLeft = Offset(x, size.height / 2 - h / 2),
                                size = Size(barWidth, h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                            )
                        }
                    }
                }

                VisualizerMode.WAVEFORM, VisualizerMode.MINIMAL -> {
                    if (wave.isEmpty()) return@Canvas
                    val path = Path()
                    val step = size.width / (wave.size - 1).coerceAtLeast(1)
                    val amp = if (mode == VisualizerMode.MINIMAL) size.height / 5f else size.height / 2.4f
                    wave.forEachIndexed { i, value ->
                        val x = i * step
                        val y = size.height / 2 + value * amp
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path,
                        brush = gradient,
                        style = Stroke(width = if (mode == VisualizerMode.MINIMAL) 2f else 4f, cap = StrokeCap.Round)
                    )
                }

                VisualizerMode.CIRCULAR -> {
                    if (mags.isEmpty()) return@Canvas
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.height / 3.6f
                    val count = mags.size
                    for (i in 0 until count) {
                        val angle = (i.toFloat() / count) * 2 * Math.PI
                        val len = radius + mags[i] * size.height / 2.4f
                        val start = Offset(
                            center.x + (radius * cos(angle)).toFloat(),
                            center.y + (radius * sin(angle)).toFloat()
                        )
                        val end = Offset(
                            center.x + (len * cos(angle)).toFloat(),
                            center.y + (len * sin(angle)).toFloat()
                        )
                        drawLine(brush = gradient, start = start, end = end, strokeWidth = 3f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}
