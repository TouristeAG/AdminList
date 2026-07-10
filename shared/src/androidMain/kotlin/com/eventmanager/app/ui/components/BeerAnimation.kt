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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import com.eventmanager.app.data.sync.settingsManagerFor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BeerAnimation(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { settingsManagerFor(context) }
    val animationMultiplier = remember { settingsManager.getAnimationIntensityMultiplier() }
    
    // Skip animation entirely if reduced motion is enabled
    val shouldAnimate = enabled && animationMultiplier > 0f
    
    // PERFORMANCE OPTIMIZATION: Determine if we're in performance mode
    val performanceMode = animationMultiplier < 1.0f
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (shouldAnimate) {
            // Animation for beer wave flowing from top to bottom
            val infinite = rememberInfiniteTransition(label = "beer_animation")
            
            val waveProgress by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing), // Fast animation
                    repeatMode = RepeatMode.Restart
                ),
                label = "beer_wave"
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Beer colors
                val beerColor = Color(0xFFFFD700) // Golden yellow
                val beerColorDark = Color(0xFFE6C200) // Darker yellow for depth
                val foamColor = Color(0xFFFFFFFF) // White foam
                
                // Draw multiple wave layers for illustration effect
                val waveHeight = height * 0.5f // Height of the wave band
                val waveSpeed = -waveHeight + waveProgress * (height + waveHeight * 2f) // Start above screen, end below
                
                // PERFORMANCE OPTIMIZATION: Reduce layers in performance mode (3 -> 2)
                val layerCount = if (performanceMode) 2 else 3
                
                // Draw wave layers for depth and illustration style
                for (layer in 0 until layerCount) {
                    val layerOffset = waveSpeed - (layer * waveHeight * 0.25f)
                    val layerAlpha = 1f - (layer * 0.15f)
                    val layerAmplitude = 25f - (layer * 6f)
                    
                    if (layerOffset > -waveHeight && layerOffset < height + waveHeight) {
                        drawBeerWave(
                            width = width,
                            height = height,
                            waveY = layerOffset,
                            waveHeight = waveHeight,
                            amplitude = layerAmplitude,
                            color = if (layer == 0) beerColor else beerColorDark,
                            alpha = layerAlpha,
                            performanceMode = performanceMode
                        )
                    }
                }
                
                // Draw white foam on top of the wave
                val foamY = waveSpeed - waveHeight * 0.15f
                if (foamY > -waveHeight * 0.3f && foamY < height + waveHeight) {
                    drawFoam(
                        width = width,
                        height = height,
                        foamY = foamY,
                        waveHeight = waveHeight,
                        color = foamColor,
                        performanceMode = performanceMode
                    )
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun DrawScope.drawBeerWave(
    width: Float,
    height: Float,
    waveY: Float,
    waveHeight: Float, // Kept for API consistency with drawFoam
    amplitude: Float,
    color: Color,
    alpha: Float,
    performanceMode: Boolean = false
) {
    val path = Path()
    val waveLength = width * 0.8f
    // PERFORMANCE OPTIMIZATION: Reduce segments in performance mode (60 -> 30)
    val segments = if (performanceMode) 30 else 60
    
    // Start path from left edge
    path.moveTo(0f, waveY)
    
    // Create wavy top edge
    for (i in 0..segments) {
        val x = (i.toFloat() / segments) * width
        val wavePhase = (x / waveLength) * 2f * PI.toFloat() + (waveY / 50f)
        val y = waveY + sin(wavePhase) * amplitude
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    // Complete the path to create a filled shape
    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()
    
    // Draw the beer wave
    drawPath(
        path = path,
        color = color.copy(alpha = alpha)
    )
}

private fun DrawScope.drawFoam(
    width: Float,
    height: Float,
    foamY: Float,
    waveHeight: Float,
    color: Color,
    performanceMode: Boolean = false
) {
    val foamThickness = waveHeight * 0.15f
    val foamAmplitude = 15f
    // PERFORMANCE OPTIMIZATION: Reduce segments in performance mode (80 -> 40)
    val segments = if (performanceMode) 40 else 80
    
    // Draw multiple foam bubbles/layers
    for (foamLayer in 0..2) {
        val layerY = foamY - (foamLayer * foamThickness * 0.3f)
        val layerAmplitude = foamAmplitude * (1f - foamLayer * 0.3f)
        val layerAlpha = 0.9f - (foamLayer * 0.2f)
        
        val foamPath = Path()
        val waveLength = width * 0.6f
        
        // Create bubbly foam top edge
        foamPath.moveTo(0f, layerY)
        
        for (i in 0..segments) {
            val x = (i.toFloat() / segments) * width
            // Use cosine for smoother, bubbly effect
            val wavePhase = (x / waveLength) * 2f * PI.toFloat() + (layerY / 40f)
            val bubblePhase = (x / (waveLength * 0.3f)) * 2f * PI.toFloat()
            val y = layerY + cos(wavePhase) * layerAmplitude + sin(bubblePhase) * (layerAmplitude * 0.3f)
            
            if (i == 0) {
                foamPath.moveTo(x, y)
            } else {
                foamPath.lineTo(x, y)
            }
        }
        
        // Complete foam shape
        foamPath.lineTo(width, layerY + foamThickness)
        foamPath.lineTo(0f, layerY + foamThickness)
        foamPath.close()
        
        // Draw foam with slight transparency for depth
        drawPath(
            path = foamPath,
            color = color.copy(alpha = layerAlpha)
        )
    }
    
    // PERFORMANCE OPTIMIZATION: Reduce or skip bubbles in performance mode
    // Normal: 16 bubbles, Performance Mode: 8 bubbles
    val bubbleCount = if (performanceMode) 8 else 16
    
    // Draw small foam bubbles for extra detail
    for (i in 0 until bubbleCount) {
        val bubbleX = (i * width / bubbleCount.toFloat()) + (foamY / 20f) % (width / bubbleCount.toFloat())
        val bubbleY = foamY + (i % 3) * (foamThickness / 3f)
        val bubbleSize = 3f + (i % 2) * 2f
        
        if (bubbleY > 0 && bubbleY < height) {
            drawCircle(
                color = color.copy(alpha = 0.7f),
                radius = bubbleSize,
                center = Offset(bubbleX, bubbleY)
            )
        }
    }
}

