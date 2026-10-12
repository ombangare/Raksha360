package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ShieldBlue
import kotlin.math.sin

@Composable
fun AudioWaveformVisualizer(
    rmsLevel: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = ShieldBlue
) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (isRecording) (rmsLevel / 1200f).coerceIn(0.1f, 1.0f) else 0.05f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "waveform_anim"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val barCount = 28
        val spacing = size.width / (barCount * 1.5f)
        val barWidth = spacing * 0.75f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val phase = (i.toFloat() / barCount) * Math.PI.toFloat() * 2f
            val waveMod = (sin(phase.toDouble()) * 0.4 + 0.6).toFloat()
            val barHeight = (size.height * 0.85f * animatedLevel * waveMod).coerceAtLeast(4f)
            val left = i * (barWidth + spacing) + spacing / 2f
            val top = centerY - (barHeight / 2f)

            drawRoundRect(
                color = if (isRecording) barColor else Color.LightGray.copy(alpha = 0.5f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}
