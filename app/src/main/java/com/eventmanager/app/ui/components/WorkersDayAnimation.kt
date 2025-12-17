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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random
import java.util.Calendar

@Composable
fun WorkersDayAnimation(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Check if current date is May 1
    val calendar = Calendar.getInstance()
    val month = calendar.get(Calendar.MONTH) // 0-11
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    
    val isWorkersDayDay = month == Calendar.MAY && day == 1
    val shouldShowSigns = enabled && isWorkersDayDay
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (shouldShowSigns) {
            // Get theme colors in Composable context
            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            val darkColor = MaterialTheme.colorScheme.onBackground // Dark dynamic color
            
            // Generate protest signs
            data class ProtestSign(
                val x: Float,
                val y: Float,
                val width: Float,
                val height: Float,
                val rotation: Float,
                val waveSpeed: Float,
                val phaseOffset: Float,
                val signType: Int, // 0-3 for different signs
                val color: Color
            )
            
            val signs = remember {
                val rnd = Random(System.nanoTime())
                val count = 5 // Number of protest signs
                List(count) { index ->
                    // Use app's theme colors dynamically
                    val colors = listOf(
                        primaryColor,
                        secondaryColor,
                        tertiaryColor,
                        primaryColor.copy(alpha = 0.8f),
                        secondaryColor.copy(alpha = 0.8f)
                    )
                    
                    // Spread signs across full width with some randomization
                    val baseX = (index / (count - 1f)) // 0, 0.25, 0.5, 0.75, 1.0 for 5 signs
                    val xVariation = rnd.nextFloat() * 0.1f - 0.05f // ±5% variation
                    
                    ProtestSign(
                        x = (baseX + xVariation).coerceIn(0f, 1f),
                        y = rnd.nextFloat() * 0.15f + 0.85f, // Bottom of screen (85-100%)
                        width = rnd.nextFloat() * 150f + 350f, // HUGE (350-500px)
                        height = rnd.nextFloat() * 150f + 350f, // HUGE square signs
                        rotation = (rnd.nextFloat() - 0.5f) * 0.2f,
                        waveSpeed = rnd.nextFloat() * 0.6f + 0.3f, // Much slower waves
                        phaseOffset = rnd.nextFloat() * 10000f,
                        signType = rnd.nextInt(4),
                        color = colors[index % colors.size] // Each sign gets a unique color in order
                    )
                }
            }
            
            // Back row - higher on screen with same structure
            val backRowSigns = remember {
                val rnd = Random(System.nanoTime() + 1) // Different seed for variation
                val count = 5
                List(count) { index ->
                    val colors = listOf(
                        primaryColor,
                        secondaryColor,
                        tertiaryColor,
                        primaryColor.copy(alpha = 0.8f),
                        secondaryColor.copy(alpha = 0.8f)
                    )
                    
                    val baseX = (index / (count - 1f))
                    val xVariation = rnd.nextFloat() * 0.1f - 0.05f
                    
                    ProtestSign(
                        x = (baseX + xVariation).coerceIn(0f, 1f),
                        y = rnd.nextFloat() * 0.15f + 0.65f, // Higher on screen (65-80%)
                        width = rnd.nextFloat() * 150f + 350f,
                        height = rnd.nextFloat() * 150f + 350f,
                        rotation = (rnd.nextFloat() - 0.5f) * 0.2f,
                        waveSpeed = rnd.nextFloat() * 0.6f + 0.3f,
                        phaseOffset = rnd.nextFloat() * 10000f + 2500f, // Offset by half the animation cycle
                        signType = rnd.nextInt(4),
                        color = colors[index % colors.size]
                    )
                }
            }
            
            // Infinite animation for waving signs - much slower
            val infinite = rememberInfiniteTransition(label = "workers_day_animation")
            
            val waveProgress by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 5000, easing = LinearEasing), // 5 seconds - much slower
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave"
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Draw back row first (behind)
                backRowSigns.forEach { sign ->
                    // Calculate animation progress with phase offset
                    val adjustedProgress = (waveProgress + sign.phaseOffset / 5000f) % 1f
                    
                    // Wave motion (sine wave for natural movement)
                    val waveAmount = sin(adjustedProgress * PI.toFloat() * 2f) * sign.waveSpeed
                    
                    // Position with wave effect - VERY minimal movement
                    val xPos = sign.x * width
                    val yPos = sign.y * height + waveAmount * height * 0.003f // Much smaller vertical movement
                    
                    // Rotation with wave effect - very subtle
                    val currentRotation = sign.rotation + waveAmount * 0.004f // Much smaller rotation
                    
                    // Draw the protest sign with reduced opacity for depth
                    drawProtestSign(
                        xPos = xPos,
                        yPos = yPos,
                        width = sign.width,
                        height = sign.height,
                        rotation = currentRotation,
                        color = sign.color,
                        signType = sign.signType,
                        opacityFactor = 0.6f // Back row is more transparent
                    )
                }
                
                // Draw front row (in front)
                signs.forEach { sign ->
                    // Calculate animation progress with phase offset
                    val adjustedProgress = (waveProgress + sign.phaseOffset / 5000f) % 1f
                    
                    // Wave motion (sine wave for natural movement)
                    val waveAmount = sin(adjustedProgress * PI.toFloat() * 2f) * sign.waveSpeed
                    
                    // Position with wave effect - VERY minimal movement
                    val xPos = sign.x * width
                    val yPos = sign.y * height + waveAmount * height * 0.003f // Much smaller vertical movement
                    
                    // Rotation with wave effect - very subtle
                    val currentRotation = sign.rotation + waveAmount * 0.004f // Much smaller rotation
                    
                    // Draw the protest sign
                    drawProtestSign(
                        xPos = xPos,
                        yPos = yPos,
                        width = sign.width,
                        height = sign.height,
                        rotation = currentRotation,
                        color = sign.color,
                        signType = sign.signType,
                        opacityFactor = 1.0f // Front row is fully opaque
                    )
                }
                
                // Draw fist in bottom right corner
                drawFist(
                    xPos = width * 0.88f, // Bottom right corner
                    yPos = height * 0.92f, // Much lower on screen, closer to bottom
                    size = minOf(width, height) * 0.45f, // 45% of screen size - much bigger
                    rotation = -15f, // -15 degrees
                    color = darkColor // Use tertiary color for better visibility
                )
            }
        }
    }
}

