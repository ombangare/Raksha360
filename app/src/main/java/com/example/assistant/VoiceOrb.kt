package com.example.assistant

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * VoiceOrb - A fluid, 3D-styled animated voice orb inspired by the ElevenLabs Orb component.
 *
 * Implements real session telemetry-driven motion mapping:
 * - IDLE: Subtle organic breathing and phase rotation.
 * - CONNECTING: Rhythmic expansion pulse with glowing outer aura rings.
 * - LISTENING: Gently reactive to user voice activity (audioLevel).
 * - SPEAKING: Dynamic organic wave deformation and swelling driven by agent playback audioLevel.
 * - MUTED: Subdued opacity, muted slate-cyan palette, reduced motion.
 * - ERROR: Calm warm amber/coral glow with reduced motion.
 * - DISCONNECTED: Clean idle state.
 */
@Composable
fun VoiceOrb(
    state: ElevenLabsAssistantManager.State,
    isMuted: Boolean,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 250.dp
) {
    // Smooth the real-time audio telemetry to avoid jagged visual jumps
    val smoothedAudioLevel by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 65, easing = LinearOutSlowInEasing),
        label = "smoothedAudioLevel"
    )

    // Continuous infinite transitions for rotation, breathing, and harmonic wave phases
    val infiniteTransition = rememberInfiniteTransition(label = "orb_infinite")

    // Slow rotation phase for internal liquid swirl (360 degrees over 12 seconds)
    val rotationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationPhase"
    )

    // Breathing pulse for idle & connecting
    val breathingCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == ElevenLabsAssistantManager.State.CONNECTING) 1200 else 3200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingCycle"
    )

    // Secondary ripple phase for outer connecting / speaking rings
    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )

    Box(
        modifier = modifier
            .size(size)
            .testTag("voice_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension * 0.34f

            // Compute effective scale based on state and telemetry
            val scale = when {
                isMuted -> 0.95f
                state == ElevenLabsAssistantManager.State.ERROR -> 0.96f
                state == ElevenLabsAssistantManager.State.CONNECTING -> 0.95f + breathingCycle * 0.12f
                state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> 1.0f + (smoothedAudioLevel * 0.32f) + (sin(rotationPhase * 3) * 0.03f)
                state == ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> 0.98f + (smoothedAudioLevel * 0.22f) + (breathingCycle * 0.04f)
                else -> 0.97f + (breathingCycle * 0.05f)
            }

            val currentRadius = baseRadius * scale

            // 1. Draw outer diffuse glow / bloom
            drawOuterBloom(
                center = center,
                radius = currentRadius,
                state = state,
                isMuted = isMuted,
                audioLevel = smoothedAudioLevel,
                breathingCycle = breathingCycle,
                ripplePhase = ripplePhase
            )

            // 2. Draw organic deforming aura layer (when connecting, listening, or speaking)
            if (state == ElevenLabsAssistantManager.State.CONNECTING ||
                state == ElevenLabsAssistantManager.State.CONNECTED_LISTENING ||
                state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING
            ) {
                drawDeformingAura(
                    center = center,
                    baseRadius = currentRadius * 1.06f,
                    phase = rotationPhase,
                    audioLevel = smoothedAudioLevel,
                    state = state
                )
            }

            // 3. Draw 3D-shaded fluid sphere body
            drawVolumetricSphere(
                center = center,
                radius = currentRadius,
                state = state,
                isMuted = isMuted,
                phase = rotationPhase,
                audioLevel = smoothedAudioLevel
            )

            // 4. Draw internal liquid swirl patterns
            drawInternalPlasma(
                center = center,
                radius = currentRadius,
                phase = rotationPhase,
                state = state,
                isMuted = isMuted,
                audioLevel = smoothedAudioLevel
            )

            // 5. Draw 3D specular highlight and Fresnel outer glass rim
            drawSpecularHighlights(
                center = center,
                radius = currentRadius,
                state = state,
                isMuted = isMuted
            )
        }
    }
}

/**
 * 1. Outer Bloom / Ambient Diffuse Glow
 */
