package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.model.VoiceState
import com.example.ui.theme.BrightGold
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.MutedRed
import com.example.ui.theme.NeonPink
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class Particle(val angle: Float, val distRatio: Float, val radius: Float, val speed: Float)

@Composable
fun MayraVoiceOrb(
    voiceState: VoiceState,
    amplitude: Float,
    isMuted: Boolean,
    onOrbTapped: () -> Unit,
    onOrbLongPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransitions")

    // Continuous rotation for particle aura and fluid waves
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Breathing pulse for idle state
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathPulse"
    )

    // Smooth smoothed amplitude interpolation
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = tween(120, easing = LinearEasing),
        label = "SmoothedAmp"
    )

    // State-based aura colors
    val (primaryColor, secondaryColor, coreColor) = when {
        isMuted -> Triple(MutedRed.copy(alpha = 0.6f), DeepPurple.copy(alpha = 0.3f), Color(0xFF330B0B))
        voiceState == VoiceState.CONNECTING -> Triple(BrightGold, ElectricCyan, DeepPurple)
        voiceState == VoiceState.LISTENING -> Triple(ElectricCyan, NeonPink, Color(0xFF1B0836))
        voiceState == VoiceState.THINKING -> Triple(BrightGold, DeepPurple, HotMagenta)
        voiceState == VoiceState.SPEAKING -> Triple(NeonPink, HotMagenta, ElectricCyan)
        voiceState == VoiceState.INTERRUPTED -> Triple(BrightGold, MutedRed, HotMagenta)
        else -> Triple(NeonPink.copy(alpha = 0.85f), DeepPurple, Color(0xFF120722))
    }

    val animatedPrimary by animateColorAsState(primaryColor, tween(350), label = "PrimaryColor")
    val animatedSecondary by animateColorAsState(secondaryColor, tween(350), label = "SecondaryColor")
    val animatedCore by animateColorAsState(coreColor, tween(350), label = "CoreColor")

    val particles = remember {
        List(24) { i ->
            Particle(
                angle = (i * (360f / 24)),
                distRatio = 0.95f + (i % 5) * 0.08f,
                radius = 2.5f + (i % 3) * 1.5f,
                speed = 1f + (i % 4) * 0.4f
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(310.dp)
            .testTag("mayra_voice_orb")
            .semantics { contentDescription = "Mayra Voice Orb: ${voiceState.label}, tap to toggle, hold to interrupt" }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onOrbTapped() },
                    onLongPress = { onOrbLongPressed() }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.62f
            val dynamicRadius = baseRadius * breathScale * (1f + animatedAmplitude * 0.45f)

            // 1. Outer Ambient Glow Blur
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedPrimary.copy(alpha = 0.45f + animatedAmplitude * 0.35f),
                        animatedSecondary.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius * 1.65f
                ),
                radius = dynamicRadius * 1.65f,
                center = center
            )

            // 2. Orbiting Floating Energy Particles
            particles.forEach { p ->
                val particleAngleRad = ((p.angle + rotation * p.speed) * (PI / 180.0)).toFloat()
                val pDist = dynamicRadius * p.distRatio + (animatedAmplitude * 30f)
                val px = center.x + cos(particleAngleRad) * pDist
                val py = center.y + sin(particleAngleRad) * pDist
                drawCircle(
                    color = animatedPrimary.copy(alpha = 0.75f),
                    radius = p.radius * (1f + animatedAmplitude),
                    center = Offset(px, py)
                )
            }

            // 3. Harmonic Waveform Ribbons
            val waveCount = 4
            for (w in 0 until waveCount) {
                val wavePhase = (rotation * 2f + w * 90f) * (PI.toFloat() / 180f)
                val wavePath = Path()
                val steps = 72
                for (step in 0..steps) {
                    val theta = step * (2f * PI.toFloat() / steps)
                    val rOffset = sin(theta * 4f + wavePhase) * (8f + animatedAmplitude * 28f)
                    val r = dynamicRadius + rOffset
                    val x = center.x + cos(theta) * r
                    val y = center.y + sin(theta) * r
                    if (step == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                }
                wavePath.close()

                drawPath(
                    path = wavePath,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            animatedPrimary.copy(alpha = 0.7f),
                            animatedSecondary.copy(alpha = 0.8f),
                            animatedPrimary.copy(alpha = 0.7f)
                        ),
                        center = center
                    ),
                    style = Stroke(width = 2.5f + (animatedAmplitude * 3.5f))
                )
            }

            // 4. Solid Liquid Orb Center Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedSecondary.copy(alpha = 0.95f),
                        animatedPrimary.copy(alpha = 0.85f),
                        animatedCore
                    ),
                    center = Offset(center.x - dynamicRadius * 0.15f, center.y - dynamicRadius * 0.15f),
                    radius = dynamicRadius
                ),
                radius = dynamicRadius * 0.92f,
                center = center
            )

            // 5. Specular Holographic Reflection Highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - dynamicRadius * 0.35f, center.y - dynamicRadius * 0.35f),
                    radius = dynamicRadius * 0.45f
                ),
                radius = dynamicRadius * 0.45f,
                center = Offset(center.x - dynamicRadius * 0.35f, center.y - dynamicRadius * 0.35f)
            )

            // 6. Outer Subtle Orbit Ring
            drawCircle(
                color = animatedPrimary.copy(alpha = 0.35f),
                radius = dynamicRadius * 1.25f,
                center = center,
                style = Stroke(width = 1.5f)
            )
        }
    }
}
