package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonPink
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun AudioSpectrumBar(
    amplitude: Float,
    isSpeakingOrListening: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SpectrumBars")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SpectrumPhase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("audio_spectrum_visualizer")
    ) {
        val barCount = 28
        val spacing = 6f
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount
        val maxHeight = size.height
        val centerY = maxHeight / 2f

        for (i in 0 until barCount) {
            val normalizedIdx = (i - barCount / 2f) / (barCount / 2f)
            val bellCurve = 1f - abs(normalizedIdx) * 0.45f
            val wave = if (isSpeakingOrListening) {
                abs(sin(i * 0.4f + phase)) * 0.7f + 0.3f
            } else {
                0.15f
            }

            val dynamicFactor = if (isSpeakingOrListening) {
                0.2f + (amplitude.coerceIn(0f, 1f) * 0.8f * wave * bellCurve)
            } else {
                0.12f
            }

            val barHeight = (maxHeight * dynamicFactor).coerceAtLeast(4f)
            val left = i * (barWidth + spacing)
            val top = centerY - (barHeight / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NeonPink,
                        ElectricCyan
                    ),
                    startY = top,
                    endY = top + barHeight
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
