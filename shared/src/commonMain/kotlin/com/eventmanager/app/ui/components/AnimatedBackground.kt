package com.eventmanager.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.eventmanager.app.data.sync.SettingsManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private data class AnimatedBackgroundLayout(
    val circleCount: Int,
    val strokeFactor: Float,
    val strokeMin: Float,
    val strokeMax: Float,
    val baseRadiusFactor: Float,
    val ampFactor: Float,
    val spanFactor: Float,
    val edgeInsetFactor: Float,
)

private fun layoutFor(isDesktop: Boolean, width: Float, height: Float, animationMultiplier: Float): AnimatedBackgroundLayout {
    val maxDim = max(width, height)
    return if (isDesktop) {
        // Keep desktop sparse — large radii overlap quickly and tank performance.
        val count = (18 * animationMultiplier).toInt().coerceIn(10, 18)
        AnimatedBackgroundLayout(
            circleCount = count,
            strokeFactor = 0.0035f,
            strokeMin = 2f,
            strokeMax = 8f,
            baseRadiusFactor = 1.08f,
            ampFactor = 0.011f,
            spanFactor = 1.55f,
            edgeInsetFactor = 0.28f,
        )
    } else {
        val count = (28 * animationMultiplier).toInt().coerceAtLeast(10)
        AnimatedBackgroundLayout(
            circleCount = count,
            strokeFactor = 0.007f,
            strokeMin = 4f,
            strokeMax = 10f,
            baseRadiusFactor = 1.05f,
            ampFactor = 0.018f,
            spanFactor = 1.5f,
            edgeInsetFactor = 0.25f,
        )
    }
}

@Composable
fun AnimatedBackground(
    settingsManager: SettingsManager,
    enabled: Boolean = true,
    isDesktop: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val animationMultiplier = remember { settingsManager.getAnimationIntensityMultiplier() }
    val shouldAnimate = enabled && animationMultiplier > 0f

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (!shouldAnimate) return@BoxWithConstraints

        val colorScheme = MaterialTheme.colorScheme
        val layout = remember(isDesktop, maxWidth, maxHeight, animationMultiplier) {
            layoutFor(isDesktop, maxWidth.value, maxHeight.value, animationMultiplier)
        }

        val lineColors = remember(isDesktop, colorScheme) { backgroundLineColors(isDesktop, colorScheme) }

        data class CircleParam(val fx: Float, val fy: Float, val theta: Float, val color: Color)
        val circles = remember(layout.circleCount, lineColors) {
            val rnd = Random(0x4E0C7541)
            List(layout.circleCount) { i ->
                CircleParam(
                    fx = rnd.nextFloat(),
                    fy = rnd.nextFloat(),
                    theta = rnd.nextFloat() * 2f * PI.toFloat(),
                    color = lineColors.forIndex(i),
                )
            }
        }

        val infinite = rememberInfiniteTransition(label = "animated_background")
        val phase1 by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 30000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "phase1",
        )
        val phase2 by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 25000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "phase2",
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxDim = maxOf(size.width, size.height)
            val stroke = (maxDim * layout.strokeFactor).coerceIn(layout.strokeMin, layout.strokeMax)
            val baseRadius = maxDim * layout.baseRadiusFactor
            val amp = maxDim * layout.ampFactor
            val inset = layout.edgeInsetFactor
            val startX = -inset * size.width
            val spanX = size.width * layout.spanFactor
            val startY = -inset * size.height
            val spanY = size.height * layout.spanFactor

            circles.forEachIndexed { index, p ->
                val driftX = if (index % 2 == 0) {
                    amp * cos(phase1 + p.theta)
                } else {
                    amp * sin(phase2 + p.theta)
                }
                val driftY = if (index % 2 == 0) {
                    amp * sin(phase2 + p.theta)
                } else {
                    amp * cos(phase1 + p.theta)
                }
                val cx = startX + p.fx * spanX + driftX
                val cy = startY + p.fy * spanY + driftY

                drawCircle(
                    color = p.color,
                    radius = baseRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}
