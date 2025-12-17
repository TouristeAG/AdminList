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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random
import java.util.Calendar

@Composable
fun ValentineAnimation(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Check if current date is February 14
    val calendar = Calendar.getInstance()
    val month = calendar.get(Calendar.MONTH) // 0-11
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    
    val isValentinesDay = month == Calendar.FEBRUARY && day == 14
    val shouldShowHearts = enabled && isValentinesDay
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (shouldShowHearts) {
            // Generate heart particles
            data class Heart(
                val x: Float,
                val y: Float,
                val size: Float,
                val speedX: Float,
                val speedY: Float,
                val rotation: Float,
                val rotationSpeed: Float,
                val phaseOffset: Float,
                val color: Color,
                val bounce: Float
            )
            
            val hearts = remember {
                val rnd = Random(System.nanoTime())
                val count = 15 // Number of hearts
                List(count) { _ ->
                    val colors = listOf(
                        Color(0xFFFF1493), // Deep Pink
                        Color(0xFFFF69B4), // Hot Pink
                        Color(0xFFFF6B9D), // Light Pink
                        Color(0xFFE91E63), // Red Pink
                        Color(0xFFFF0000)  // Red
                    )
                    
                    Heart(
                        x = rnd.nextFloat() * 0.9f + 0.05f,
                        y = rnd.nextFloat() * 0.8f + 0.1f,
                        size = rnd.nextFloat() * 30f + 20f,
                        speedX = (rnd.nextFloat() - 0.5f) * 0.8f,
                        speedY = (rnd.nextFloat() - 0.5f) * 0.8f,
                        rotation = rnd.nextFloat() * PI.toFloat() * 2f,
                        rotationSpeed = (rnd.nextFloat() - 0.5f) * 8f,
                        phaseOffset = rnd.nextFloat() * 10000f,
                        color = colors[rnd.nextInt(colors.size)],
                        bounce = rnd.nextFloat() * 0.5f + 0.5f
                    )
                }
            }
            
            // Infinite animation for heart popping
            val infinite = rememberInfiniteTransition(label = "valentine_animation")
            
            val heartProgress by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "hearts"
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                hearts.forEach { heart ->
                    // Calculate animation progress with phase offset
                    val adjustedProgress = (heartProgress + heart.phaseOffset / 3000f) % 1f
                    
                    // Bounce cycle (0-1-0)
                    val bounceProgress = if (adjustedProgress < 0.5f) {
                        adjustedProgress * 2f
                    } else {
                        (1f - adjustedProgress) * 2f
                    }
                    
                    // Pop and shrink effect
                    val scale = when {
                        adjustedProgress < 0.1f -> adjustedProgress * 10f // Pop in
                        adjustedProgress < 0.8f -> 1f // Stay
                        else -> (1f - adjustedProgress) / 0.2f // Pop out
                    }.coerceIn(0f, 1f)
                    
                    // Movement with bouncing
                    val moveX = heart.speedX * bounceProgress * width * 0.2f
                    val moveY = heart.speedY * bounceProgress * height * 0.2f
                    
                    val xPos = (heart.x * width + moveX).coerceIn(0f, width)
                    val yPos = (heart.y * height + moveY).coerceIn(0f, height)
                    
                    // Rotation
                    val currentRotation = heart.rotation + heart.rotationSpeed * adjustedProgress
                    
                    // Alpha fade out
                    val alpha = when {
                        adjustedProgress < 0.1f -> adjustedProgress * 10f
                        adjustedProgress < 0.8f -> 1f
                        else -> (1f - adjustedProgress) / 0.2f
                    }.coerceIn(0f, 1f)
                    
                    // Draw heart shape
                    drawHeart(
                        centerX = xPos,
                        centerY = yPos,
                        size = heart.size * scale,
                        rotation = currentRotation,
                        color = heart.color.copy(alpha = alpha * 0.9f)
                    )
                    
                    // Add glow effect
                    drawHeart(
                        centerX = xPos,
                        centerY = yPos,
                        size = heart.size * scale * 1.3f,
                        rotation = currentRotation,
                        color = heart.color.copy(alpha = alpha * 0.2f)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawHeart(
    centerX: Float,
    centerY: Float,
    size: Float,
    rotation: Float,
    color: Color
) {
    val scaleFactor = size / 100f
    
    // Create a simple heart shape using circles and a triangle
    val halfSize = size / 2f
    
    // Left lobe
    drawCircle(
        color = color,
        radius = halfSize * 0.4f,
        center = Offset(
            centerX - halfSize * 0.35f + cos(rotation) * halfSize * 0.1f,
            centerY - halfSize * 0.15f + sin(rotation) * halfSize * 0.1f
        )
    )
    
    // Right lobe
    drawCircle(
        color = color,
        radius = halfSize * 0.4f,
        center = Offset(
            centerX + halfSize * 0.35f + cos(rotation) * halfSize * 0.1f,
            centerY - halfSize * 0.15f + sin(rotation) * halfSize * 0.1f
        )
    )
    
    // Bottom pointed part (approximate with circles)
    drawCircle(
        color = color,
        radius = halfSize * 0.35f,
        center = Offset(
            centerX + cos(rotation) * halfSize * 0.05f,
            centerY + halfSize * 0.4f + sin(rotation) * halfSize * 0.1f
        )
    )
    
    // Center bottom point
    drawCircle(
        color = color,
        radius = halfSize * 0.3f,
        center = Offset(
            centerX + cos(rotation) * halfSize * 0.08f,
            centerY + halfSize * 0.55f + sin(rotation) * halfSize * 0.15f
        )
    )
}
