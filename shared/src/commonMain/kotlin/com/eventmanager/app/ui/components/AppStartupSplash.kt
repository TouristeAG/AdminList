package com.eventmanager.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.app_name
import com.eventmanager.app.resources.startup_splash_opening
import com.eventmanager.app.resources.startup_splash_preparing
import com.eventmanager.app.resources.startup_splash_syncing
import org.jetbrains.compose.resources.stringResource

enum class StartupSplashStep {
    Opening,
    Syncing,
    Preparing,
}

@Composable
fun AppStartupSplash(
    step: StartupSplashStep,
    modifier: Modifier = Modifier,
) {
    val message = when (step) {
        StartupSplashStep.Opening -> stringResource(Res.string.startup_splash_opening)
        StartupSplashStep.Syncing -> stringResource(Res.string.startup_splash_syncing)
        StartupSplashStep.Preparing -> stringResource(Res.string.startup_splash_preparing)
    }
    val colorScheme = MaterialTheme.colorScheme
    val appName = stringResource(Res.string.app_name)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StartupSplashBrand(appName = appName)
            Spacer(Modifier.height(36.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(280.dp),
            )
            Spacer(Modifier.height(28.dp))
            StartupSplashProgressBar()
        }
    }
}

@Composable
private fun StartupSplashBrand(appName: String) {
    val colorScheme = MaterialTheme.colorScheme
    val splitAt = appName.indexOf("List").takeIf { it > 0 } ?: appName.length
    val lead = appName.substring(0, splitAt)
    val trail = appName.substring(splitAt)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface,
                        letterSpacing = 0.6.sp,
                    ),
                ) {
                    append(lead)
                }
                if (trail.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Light,
                            fontStyle = FontStyle.Italic,
                            color = colorScheme.primary,
                            letterSpacing = 2.sp,
                        ),
                    ) {
                        append(trail)
                    }
                }
            },
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 40.sp,
                lineHeight = 44.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colorScheme.primary.copy(alpha = 0.55f)),
        )
    }
}

/** Sliding pill on a soft track — indeterminate but smooth and a bit playful. */
@Composable
private fun StartupSplashProgressBar(
    modifier: Modifier = Modifier,
    barWidth: androidx.compose.ui.unit.Dp = 220.dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    val infinite = rememberInfiniteTransition(label = "startupSplashBar")
    // Kept as State (no `by`) so the animated values are only read inside the graphicsLayer
    // block below: the pill redraws every frame without recomposing during startup, when DB
    // init and the first sync are already competing for the main thread.
    val sweep = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "startupSplashBarSweep",
    )
    val shimmer = infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "startupSplashBarShimmer",
    )

    BoxWithConstraints(
        modifier = modifier
            .width(barWidth)
            .height(7.dp)
            .clip(shape)
            .background(colorScheme.primary.copy(alpha = 0.1f)),
    ) {
        val trackPx = maxWidth
        val pillWidth = maxWidth * 0.38f
        val travel = maxWidth - pillWidth
        Box(
            modifier = Modifier
                .width(pillWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = (travel * sweep.value).toPx()
                    alpha = shimmer.value
                }
                .clip(shape)
                .background(colorScheme.primary),
        )
    }
}
