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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import java.util.Calendar

@Composable
fun PrideAnimation(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Check if current date is June 28 (Pride Day)
    val calendar = Calendar.getInstance()
    val month = calendar.get(Calendar.MONTH) // 0-11, so June is 5
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    
    val isPrideDay = month == Calendar.JUNE && day == 28
    val shouldShowPride = enabled && isPrideDay
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (shouldShowPride) {
            // Pride flag colors (traditional 6-stripe rainbow)
            val prideColors = listOf(
                Color(0xFFE40303), // Red
                Color(0xFFFF8C00), // Orange
                Color(0xFFFFFF00), // Yellow
                Color(0xFF008026), // Green
                Color(0xFF24408E), // Blue
                Color(0xFF732982)  // Violet
            )
            
            // Animation for flow effect
            val infinite = rememberInfiniteTransition(label = "pride_animation")
            val flowProgress by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "flow"
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val rainbowCycleHeight = height / 6f * 6f // One full rainbow is 6 colors
                
                // Calculate offset for continuous smooth scrolling
                // The animation scrolls through multiple full cycles
                val offset = -(flowProgress * height * 2f) // Scroll downward continuously
                
                // Draw multiple copies of the rainbow pattern to ensure continuous coverage
                // We need to cover: height above screen + screen height + height below
                // Total needed: height * 3
                val numCycles = 3 // Draw 3 full rainbow cycles for seamless looping
                
                repeat(numCycles) { cycleIndex ->
                    prideColors.forEachIndexed { colorIndex, color ->
                        val baseY = (cycleIndex * height) + (colorIndex * height / 6f) + offset
                        
                        // Draw stripe
                        drawRect(
                            color = color,
                            topLeft = Offset(0f, baseY),
                            size = Size(width, height / 6f),
                            alpha = 0.95f
                        )
                    }
                }
            }
        }
    }
}
