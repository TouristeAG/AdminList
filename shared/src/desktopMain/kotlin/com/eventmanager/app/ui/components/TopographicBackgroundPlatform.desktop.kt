package com.eventmanager.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import com.eventmanager.app.data.sync.AppLogger
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

    val infinite = rememberInfiniteTransition(label = "topographic_shader")
    val time by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = topographicAnimationDurationMillis(config, animationMultiplier),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "topographic_time",
    )

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