private fun DrawScope.drawProtestSign(
    xPos: Float,
    yPos: Float,
    width: Float,
    height: Float,
    rotation: Float,
    color: Color,
    signType: Int,
    opacityFactor: Float = 1.0f
) {
    val handleWidth = width * 0.12f
    val handleHeight = height * 0.6f
    val signSize = minOf(width * 0.9f, height) // Increased width constraint and removed multiplier
    val outerBoxBorder = 6f // Thicker outer border
    val outerBoxPadding = 8f // Space between outer box and sign
    val cornerRadius = signSize * 0.12f // Rounded corners
    
    // The handle position stays fixed at the bottom
    val handleTopY = yPos + signSize / 2f
    
    // Draw vertical handle (stays fixed at bottom)
    drawRect(
        color = Color(0xFF8B6F47),
        topLeft = Offset(
            xPos - handleWidth / 2f,
            handleTopY
        ),
        size = androidx.compose.ui.geometry.Size(handleWidth, handleHeight),
        alpha = 0.95f * opacityFactor
    )
    
    // Draw inner colored square with rounded corners (no border outlines)
    drawRoundRect(
        color = color,
        topLeft = Offset(
            xPos - signSize / 2f,
            yPos - signSize / 2f
        ),
        size = androidx.compose.ui.geometry.Size(signSize, signSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
        alpha = 0.9f * opacityFactor
    )
}

private fun DrawScope.drawFist(
    xPos: Float,
    yPos: Float,
    size: Float,
    rotation: Float,
    color: Color
) {
    // Helper function to rotate a point around a center
    fun rotatePoint(point: Offset, center: Offset, angle: Float): Offset {
        val radians = angle * PI.toFloat() / 180f
        val cosA = cos(radians)
        val sinA = sin(radians)
        val dx = point.x - center.x
        val dy = point.y - center.y
        return Offset(
            center.x + dx * cosA - dy * sinA,
            center.y + dx * sinA + dy * cosA
        )
    }
    
    val center = Offset(xPos, yPos)
    val handSize = size * 0.6f // Size of the circular hand - define FIRST
    val fistWidth = size * 0.24f // Arm width - restore to original smaller size
    val fistHeight = size * 0.8f // Height of the arm extending downward
    val armGap = 5f // Fixed gap between circles and arm (doesn't scale)
    
    // Calculate arm rectangle corners before rotation
    // Rectangle starts at about 30% down the circle for overlap effect
    val armStartY = yPos - handSize * 0.6f // About 30% down from top of circle
    val armTopLeft = Offset(xPos - fistWidth / 2f, armStartY) // Start 30% into circle
    val armBottomRight = Offset(xPos + fistWidth / 2f, armStartY + fistHeight) // Extend downward
    
    // Rotate arm corners
    val rotatedTopLeft = rotatePoint(armTopLeft, center, rotation)
    val rotatedBottomLeft = rotatePoint(Offset(xPos - fistWidth / 2f, armBottomRight.y), center, rotation)
    val rotatedTopRight = rotatePoint(Offset(xPos + fistWidth / 2f, armStartY), center, rotation)
    val rotatedBottomRight = rotatePoint(armBottomRight, center, rotation)
    
    // Draw rotated arm as a polygon (using 4 lines for the rectangle)
    drawLine(
        color = color,
        start = rotatedTopLeft,
        end = rotatedTopRight,
        strokeWidth = 2f,
        alpha = 0.95f
    )
    drawLine(
        color = color,
        start = rotatedTopRight,
        end = rotatedBottomRight,
        strokeWidth = fistWidth,
        alpha = 0.95f
    )
    drawLine(
        color = color,
        start = rotatedBottomRight,
        end = rotatedBottomLeft,
        strokeWidth = 2f,
        alpha = 0.95f
    )
    drawLine(
        color = color,
        start = rotatedBottomLeft,
        end = rotatedTopLeft,
        strokeWidth = fistWidth,
        alpha = 0.95f
    )
    
    // Filled rectangle (draw rect at original position, then will be drawn with circles on top)
    drawRect(
        color = color,
        topLeft = armTopLeft,
        size = androidx.compose.ui.geometry.Size(fistWidth, fistHeight),
        alpha = 0.95f
    )
    
    // Draw the hand (5 circles to make a fist - 1 palm + 4 fingers)
    // Palm circle (center top)
    val palmCenter = rotatePoint(Offset(xPos, yPos - handSize * 0.4f), center, rotation)
    drawCircle(
        color = color,
        radius = handSize * 0.5f,
        center = palmCenter,
        alpha = 0.95f
    )
    
    // Finger 1 (top left)
    val finger1 = rotatePoint(Offset(xPos - handSize * 0.4f, yPos - handSize * 0.7f), center, rotation)
    drawCircle(
        color = color,
        radius = handSize * 0.35f,
        center = finger1,
        alpha = 0.95f
    )
    
    // Finger 2 (top center-left)
    val finger2 = rotatePoint(Offset(xPos - handSize * 0.15f, yPos - handSize * 0.85f), center, rotation)
    drawCircle(
        color = color,
        radius = handSize * 0.35f,
        center = finger2,
        alpha = 0.95f
    )
    
    // Finger 3 (top center-right)
    val finger3 = rotatePoint(Offset(xPos + handSize * 0.15f, yPos - handSize * 0.85f), center, rotation)
    drawCircle(
        color = color,
        radius = handSize * 0.35f,
        center = finger3,
        alpha = 0.95f
    )
    
    // Finger 4 (top right)
    val finger4 = rotatePoint(Offset(xPos + handSize * 0.4f, yPos - handSize * 0.7f), center, rotation)
    drawCircle(
        color = color,
        radius = handSize * 0.35f,
        center = finger4,
        alpha = 0.95f
    )
}
