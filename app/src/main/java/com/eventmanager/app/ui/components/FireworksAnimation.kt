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
fun FireworksAnimation(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Check if current date is Dec 31 or Jan 1
    val calendar = Calendar.getInstance()
    val month = calendar.get(Calendar.MONTH) // 0-11
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    
    val isNewYearSeason = (month == Calendar.DECEMBER && day == 31) || (month == Calendar.JANUARY && day == 1)
    val shouldShowFireworks = enabled && isNewYearSeason
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (shouldShowFireworks) {
            // Generate firework bursts
            data class FireworkParticle(
                val startX: Float,
                val startY: Float,
                val velocityX: Float,
                val velocityY: Float,
                val color: Color,
                val size: Float,
                val lifespan: Float // 0-1, used for opacity fade
            )
            
            val fireworks = remember {
                val rnd = Random(System.nanoTime())
                val particleList = mutableListOf<FireworkParticle>()
                
                // Create 5 bursts at different positions
                repeat(5) { burstIndex ->
                    // Random position for each burst
                    val burstX = rnd.nextFloat() * 0.8f + 0.1f
                    val burstY = rnd.nextFloat() * 0.6f + 0.1f
                    
                    // Firework colors (bright and festive)
                    val colors = listOf(
                        Color(0xFFFF1493), // Deep Pink
                        Color(0xFFFFD700), // Gold
                        Color(0xFF00BFFF), // Deep Sky Blue
                        Color(0xFF00FF00), // Lime Green
                        Color(0xFFFF4500), // Orange Red
                        Color(0xFF00CED1)  // Dark Turquoise
                    )
                    
                    // Create particles for this burst
                    repeat(60) { _ ->
                        val angle = rnd.nextFloat() * 2f * PI.toFloat()
                        val speed = rnd.nextFloat() * 3f + 1f
                        
                        particleList.add(
                            FireworkParticle(
                                startX = burstX,
                                startY = burstY,
                                velocityX = cos(angle) * speed,
                                velocityY = sin(angle) * speed,
                                color = colors[rnd.nextInt(colors.size)],
                                size = rnd.nextFloat() * 4f + 2f,
                                lifespan = rnd.nextFloat() * 0.3f + 0.3f
                            )
                        )
                    }
                }
                
                particleList
            }
            
            // Infinite animation for fireworks bursts
            val infinite = rememberInfiniteTransition(label = "fireworks_animation")
            
            val fireworksProgress by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "fireworks"
            )
            
            // Burst timing for staggered explosions
            val burstTiming by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 10000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "burst_timing"
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                fireworks.forEachIndexed { index, particle ->
                    // Determine which burst this particle belongs to based on index
                    val burstIndex = index / 60 // 60 particles per burst
                    val particleInBurst = index % 60
                    
                    // Calculate burst start time for staggered effect
                    val burstStartTime = (burstIndex * 0.8f) // Offset each burst
                    val timeSinceBurst = (fireworksProgress + burstTiming) % 5f - burstStartTime
                    
                    // Only show if within lifespan
                    if (timeSinceBurst >= 0f && timeSinceBurst <= particle.lifespan) {
                        val progress = timeSinceBurst / particle.lifespan
                        
                        // Gravity effect
                        val gravity = 0.3f
                        val vx = particle.velocityX * progress
                        val vy = particle.velocityY * progress + gravity * progress * progress
                        
                        val x = particle.startX * width + vx * width * 0.15f
                        val y = particle.startY * height + vy * height * 0.15f
                        
                        // Fade out effect (alpha decreases as particle ages)
                        val alpha = (1f - progress).coerceIn(0f, 1f)
                        
                        // Draw firework particle
                        drawCircle(
                            color = particle.color.copy(alpha = alpha * 0.9f),
                            radius = particle.size,
                            center = Offset(x, y)
                        )
                        
                        // Add glow effect with semi-transparent circle
                        drawCircle(
                            color = particle.color.copy(alpha = alpha * 0.3f),
                            radius = particle.size * 4f,
                            center = Offset(x, y)
                        )
                        
                        // Add larger outer glow for extra brilliance
                        drawCircle(
                            color = particle.color.copy(alpha = alpha * 0.15f),
                            radius = particle.size * 6f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}
