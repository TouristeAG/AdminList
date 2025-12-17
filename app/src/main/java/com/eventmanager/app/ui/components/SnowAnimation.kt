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
fun SnowAnimation(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Check if current date is between Dec 22-25
    val calendar = Calendar.getInstance()
    val month = calendar.get(Calendar.MONTH) // 0-11
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    
    val isSnowSeason = month == Calendar.DECEMBER && day in 22..25
    val shouldShowSnow = enabled && isSnowSeason
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (shouldShowSnow) {
            // Generate snowflake particles
            data class Snowflake(
                val x: Float,
                val startY: Float,
                val size: Float,
                val speed: Float,
                val wobble: Float,
                val wobbleAmount: Float,
                val opacity: Float,
                val phaseOffset: Float // Unique offset for each snowflake
            )
            
            val snowflakes = remember {
                val rnd = Random(System.nanoTime())
                val count = 80 // Number of snowflakes
                List(count) { _ ->
                    Snowflake(
                        x = rnd.nextFloat(),
                        startY = rnd.nextFloat() * 1.5f - 0.5f, // Start from above or within screen
                        size = rnd.nextFloat() * 3f + 1f,
                        speed = rnd.nextFloat() * 1.5f + 0.5f, // Varying speeds
                        wobble = rnd.nextFloat() * PI.toFloat() * 2f,
                        wobbleAmount = rnd.nextFloat() * 0.15f + 0.05f,
                        opacity = rnd.nextFloat() * 0.6f + 0.3f,
                        phaseOffset = rnd.nextFloat() * 10000f // Random offset in milliseconds
                    )
                }
            }
            
            // Infinite animation for falling snow
            val infinite = rememberInfiniteTransition(label = "snow_animation")
            
            val snowfallProgress by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 8000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "snowfall"
            )
            
            // Wobble animation for side-to-side movement
            val wobbleProgress by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 2f * PI.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "wobble"
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                snowflakes.forEach { flake ->
                    // Each snowflake has its own phase offset for independent falling
                    val adjustedProgress = (snowfallProgress + flake.phaseOffset / 8000f) % 1f
                    
                    // Calculate vertical position with speed variation
                    val yPos = flake.startY * height + (adjustedProgress * (height + flake.size * 4)) - flake.size
                    
                    // Only draw if within visible area or just above/below
                    if (yPos < height + flake.size && yPos > -flake.size * 2) {
                        // Calculate wobble movement (left-right swaying) - unique to each flake
                        val wobbleOffset = (wobbleProgress + flake.wobble + flake.phaseOffset / 100f)
                        val wobbleAmount = cos(wobbleOffset) * width * flake.wobbleAmount
                        val xPos = (flake.x * width + wobbleAmount).mod(width)
                        
                        // Draw snowflake as a small circle
                        drawCircle(
                            color = Color.White.copy(alpha = flake.opacity),
                            radius = flake.size,
                            center = Offset(xPos, yPos)
                        )
                        
                        // Draw snowflake sparkle/star pattern
                        drawSnowflakeStar(
                            centerX = xPos,
                            centerY = yPos,
                            size = flake.size * 2f,
                            alpha = flake.opacity * 0.6f,
                            rotation = wobbleOffset
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSnowflakeStar(
    centerX: Float,
    centerY: Float,
    size: Float,
    alpha: Float,
    rotation: Float
) {
    val color = Color.White.copy(alpha = alpha)
    
    // Draw 4 lines in cross pattern for snowflake effect
    repeat(4) { index ->
        val angle = (index * PI.toFloat() / 2f) + rotation
        val endX = centerX + cos(angle) * size
        val endY = centerY + sin(angle) * size
        
        drawLine(
            color = color,
            start = Offset(centerX, centerY),
            end = Offset(endX, endY),
            strokeWidth = 0.5f
        )
    }
}
