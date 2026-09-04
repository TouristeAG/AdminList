package com.eventmanager.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min
import kotlin.time.TimeSource

/** CPU fallback when GPU shaders are unavailable or fail to compile. */
@Composable
internal fun TopographicBackgroundCpu(
    lineColors: BackgroundLineColors,
    config: TopographicConfig,
    animationMultiplier: Float,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()
        val renderer = remember(lineColors, config) { TopographicCpuRenderer(config) }

        val pauseMotion = LocalPauseBackgroundMotion.current
        var time by remember { mutableFloatStateOf(0f) }
        var frameTick by remember { mutableIntStateOf(0) }
        var buckets by remember { mutableStateOf<Array<ArrayList<Offset>>?>(null) }

        LaunchedEffect(lineColors, config, maxWidth, maxHeight, animationMultiplier, pauseMotion) {
            while (isActive) {
                if (pauseMotion) {
                    delay(50)
                    continue
                }
                if (maxWidth <= 0f || maxHeight <= 0f) {
                    delay(16)
                    continue
                }
                val frameStart = TimeSource.Monotonic.markNow()
                withContext(Dispatchers.Default) {
                    renderer.computeBuckets(time, maxWidth, maxHeight)
                }
                buckets = renderer.buckets
                frameTick++
                time += 0.012f
                val elapsed = frameStart.elapsedNow().inWholeMilliseconds
                delay((100L - elapsed).coerceAtLeast(8L))
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.minDimension <= 0f) return@Canvas
            @Suppress("UNUSED_VARIABLE")
            val tick = frameTick
            buckets?.forEachIndexed { index, points ->
                if (points.isEmpty()) return@forEachIndexed
                drawPoints(
                    points = points,
                    pointMode = PointMode.Points,
                    color = lineColors.forIndex(index),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private class TopographicCpuRenderer(
    private val config: TopographicConfig,
) {
    val buckets = Array(4) { ArrayList<Offset>(4096) }

    fun computeBuckets(time: Float, targetWidth: Float, targetHeight: Float) {
        buckets.forEach { it.clear() }

        val width = min(targetWidth, 480f).toInt().coerceAtLeast(1)
        val height = (width * (targetHeight / targetWidth)).toInt().coerceAtLeast(1)
        val scaleX = targetWidth / width
        val scaleY = targetHeight / height
        val step = 2

        var y = 0
        while (y < height) {
            val screenY = y * scaleY
            var x = 0
            while (x < width) {
                val band = contourBandAt(x * scaleX, screenY, time, config)
                if (band != null) {
                    val point = Offset(
                        x = x * scaleX,
                        y = screenY,
                    )
                    buckets[band % 4].add(point)
                }
                x += step
            }
            y += step
        }
    }
}

private fun contourBandAt(x: Float, y: Float, time: Float, config: TopographicConfig): Int? {
    val raw = SimplexNoise3D.noise(x * config.zoom, y * config.zoom, time * config.timeScale)
    val normalized = (raw + 1f) * 0.5f
    val scaled = config.bands * normalized
    val rounded = ceil(scaled)
    val roundingError = rounded - scaled
    return if (roundingError <= config.edgeThreshold) {
        rounded.toInt()
    } else {
        null
    }
}
