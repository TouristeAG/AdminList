package com.eventmanager.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import com.eventmanager.app.data.sync.AppLogger
import kotlinx.coroutines.isActive
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private const val LOG_TAG = "TopographicBackground"

@Composable
internal actual fun TopographicBackgroundPlatform(
    lineColors: BackgroundLineColors,
    config: TopographicConfig,
    animationMultiplier: Float,
    modifier: Modifier,
) {
    val effect = remember {
        try {
            RuntimeEffect.makeForShader(TOPOGRAPHIC_SKSL)
        } catch (t: Throwable) {
            AppLogger.e(LOG_TAG, "GPU shader compile failed, using CPU fallback", t)
            null
        }
    }

    if (effect == null) {
        TopographicBackgroundCpu(
            lineColors = lineColors,
            config = config,
            animationMultiplier = animationMultiplier,
            modifier = modifier,
        )
        return
    }

    val builder = remember(effect) { RuntimeShaderBuilder(effect) }

    SideEffect {
        applyTopographicConfigUniforms(setFloat = { name, value -> builder.uniform(name, value) }, config = config)
        applyTopographicColorUniforms(
            setUniform4 = { name, r, g, b, a -> builder.uniform(name, r, g, b, a) },
            lineColors = lineColors,
        )
    }

    val pauseMotion = LocalPauseBackgroundMotion.current
    val durationMs = remember(config, animationMultiplier) {
        topographicAnimationDurationMillis(config, animationMultiplier).toFloat().coerceAtLeast(1f)
    }
    var time by remember { mutableFloatStateOf(0f) }

    // Drive time from frames only while motion is allowed. Pausing stops invalidation so a parent
    // graphicsLayer can reuse its offscreen buffer during space entrances.
    LaunchedEffect(pauseMotion, durationMs) {
        if (pauseMotion) return@LaunchedEffect
        val timeAtResume = time
        val startNs = withFrameNanos { it }
        // Time stays derived from (now - startNs), so throttling the writes cannot drift.
        var lastEmitNs = startNs - BackgroundFrameIntervalNanos
        while (isActive) {
            withFrameNanos { now ->
                if (now - lastEmitNs < BackgroundFrameIntervalNanos) return@withFrameNanos
                lastEmitNs = now
                val elapsedMs = (now - startNs) / 1_000_000f
                time = (timeAtResume + (elapsedMs / durationMs) * 3600f) % 3600f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.minDimension <= 0f) return@Canvas
        builder.uniform("iTime", time)
        drawRect(
            topLeft = Offset.Zero,
            size = size,
            brush = ShaderBrush(builder.makeShader()),
        )
    }
}