private fun DrawScope.drawOuterBloom(
    center: Offset,
    radius: Float,
    state: ElevenLabsAssistantManager.State,
    isMuted: Boolean,
    audioLevel: Float,
    breathingCycle: Float,
    ripplePhase: Float
) {
    val bloomScale = when {
        isMuted -> 1.25f
        state == ElevenLabsAssistantManager.State.ERROR -> 1.30f
        state == ElevenLabsAssistantManager.State.CONNECTING -> 1.35f + (breathingCycle * 0.2f)
        state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> 1.40f + (audioLevel * 0.45f)
        state == ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> 1.35f + (audioLevel * 0.30f)
        else -> 1.30f + (breathingCycle * 0.08f)
    }

    val (glowCenterColor, glowOuterColor) = when {
        isMuted -> Color(0x3094A3B8) to Color(0x05475569)
        state == ElevenLabsAssistantManager.State.ERROR -> Color(0x40F97316) to Color(0x05EF4444)
        state == ElevenLabsAssistantManager.State.CONNECTING -> Color(0x5500E5FF) to Color(0x102563EB)
        state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> Color(0x7000E5FF) to Color(0x203B82F6)
        state == ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> Color(0x5510B981) to Color(0x1500E5FF)
        else -> Color(0x3500E5FF) to Color(0x081D4ED8)
    }

    // Soft outer diffuse circle
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowCenterColor, glowOuterColor, Color.Transparent),
            center = center,
            radius = radius * bloomScale
        ),
        radius = radius * bloomScale,
        center = center
    )

    // Animated connecting pulse ring
    if (state == ElevenLabsAssistantManager.State.CONNECTING) {
        val pulseRingRadius = radius * (1.1f + ripplePhase * 0.6f)
        val pulseAlpha = (1f - ripplePhase) * 0.5f
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = pulseAlpha),
            radius = pulseRingRadius,
            center = center,
            style = Stroke(width = 2.5f)
        )
    }
}

/**
 * 2. Deforming Organic Plasma Ring
 */
private fun DrawScope.drawDeformingAura(
    center: Offset,
    baseRadius: Float,
    phase: Float,
    audioLevel: Float,
    state: ElevenLabsAssistantManager.State
) {
    val harmonicPath = Path()
    val steps = 90
    val deformationAmp = when (state) {
        ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> 0.12f + (audioLevel * 0.28f)
        ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> 0.06f + (audioLevel * 0.15f)
        else -> 0.05f
    }

    for (i in 0..steps) {
        val angle = (i.toFloat() / steps) * (2 * PI).toFloat()
        // Combine 3 distinct sinusoidal harmonics for natural organic deformation
        val harmonic = (
            sin(angle * 3f + phase * 2f) * 0.5f +
            cos(angle * 5f - phase * 1.5f) * 0.35f +
            sin(angle * 7f + phase * 0.8f) * 0.15f
        ) * deformationAmp

        val r = baseRadius * (1f + harmonic)
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)

        if (i == 0) {
            harmonicPath.moveTo(x, y)
        } else {
            harmonicPath.lineTo(x, y)
        }
    }
    harmonicPath.close()

    val auraColor = when (state) {
        ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> Color(0x3500E5FF)
        ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> Color(0x2534D399)
        else -> Color(0x2038BDF8)
    }

    drawPath(
        path = harmonicPath,
        color = auraColor,
        style = Stroke(width = 3f)
    )
}

/**
 * 3. Volumetric 3D Shaded Sphere
 */
private fun DrawScope.drawVolumetricSphere(
    center: Offset,
    radius: Float,
    state: ElevenLabsAssistantManager.State,
    isMuted: Boolean,
    phase: Float,
    audioLevel: Float
) {
    // Offset light source slightly to top-left for natural 3D spherical curvature
    val lightOffset = Offset(
        x = center.x - radius * 0.30f + sin(phase) * (radius * 0.05f),
        y = center.y - radius * 0.32f + cos(phase) * (radius * 0.05f)
    )

    val sphereColors = when {
        isMuted -> listOf(
            Color(0xFFE2E8F0),
            Color(0xFF94A3B8),
            Color(0xFF475569),
            Color(0xFF1E293B),
            Color(0xFF0F172A)
        )
        state == ElevenLabsAssistantManager.State.ERROR -> listOf(
            Color(0xFFFFFBEB),
            Color(0xFFFBBF24),
            Color(0xFFF97316),
            Color(0xFFDC2626),
            Color(0xFF7F1D1D)
        )
        state == ElevenLabsAssistantManager.State.CONNECTING -> listOf(
            Color(0xFFE0F7FA),
            Color(0xFF38BDF8),
            Color(0xFF0284C7),
            Color(0xFF1E40AF),
            Color(0xFF0F172A)
        )
        state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> listOf(
            Color(0xFFFFFFFF),
            Color(0xFF67E8F9),
            Color(0xFF00E5FF),
            Color(0xFF2563EB),
            Color(0xFF1E1B4B)
        )
        state == ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> listOf(
            Color(0xFFF0FDF4),
            Color(0xFF6EE7B7),
            Color(0xFF059669),
            Color(0xFF0284C7),
            Color(0xFF0F172A)
        )
        else -> listOf(
            Color(0xFFF8FAFC),
            Color(0xFF7DD3FC),
            Color(0xFF0284C7),
            Color(0xFF1E3A8A),
            Color(0xFF0B1329)
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = sphereColors,
            center = lightOffset,
            radius = radius * 1.35f
        ),
        radius = radius,
        center = center
    )
}

