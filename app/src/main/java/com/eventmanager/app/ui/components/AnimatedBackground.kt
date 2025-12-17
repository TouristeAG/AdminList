package com.eventmanager.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AnimatedBackground(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Only show animation if enabled
        if (enabled) {
            // Get theme colors
            val colorScheme = MaterialTheme.colorScheme
        
        // Create theme-adaptive colors
        val primaryColor = colorScheme.primary.copy(alpha = 0.15f)
        val secondaryColor = colorScheme.secondary.copy(alpha = 0.12f)
        val tertiaryColor = colorScheme.tertiary.copy(alpha = 0.10f)
        val surfaceVariantColor = colorScheme.surfaceVariant.copy(alpha = 0.08f)
        
        // Prepare randomized circle layout once per composition - BALANCED for performance and visual appeal
        data class CircleParam(val fx: Float, val fy: Float, val theta: Float, val color: Color)
        val circles = remember {
            val rnd = Random(System.nanoTime())
            val count = 28 // Middle ground between original 45 and optimized 15
            List(count) { i ->
                val fx = rnd.nextFloat() // 0..1 normalized
                val fy = rnd.nextFloat()
                val theta = rnd.nextFloat() * 2f * PI.toFloat()
                
                // Cycle through different colors for variety
                val color = when (i % 4) {
                    0 -> primaryColor
                    1 -> secondaryColor
                    2 -> tertiaryColor
                    else -> surfaceVariantColor
                }
                
                CircleParam(fx, fy, theta, color)
            }
        }

        // Simplified animation phases for better performance
        val infinite = rememberInfiniteTransition(label = "animated_background")
        
        val phase1 by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 30000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "phase1"
        )
        
        val phase2 by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 25000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "phase2"
        )

        // Draw animated circles - BALANCED for performance and visual appeal
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxDim = maxOf(size.width, size.height)
            val stroke = (maxDim * 0.007f).coerceIn(4f, 10f) // Slightly thicker strokes for better visibility
            
            // Balanced radius and movement
            val baseRadius = maxDim * 1.05f
            val amp = maxDim * 0.018f // Slightly increased amplitude for more movement
            
            // Balanced positioning for better coverage
            val startX = -0.25f * size.width
            val spanX = size.width * 1.5f
            val startY = -0.25f * size.height
            val spanY = size.height * 1.5f

            circles.forEachIndexed { index, p ->
                // Simplified movement pattern - only 2 phases instead of 3
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
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        }
    }
}
