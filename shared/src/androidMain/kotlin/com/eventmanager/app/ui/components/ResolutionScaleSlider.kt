package com.eventmanager.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmanager.app.data.sync.SettingsManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Custom slider for resolution scale with magnetic effect at 100%
 * Range: 80% (SMALL) to 120% (BIG) with 100% (DEFAULT) in the center
 */
@Composable
fun ResolutionScaleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val sliderWidth = 400.dp
    val sliderHeight = 120.dp
    val thumbSize = 48.dp
    val trackHeight = 16.dp
    
    // Get theme colors once
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    
    // Scale range: 0.8f (80%) to 1.2f (120%)
    val minValue = 0.8f
    val maxValue = 1.2f
    val defaultValue = 1.0f
    
    // Convert value to slider position (0.0 to 1.0) - memoized
    val normalizedValue = remember(value) {
        (value - minValue) / (maxValue - minValue)
    }
    
    // Magnetic effect threshold
    val magneticThreshold = 0.08f
    
    var isDragging by remember { mutableStateOf(false) }
    var thumbPosition by remember { mutableStateOf(normalizedValue) }
    
    // Update thumb position when value changes externally (only when not dragging)
    LaunchedEffect(normalizedValue) {
        if (!isDragging) {
            thumbPosition = normalizedValue
        }
    }
    
    // Simplified animation - no animation during drag for immediate response
    val animatedThumbPosition by animateFloatAsState(
        targetValue = thumbPosition,
        animationSpec = if (isDragging) {
            spring(dampingRatio = 1f, stiffness = Float.MAX_VALUE)
        } else {
            spring(dampingRatio = 0.8f, stiffness = 300f)
        },
        label = "thumb_position"
    )
    
    // Apply magnetic effect to default value (only when not dragging)
    LaunchedEffect(animatedThumbPosition) {
        if (!isDragging && abs(animatedThumbPosition - 0.5f) < magneticThreshold) {
            thumbPosition = 0.5f
            onValueChange(defaultValue)
        }
    }
    
    BoxWithConstraints(
        modifier = modifier
            .width(sliderWidth)
            .padding(vertical = 8.dp)
    ) {
        val density = LocalDensity.current
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top labels - SMALL, DEFAULT, BIG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // SMALL (80%) label
                Text(
                    text = "BIG",
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurfaceVariantColor,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // DEFAULT (100%) label
                Text(
                    text = "DEFAULT : 100%",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryColor,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // BIG (120%) label
                Text(
                    text = "SMALL",
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurfaceVariantColor,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Slider track and thumb
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thumbSize + 16.dp)
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val relativeX = offset.x / this@BoxWithConstraints.maxWidth.toPx()
                                val clampedX = relativeX.coerceIn(0f, 1f)
                                thumbPosition = clampedX
                                val newValue = minValue + clampedX * (maxValue - minValue)
                                onValueChange(newValue)
                            },
                            onDrag = { _, dragAmount ->
                                val relativeDrag = dragAmount.x / this@BoxWithConstraints.maxWidth.toPx()
                                val newPosition = (thumbPosition + relativeDrag).coerceIn(0f, 1f)
                                thumbPosition = newPosition
                                val newValue = minValue + newPosition * (maxValue - minValue)
                                onValueChange(newValue)
                            },
                            onDragEnd = {
                                isDragging = false
                                // Apply magnetic effect if close to default
                                if (abs(thumbPosition - 0.5f) < magneticThreshold) {
                                    thumbPosition = 0.5f
                                    onValueChange(defaultValue)
                                }
                            }
                        )
                    }
            ) {
                val trackWidthPx = with(density) { maxWidth.toPx() }
                val thumbSizePx = with(density) { thumbSize.toPx() }
                
                // Track background - optimized drawing
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .align(Alignment.Center)
                ) {
                    val trackY = size.height / 2
                    val trackStart = 0f
                    val trackEnd = size.width
                    val trackCenter = size.width / 2
                    
                    // Pre-calculate values for better performance
                    val thumbCenterX = trackStart + animatedThumbPosition * (trackEnd - trackStart)
                    val trackStrokeWidth = trackHeight.toPx()
                    val centerMarkerStrokeWidth = 3.dp.toPx()
                    
                    // Draw track background with two-tone effect
                    // Left part of track (before thumb center)
                    drawLine(
                        color = primaryColor,
                        start = Offset(trackStart, trackY),
                        end = Offset(thumbCenterX, trackY),
                        strokeWidth = trackStrokeWidth,
                        cap = StrokeCap.Round
                    )
                    
                    // Right part of track (after thumb center)
                    drawLine(
                        color = outlineColor.copy(alpha = 0.3f),
                        start = Offset(thumbCenterX, trackY),
                        end = Offset(trackEnd, trackY),
                        strokeWidth = trackStrokeWidth,
                        cap = StrokeCap.Round
                    )
                    
                    // Draw center marker for default value (100%)
                    drawLine(
                        color = primaryColor.copy(alpha = 0.8f),
                        start = Offset(trackCenter, trackY - trackStrokeWidth / 2),
                        end = Offset(trackCenter, trackY + trackStrokeWidth / 2),
                        strokeWidth = centerMarkerStrokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                
                // Thumb - optimized positioning
                val thumbCenterX = animatedThumbPosition * trackWidthPx
                val thumbLeftX = thumbCenterX - thumbSizePx / 2
                
                // Memoize thumb colors for better performance
                val thumbBackgroundColor = if (isDragging) primaryColor else primaryContainerColor
                val thumbTextColor = if (isDragging) onPrimaryColor else onPrimaryContainerColor
                
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { thumbLeftX.toDp() },
                            y = 8.dp
                        )
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(thumbBackgroundColor)
                ) {
                    // Thumb content - memoized text
                    val percentageText = remember(value) {
                        "${(value * 100).roundToInt()}%"
                    }
                    
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = percentageText,
                            style = MaterialTheme.typography.titleLarge,
                            color = thumbTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
