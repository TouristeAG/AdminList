package com.eventmanager.app.ui.components

import androidx.compose.ui.graphics.Color

object BackgroundAnimationStyle {
    const val NONE = "none"
    const val ARCHES = "arches"
    const val TOPOGRAPHIC = "topographic"

    fun fromStored(value: String?): String = when (value) {
        NONE, ARCHES, TOPOGRAPHIC -> value
        else -> ARCHES
    }

    fun isEnabled(style: String): Boolean = style != NONE

    fun defaultOpacity(style: String): Float = when (style) {
        TOPOGRAPHIC -> 0.6f
        ARCHES -> 1.0f
        else -> 1.0f
    }
}

enum class BackgroundAnimationSettingsTarget {
    Admin,
    Billeterie,
    Pos,
}

internal data class BackgroundLineColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surfaceVariant: Color,
)

internal fun backgroundTopographicLineColors(
    isDesktop: Boolean,
    colorScheme: androidx.compose.material3.ColorScheme,
): BackgroundLineColors {
    return if (isDesktop) {
        BackgroundLineColors(
            primary = colorScheme.primary.copy(alpha = 0.48f),
            secondary = colorScheme.secondary.copy(alpha = 0.42f),
            tertiary = colorScheme.tertiary.copy(alpha = 0.38f),
            surfaceVariant = colorScheme.primary.copy(alpha = 0.34f),
        )
    } else {
        BackgroundLineColors(
            primary = colorScheme.primary.copy(alpha = 0.52f),
            secondary = colorScheme.secondary.copy(alpha = 0.46f),
            tertiary = colorScheme.tertiary.copy(alpha = 0.40f),
            surfaceVariant = colorScheme.primary.copy(alpha = 0.36f),
        )
    }
}

internal fun backgroundLineColors(isDesktop: Boolean, colorScheme: androidx.compose.material3.ColorScheme): BackgroundLineColors {
    return if (isDesktop) {
        BackgroundLineColors(
            primary = colorScheme.primary.copy(alpha = 0.11f),
            secondary = colorScheme.secondary.copy(alpha = 0.09f),
            tertiary = colorScheme.tertiary.copy(alpha = 0.07f),
            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = 0.05f),
        )
    } else {
        BackgroundLineColors(
            primary = colorScheme.primary.copy(alpha = 0.15f),
            secondary = colorScheme.secondary.copy(alpha = 0.12f),
            tertiary = colorScheme.tertiary.copy(alpha = 0.10f),
            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = 0.08f),
        )
    }
}

internal fun BackgroundLineColors.forIndex(index: Int): Color = when (index % 4) {
    0 -> primary
    1 -> secondary
    2 -> tertiary
    else -> surfaceVariant
}
