package com.eventmanager.app.ui.transitions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.eventmanager.app.ui.components.LocalPauseBackgroundMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * False while a [DramaticSpaceEntrance] animation is running; true once it has settled
 * (after a short delay when entrances are disabled). Use to defer heavy work (sync, DB
 * refresh) so it does not contend with the last frames of the transition.
 */
val LocalSpaceEntranceSettled = compositionLocalOf { true }

/** Brief calm after the spring settles before kicking off IO-heavy workspace sync. */
private const val SpaceEntranceSyncGraceMs = 48L

/**
 * When entrances are disabled, still wait a beat so the first workspace frames can commit
 * before full sync / Room refresh contend with input (ANR risk on slow tablets).
 */
private const val SpaceEntranceDisabledSettleMs = 400L

/**
 * Runs [block] only after the surrounding [DramaticSpaceEntrance] has finished (plus a short
 * grace period). Outside an entrance, or when animations are off, runs after the disabled-settle
 * delay then grace.
 */
@Composable
fun DeferredUntilSpaceEntranceSettled(
    key: Any? = Unit,
    block: suspend () -> Unit,
) {
    val settled = LocalSpaceEntranceSettled.current
    LaunchedEffect(settled, key) {
        if (!settled) return@LaunchedEffect
        delay(SpaceEntranceSyncGraceMs)
        block()
    }
}

/**
 * Dramatic but GPU-cheap workspace entrances (scale / fade / slide only).
 * Tuned like over-the-top marketing sites, without blur or layout thrash.
 *
 * Performance notes:
 * - Anim values are only read inside [graphicsLayer] / flash layer blocks so they do not
 *   recompose the whole space tree every frame.
 * - Continuous backgrounds are paused while the layer is active so the offscreen buffer
 *   can be transformed without re-recording the topographic / arches draw each frame.
 * - After the entrance settles, the graphicsLayer is removed so animated backgrounds
 *   are not forced through an offscreen layer at rest. Content stays composed.
 * - [LocalSpaceEntranceSettled] stays false until motion finishes so callers can defer sync.
 */
enum class SpaceEntrance {
    Welcome,
    Billeterie,
    Pos,
    Admin,
}

private data class SpaceEntranceMotion(
    val startScale: Float,
    val startOffsetXFraction: Float,
    val startOffsetYFraction: Float,
    val origin: TransformOrigin,
    val flashAlpha: Float,
)

private fun SpaceEntrance.motion(): SpaceEntranceMotion = when (this) {
    SpaceEntrance.Welcome -> SpaceEntranceMotion(
        startScale = 0.94f,
        startOffsetXFraction = 0f,
        startOffsetYFraction = 0.05f,
        origin = TransformOrigin(0.5f, 0.42f),
        flashAlpha = 0.10f,
    )
    SpaceEntrance.Billeterie -> SpaceEntranceMotion(
        startScale = 1.16f,
        startOffsetXFraction = -0.10f,
        startOffsetYFraction = 0.01f,
        origin = TransformOrigin(0.12f, 0.5f),
        flashAlpha = 0.20f,
    )
    SpaceEntrance.Pos -> SpaceEntranceMotion(
        startScale = 1.12f,
        startOffsetXFraction = 0f,
        startOffsetYFraction = 0.14f,
        origin = TransformOrigin(0.5f, 0.9f),
        flashAlpha = 0.18f,
    )
    SpaceEntrance.Admin -> SpaceEntranceMotion(
        startScale = 0.78f,
        startOffsetXFraction = 0f,
        startOffsetYFraction = -0.04f,
        origin = TransformOrigin(0.5f, 0.32f),
        flashAlpha = 0.24f,
    )
}

private fun SpaceEntrance.flashColor(primary: Color, secondary: Color, tertiary: Color): Color =
    when (this) {
        SpaceEntrance.Welcome -> primary
        SpaceEntrance.Billeterie -> primary
        SpaceEntrance.Pos -> tertiary
        SpaceEntrance.Admin -> secondary
    }

@Composable
fun DramaticSpaceEntrance(
    enabled: Boolean,
    space: SpaceEntrance,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(SpaceEntranceDisabledSettleMs)
            settled = true
        }
        CompositionLocalProvider(LocalSpaceEntranceSettled provides settled) {
            Box(modifier = modifier.fillMaxSize()) {
                content()
            }
        }
        return
    }

    val motion = remember(space) { space.motion() }
    val colorScheme = MaterialTheme.colorScheme
    val flashTint = remember(space, colorScheme.primary, colorScheme.secondary, colorScheme.tertiary) {
        space.flashColor(colorScheme.primary, colorScheme.secondary, colorScheme.tertiary)
    }

    val springSpec = remember {
        spring<Float>(
            dampingRatio = 0.58f,
            stiffness = 230f,
        )
    }
    val fadeSpec = remember {
        tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
    }
    val flashSpec = remember {
        tween<Float>(durationMillis = 520, easing = FastOutSlowInEasing)
    }

    val scale = remember(space) { Animatable(motion.startScale) }
    val alpha = remember(space) { Animatable(0f) }
    val offsetProgress = remember(space) { Animatable(1f) }
    val flash = remember(space) { Animatable(motion.flashAlpha) }
    var layerActive by remember(space) { mutableStateOf(true) }
    var entranceSettled by remember(space) { mutableStateOf(false) }

    LaunchedEffect(space) {
        layerActive = true
        entranceSettled = false
        scale.snapTo(motion.startScale)
        alpha.snapTo(0f)
        offsetProgress.snapTo(1f)
        flash.snapTo(motion.flashAlpha)

        joinAll(
            launch { scale.animateTo(1f, springSpec) },
            launch { alpha.animateTo(1f, fadeSpec) },
            launch { offsetProgress.animateTo(0f, springSpec) },
            launch { flash.animateTo(0f, flashSpec) },
        )
        layerActive = false
        entranceSettled = true
    }

    CompositionLocalProvider(
        LocalPauseBackgroundMotion provides layerActive,
        LocalSpaceEntranceSettled provides entranceSettled,
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (layerActive) {
                            Modifier.graphicsLayer {
                                this.alpha = alpha.value
                                scaleX = scale.value
                                scaleY = scale.value
                                translationX = motion.startOffsetXFraction * widthPx * offsetProgress.value
                                translationY = motion.startOffsetYFraction * heightPx * offsetProgress.value
                                transformOrigin = motion.origin
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                content()
            }

            // Flash alpha is applied in graphicsLayer so Animatable updates do not
            // recompose [content] (and the topographic shader) every frame.
            if (layerActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = flash.value }
                        .background(flashTint),
                )
            }
        }
    }
}
