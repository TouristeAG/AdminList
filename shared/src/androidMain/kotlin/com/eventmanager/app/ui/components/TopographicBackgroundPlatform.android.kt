package com.eventmanager.app.ui.components

import android.graphics.RuntimeShader
import android.os.Build
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

private const val LOG_TAG = "TopographicBackground"

@Composable
internal actual fun TopographicBackgroundPlatform(
    lineColors: BackgroundLineColors,
    config: TopographicConfig,
    animationMultiplier: Float,
    modifier: Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val runtimeShader = remember {
            try {
                RuntimeShader(TOPOGRAPHIC_AGSL)
            } catch (t: Throwable) {
                AppLogger.e(LOG_TAG, "GPU shader compile failed, using CPU fallback", t)
                null
            }
        }
        if (runtimeShader != null) {
            TopographicBackgroundGpu(
                runtimeShader = runtimeShader,
                lineColors = lineColors,
                config = config,
                animationMultiplier = animationMultiplier,
                modifier = modifier,
            )
            return
        }
    }

    TopographicBackgroundCpu(
        lineColors = lineColors,
        config = config,
        animationMultiplier = animationMultiplier,
        modifier = modifier,
    )
}

@Composable
private fun TopographicBackgroundGpu(
    runtimeShader: RuntimeShader,
    lineColors: BackgroundLineColors,
    config: TopographicConfig,
    animationMultiplier: Float,
    modifier: Modifier,
) {
    SideEffect {
        applyTopographicConfigUniforms(setFloat = { name, value -> runtimeShader.setFloatUniform(name, value) }, config = config)
        applyTopographicColorUniforms(
            setUniform4 = { name, r, g, b, a -> runtimeShader.setFloatUniform(name, r, g, b, a) },
            lineColors = lineColors,
        )
    }

    val pauseMotion = LocalPauseBackgroundMotion.current
    val durationMs = remember(config, animationMultiplier) {
        topographicAnimationDurationMillis(config, animationMultiplier).toFloat().coerceAtLeast(1f)
    }
    var time by remember { mutableFloatStateOf(0f) }

    // Drive time from frames only while motion is allowed. Pausing stops invalidation so a parent
    // graphicsLayer can reuse its offscreen buffer during space entrances. Same linear 0 -> 3600
    // sweep as before, sampled at the shared background frame budget instead of the display rate.
    LaunchedEffect(pauseMotion, durationMs) {
        if (pauseMotion) return@LaunchedEffect
        val timeAtResume = time
        val startNs = withFrameNanos { it }
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
        runtimeShader.setFloatUniform("iTime", time)
        // A fresh ShaderBrush per draw is required: ShaderBrush.applyTo only re-assigns the
        // Paint's shader when the instance differs, so reusing it would keep the stale native
        // shader from before setFloatUniform and freeze the pattern.
        drawRect(
            topLeft = Offset.Zero,
            size = size,
            brush = ShaderBrush(runtimeShader),
        )
    }
}
