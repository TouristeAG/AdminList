package com.eventmanager.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eventmanager.app.utils.QRCodeUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val LEONARDO_FIRST = "leonardo"
private const val LEONARDO_LAST = "mondada"

fun isLeonardoMondadaProfile(firstName: String, lastNameOrAbbreviation: String): Boolean {
    val first = firstName.trim().lowercase()
    val last = lastNameOrAbbreviation.trim().lowercase()
    return first.contains(LEONARDO_FIRST) && (last.contains(LEONARDO_LAST) || last == "m")
}

/**
 * Wraps profile content with Leonardo easter-egg visuals.
 * Background orbs sit behind content; tap confetti is drawn on top without blocking scroll.
 */
@Composable
fun ProfileEasterEggHost(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val burstParticles = remember { mutableStateListOf<BurstParticle>() }
    var frameTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            burstParticles.clear()
            frameTimeMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { frameNanos ->
                val nowMs = frameNanos / 1_000_000L
                if (burstParticles.isNotEmpty()) {
                    frameTimeMs = nowMs
                    burstParticles.removeAll { it.isExpired(nowMs) }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val nowMs = System.currentTimeMillis()
                                burstParticles.addAll(createBurstParticles(offset, nowMs))
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            drawBurstConfetti(burstParticles, frameTimeMs)
                        }
                } else {
                    Modifier
                }
            )
    ) {
        ProfileEasterEggBackground(enabled = enabled)
        content()
    }
}

@Composable
fun ProfileEasterEggBackground(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return

    val infiniteTransition = rememberInfiniteTransition(label = "leonardo_orbs")
    val orbPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orb_rotation",
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb_pulse",
    )

    Canvas(modifier.fillMaxSize()) {
        for (i in 0 until 12) {
            val angleRad = Math.toRadians((i * 30f + orbPhase).toDouble())
            val radius = size.minDimension * (0.22f + 0.04f * pulse) * (0.9f + 0.1f * i / 12f)
            val cx = center.x + radius * cos(angleRad).toFloat()
            val cy = center.y + radius * sin(angleRad).toFloat()
            drawCircle(
                color = Color(0x33FFD700),
                radius = (14f + i * 1.5f) * pulse,
                center = Offset(cx, cy),
            )
        }
    }
}

/** @deprecated Use [ProfileEasterEggHost] — kept for call sites being migrated. */
@Composable
fun ProfileEasterEggConfetti(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
}

@Composable
fun StaffObfuscatedQrPreview(
    qrPayload: String,
    isPhone: Boolean,
    isTabletDevice: Boolean,
    tabletQrSize: Dp,
    modifier: Modifier = Modifier,
) {
    val size = when {
        isPhone -> 220.dp
        isTabletDevice -> tabletQrSize
        else -> 280.dp
    }
    val obfuscated = remember(qrPayload) {
        QRCodeUtils.generateStaffObfuscatedQrImageBitmap(qrPayload, 512)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (isPhone) 8.dp else 12.dp))
            .background(Color.White)
    ) {
        if (obfuscated != null) {
            Image(
                bitmap = obfuscated,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp),
            )
        }
    }
}

fun leonardoEasterEggProfileNameColors(): Pair<Color, Color> =
    Color(0xFFFFD700) to Color(0xFFFFA500)

@Composable
fun leonardoEasterEggHeaderIconTint(): Color = Color(0xFFFFD700)

private data class BurstParticle(
    val origin: Offset,
    val velocity: Offset,
    val color: Color,
    val size: Float,
    val rotationSpeed: Float,
    val startRotation: Float,
    val bornAtMs: Long,
    val lifetimeMs: Long,
) {
    fun isExpired(nowMs: Long): Boolean = nowMs - bornAtMs > lifetimeMs

    fun positionAt(nowMs: Long): Offset {
        val t = ((nowMs - bornAtMs).coerceAtLeast(0L).toFloat() / 1_000f)
        return Offset(
            x = origin.x + velocity.x * t,
            y = origin.y + velocity.y * t + 120f * t * t,
        )
    }

    fun alphaAt(nowMs: Long): Float {
        val progress = ((nowMs - bornAtMs).toFloat() / lifetimeMs.toFloat()).coerceIn(0f, 1f)
        return (1f - progress).coerceIn(0f, 1f)
    }

    fun rotationAt(nowMs: Long): Float =
        startRotation + rotationSpeed * ((nowMs - bornAtMs).coerceAtLeast(0L).toFloat() / 1_000f)
}

private fun createBurstParticles(origin: Offset, nowMs: Long): List<BurstParticle> {
    val random = Random(nowMs xor origin.x.toBits().toLong())
    return List(18) {
        val angle = random.nextFloat() * 360f
        val speed = 80f + random.nextFloat() * 160f
        val angleRad = Math.toRadians(angle.toDouble())
        BurstParticle(
            origin = origin,
            velocity = Offset(
                x = cos(angleRad).toFloat() * speed,
                y = sin(angleRad).toFloat() * speed - 60f,
            ),
            color = Color(
                red = 0.7f + random.nextFloat() * 0.3f,
                green = 0.5f + random.nextFloat() * 0.5f,
                blue = random.nextFloat() * 0.6f,
                alpha = 0.9f,
            ),
            size = 3f + random.nextFloat() * 5f,
            rotationSpeed = random.nextFloat() * 720f - 360f,
            startRotation = random.nextFloat() * 360f,
            bornAtMs = nowMs,
            lifetimeMs = 900L + random.nextLong(400L),
        )
    }
}

private fun DrawScope.drawBurstConfetti(particles: List<BurstParticle>, nowMs: Long) {
    particles.forEach { particle ->
        val alpha = particle.alphaAt(nowMs)
        if (alpha <= 0.01f) return@forEach
        val position = particle.positionAt(nowMs)
        rotate(particle.rotationAt(nowMs), position) {
            drawCircle(
                color = particle.color.copy(alpha = particle.color.alpha * alpha),
                radius = particle.size,
                center = position,
            )
        }
    }
}
