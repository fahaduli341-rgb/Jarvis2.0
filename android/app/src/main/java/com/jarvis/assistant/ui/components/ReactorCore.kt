package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.data.AssistantState
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisCyanDim
import com.jarvis.assistant.ui.theme.JarvisNavy

@Composable
fun ReactorCore(
    state: AssistantState,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val isActive = state != AssistantState.OFFLINE
    val infiniteTransition = rememberInfiniteTransition(label = "reactor")

    val rotationDuration = when (state) {
        AssistantState.OFFLINE -> 24000
        AssistantState.STANDBY -> 12000
        AssistantState.LISTENING -> 5000
        AssistantState.THINKING -> 2200
        AssistantState.SPEAKING -> 3600
    }

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (rotationDuration * 1.4f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    val smoothedAudio by animateFloatAsState(
        targetValue = if (state == AssistantState.LISTENING) audioLevel else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "audioScale"
    )

    val coreColor = when (state) {
        AssistantState.OFFLINE -> Color(0xFF334155)
        AssistantState.STANDBY -> JarvisCyanDim
        AssistantState.LISTENING -> JarvisCyan
        AssistantState.THINKING -> JarvisAmber
        AssistantState.SPEAKING -> Color(0xFF38BDF8)
    }

    Box(
        modifier = modifier.size(230.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(230.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Subtle glow backdrop (lightweight radial gradient, GPU-friendly on Redmi A5)
            if (isActive) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.22f + (smoothedAudio * 0.18f)),
                            JarvisNavy.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius,
                    center = center
                )
            }

            // Outer segmented arc ring
            val outerRadius = maxRadius * 0.86f
            val outerTopLeft = Offset(center.x - outerRadius, center.y - outerRadius)
            val outerSize = Size(outerRadius * 2f, outerRadius * 2f)
            val arcAngles = listOf(0f, 120f, 240f)
            arcAngles.forEach { baseAngle ->
                drawArc(
                    color = coreColor.copy(alpha = if (isActive) 0.85f else 0.35f),
                    startAngle = outerRotation + baseAngle,
                    sweepAngle = 76f,
                    useCenter = false,
                    topLeft = outerTopLeft,
                    size = outerSize,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Middle counter-rotating ring
            val midRadius = maxRadius * 0.66f
            val midTopLeft = Offset(center.x - midRadius, center.y - midRadius)
            val midSize = Size(midRadius * 2f, midRadius * 2f)
            listOf(0f, 180f).forEach { baseAngle ->
                drawArc(
                    color = coreColor.copy(alpha = if (isActive) 0.6f else 0.2f),
                    startAngle = innerRotation + baseAngle,
                    sweepAngle = 125f,
                    useCenter = false,
                    topLeft = midTopLeft,
                    size = midSize,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Inner reactor core
            val coreScale = if (state == AssistantState.LISTENING) {
                0.92f + (smoothedAudio * 0.24f)
            } else if (isActive) {
                idlePulse
            } else {
                0.85f
            }

            val coreRadius = (maxRadius * 0.34f) * coreScale
            drawCircle(
                color = JarvisNavy,
                radius = coreRadius + 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = coreColor.copy(alpha = if (isActive) 0.9f else 0.35f),
                radius = coreRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = coreColor.copy(alpha = if (isActive) 0.28f else 0.08f),
                radius = coreRadius * 0.72f,
                center = center
            )
        }
    }
}
