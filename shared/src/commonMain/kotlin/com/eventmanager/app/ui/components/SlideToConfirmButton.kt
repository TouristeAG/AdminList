package com.eventmanager.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SlideToConfirmButton(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isConfirmed: Boolean = false,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val latestOnConfirm by rememberUpdatedState(onConfirm)

    val thumbSizeDp = 48.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val maxOffset by remember(trackWidthPx) {
        derivedStateOf { (trackWidthPx - thumbSizePx).coerceAtLeast(0f) }
    }

    val offsetX = remember { Animatable(0f) }
    var hasTriggered by remember { mutableStateOf(false) }

    val checkScale = remember { Animatable(0f) }
    val ringScale = remember { Animatable(0f) }
    val ringAlpha = remember { Animatable(0f) }

    LaunchedEffect(isConfirmed) {
        if (isConfirmed) {
            delay(80)
            launch {
                checkScale.animateTo(
                    1f,
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                )
            }
            launch {
                ringAlpha.snapTo(0.5f)
                ringScale.snapTo(0.4f)
                launch { ringScale.animateTo(1.3f, tween(400)) }
                ringAlpha.animateTo(0f, tween(400))
            }
        } else {
            hasTriggered = false
            if (offsetX.value > 0f) {
                offsetX.animateTo(0f, animationSpec = tween(durationMillis = 140))
            } else {
                offsetX.snapTo(0f)
            }
            checkScale.snapTo(0f)
            ringScale.snapTo(0f)
            ringAlpha.snapTo(0f)
        }
    }

    LaunchedEffect(enabled, isConfirmed) {
        if (enabled && !isConfirmed && hasTriggered) {
            hasTriggered = false
            if (offsetX.value > 0f) {
                offsetX.animateTo(0f, animationSpec = tween(durationMillis = 120))
            } else {
                offsetX.snapTo(0f)
            }
        }
    }

    val progress by remember(offsetX.value, maxOffset) {
        derivedStateOf {
            if (maxOffset > 0f) (offsetX.value / maxOffset).coerceIn(0f, 1f) else 0f
        }
    }

    val trackColor = when {
        isConfirmed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }

    val thumbColor = when {
        isConfirmed -> MaterialTheme.colorScheme.primary
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.primary
    }

    val progressFillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(trackColor)
            .onSizeChanged { trackWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (!isConfirmed && progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(28.dp))
                    .background(progressFillColor),
            )
        }

        if (isConfirmed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            )
        }

        Text(
            text = if (isConfirmed) "" else text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = (1f - progress * 1.5f).coerceIn(0f, 1f))
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = thumbSizeDp + 8.dp, end = 16.dp),
        )

        if (isConfirmed) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = ringScale.value
                        scaleY = ringScale.value
                        alpha = ringAlpha.value
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            )
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .scale(checkScale.value),
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .padding(4.dp)
                .size(thumbSizeDp)
                .clip(CircleShape)
                .background(thumbColor)
                .then(
                    if (enabled && !isConfirmed && !hasTriggered) {
                        Modifier.pointerInput(maxOffset) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (!hasTriggered) {
                                        scope.launch {
                                            offsetX.animateTo(0f, animationSpec = tween(300))
                                        }
                                    }
                                },
                                onDragCancel = {
                                    if (!hasTriggered) {
                                        scope.launch {
                                            offsetX.animateTo(0f, animationSpec = tween(300))
                                        }
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    scope.launch {
                                        val newVal = (offsetX.value + dragAmount).coerceIn(0f, maxOffset)
                                        offsetX.snapTo(newVal)
                                        if (maxOffset > 0f && newVal >= maxOffset * 0.9f && !hasTriggered) {
                                            hasTriggered = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            offsetX.animateTo(maxOffset, animationSpec = tween(150))
                                            latestOnConfirm()
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isConfirmed) Icons.Default.Check else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