/**
 * 4. Internal Liquid Swirl / Plasma
 */
private fun DrawScope.drawInternalPlasma(
    center: Offset,
    radius: Float,
    phase: Float,
    state: ElevenLabsAssistantManager.State,
    isMuted: Boolean,
    audioLevel: Float
) {
    if (isMuted || state == ElevenLabsAssistantManager.State.DISCONNECTED) return

    val spherePath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center, radius))
    }

    clipPath(spherePath) {
        val swirlAngle = phase * 1.5f
        val swirlOffset1 = Offset(
            center.x + (radius * 0.35f * cos(swirlAngle)),
            center.y + (radius * 0.35f * sin(swirlAngle))
        )
        val swirlOffset2 = Offset(
            center.x + (radius * 0.40f * cos(-swirlAngle * 0.8f + 1.2f)),
            center.y + (radius * 0.40f * sin(-swirlAngle * 0.8f + 1.2f))
        )

        val plasmaColor1 = if (state == ElevenLabsAssistantManager.State.ERROR) {
            Color(0x50FDE047)
        } else {
            Color(0x4500E5FF).copy(alpha = 0.30f + (audioLevel * 0.35f))
        }

        val plasmaColor2 = if (state == ElevenLabsAssistantManager.State.ERROR) {
            Color(0x35F87171)
        } else {
            Color(0x4038BDF8).copy(alpha = 0.25f + (audioLevel * 0.30f))
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(plasmaColor1, Color.Transparent),
                center = swirlOffset1,
                radius = radius * 0.65f
            ),
            radius = radius * 0.65f,
            center = swirlOffset1
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(plasmaColor2, Color.Transparent),
                center = swirlOffset2,
                radius = radius * 0.55f
            ),
            radius = radius * 0.55f,
            center = swirlOffset2
        )
    }
}

/**
 * 5. Specular Highlights & Fresnel Outer Glass Rim
 */
private fun DrawScope.drawSpecularHighlights(
    center: Offset,
    radius: Float,
    state: ElevenLabsAssistantManager.State,
    isMuted: Boolean
) {
    // Primary specular gloss at top-left
    val glossCenter = Offset(
        x = center.x - radius * 0.38f,
        y = center.y - radius * 0.38f
    )
    val glossColor = if (isMuted) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.75f)

    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(glossColor, Color.Transparent),
            center = glossCenter,
            radius = radius * 0.32f
        ),
        topLeft = Offset(glossCenter.x - radius * 0.18f, glossCenter.y - radius * 0.14f),
        size = Size(radius * 0.36f, radius * 0.28f)
    )

    // Secondary subtle bottom-right Fresnel reflection
    val fresnelCenter = Offset(
        x = center.x + radius * 0.35f,
        y = center.y + radius * 0.35f
    )
    val fresnelColor = if (state == ElevenLabsAssistantManager.State.ERROR) {
        Color(0x20F87171)
    } else {
        Color(0x3038BDF8)
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(fresnelColor, Color.Transparent),
            center = fresnelCenter,
            radius = radius * 0.45f
        ),
        radius = radius * 0.45f,
        center = fresnelCenter
    )

    // Outer subtle glass rim
    drawCircle(
        color = Color.White.copy(alpha = if (isMuted) 0.12f else 0.25f),
        radius = radius - 1f,
        center = center,
        style = Stroke(width = 1.5f)
    )
}
