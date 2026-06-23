package com.eventmanager.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

private const val LEONARDO_FIRST = "leonardo"
private const val LEONARDO_LAST = "mondada"

fun isLeonardoMondadaProfile(firstName: String, lastNameOrAbbreviation: String): Boolean {
    val first = firstName.trim().lowercase()
    val last = lastNameOrAbbreviation.trim().lowercase()
    return first.contains(LEONARDO_FIRST) && (last.contains(LEONARDO_LAST) || last == "m")
}

@Composable
fun ProfileEasterEggBackground(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    Canvas(modifier.fillMaxSize()) {
        val t = System.currentTimeMillis() / 1000f
        for (i in 0 until 12) {
            val angle = (i * 30f + t * 20f) % 360f
            val radius = size.minDimension * (0.25f + 0.02f * sin(t + i))
            val cx = center.x + radius * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
            val cy = center.y + radius * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
            drawCircle(
                color = Color(0x33FFD700),
                radius = 18f + i,
                center = Offset(cx, cy)
            )
        }
    }
}

@Composable
fun ProfileEasterEggConfetti(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    val particles = rememberConfettiParticles()
    Canvas(modifier.fillMaxSize()) {
        particles.forEach { p ->
            rotate(p.rotation, center) {
                drawCircle(color = p.color, radius = p.size, center = p.offset)
            }
        }
    }
}

@Composable
fun StaffObfuscatedQrPreview(
    qrImage: ImageBitmap,
    isPhone: Boolean,
    isTabletDevice: Boolean,
    tabletQrSize: Dp,
    modifier: Modifier = Modifier
) {
    val size = when {
        isPhone -> 220.dp
        isTabletDevice -> tabletQrSize
        else -> 280.dp
    }
    Box(modifier) {
        androidx.compose.foundation.Image(
            bitmap = qrImage,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.15f
        )
        androidx.compose.foundation.Image(
            bitmap = qrImage,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun rememberConfettiParticles(): List<ConfettiParticle> {
    return androidx.compose.runtime.remember {
        List(24) {
            ConfettiParticle(
                offset = Offset(Random.nextFloat() * 800f, Random.nextFloat() * 1200f),
                color = Color(
                    red = Random.nextFloat(),
                    green = Random.nextFloat(),
                    blue = Random.nextFloat(),
                    alpha = 0.85f
                ),
                size = 4f + Random.nextFloat() * 6f,
                rotation = Random.nextFloat() * 360f
            )
        }
    }
}

fun leonardoEasterEggProfileNameColors(): Pair<Color, Color> =
    Color(0xFFFFD700) to Color(0xFFFFA500)

@Composable
fun leonardoEasterEggHeaderIconTint(): Color = Color(0xFFFFD700)

private data class ConfettiParticle(
    val offset: Offset,
    val color: Color,
    val size: Float,
    val rotation: Float
)
