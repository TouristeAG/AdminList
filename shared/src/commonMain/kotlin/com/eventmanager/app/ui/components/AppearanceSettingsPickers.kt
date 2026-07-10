package com.eventmanager.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbShade
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.color_theme_custom
import com.eventmanager.app.resources.color_theme_description
import com.eventmanager.app.resources.color_theme_neutral_green
import com.eventmanager.app.resources.color_theme_neutral_purple
import com.eventmanager.app.resources.color_theme_professional_blue
import com.eventmanager.app.resources.color_theme_rich_brown
import com.eventmanager.app.resources.color_theme_sunset_mist
import com.eventmanager.app.resources.color_theme_system
import com.eventmanager.app.resources.color_theme_title
import com.eventmanager.app.resources.color_theme_warm_gray
import com.eventmanager.app.resources.desktop_admin_nav_layout_bottom
import com.eventmanager.app.resources.desktop_admin_nav_layout_description
import com.eventmanager.app.resources.desktop_admin_nav_layout_left
import com.eventmanager.app.resources.desktop_admin_nav_layout_right
import com.eventmanager.app.resources.desktop_admin_nav_layout_title
import com.eventmanager.app.resources.scroll_behavior_description
import com.eventmanager.app.resources.scroll_behavior_full_page_description
import com.eventmanager.app.resources.scroll_behavior_full_page_title
import com.eventmanager.app.resources.scroll_behavior_list_only_description
import com.eventmanager.app.resources.scroll_behavior_list_only_title
import com.eventmanager.app.resources.scroll_behavior_sticky_filters_description
import com.eventmanager.app.resources.scroll_behavior_sticky_filters_option
import com.eventmanager.app.resources.scroll_behavior_title
import com.eventmanager.app.resources.theme_dark
import com.eventmanager.app.resources.theme_default
import com.eventmanager.app.resources.theme_description
import com.eventmanager.app.resources.theme_light
import com.eventmanager.app.resources.theme_title
import com.eventmanager.app.ui.theme.ColorThemes
import com.eventmanager.app.ui.theme.ThemeMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class ColorThemeOption(
    val key: String,
    val labelRes: StringResource,
    val icon: ImageVector,
)

private val defaultColorThemeOptions = listOf(
    ColorThemeOption("system", Res.string.color_theme_system, Icons.Default.AutoAwesome),
    ColorThemeOption("professional_blue", Res.string.color_theme_professional_blue, Icons.Default.WaterDrop),
    ColorThemeOption("neutral_green", Res.string.color_theme_neutral_green, Icons.Default.Spa),
    ColorThemeOption("warm_gray", Res.string.color_theme_warm_gray, Icons.Default.WbShade),
    ColorThemeOption("neutral_purple", Res.string.color_theme_neutral_purple, Icons.Default.Interests),
    ColorThemeOption("rich_brown", Res.string.color_theme_rich_brown, Icons.Default.LocalCafe),
    ColorThemeOption("sunset_mist", Res.string.color_theme_sunset_mist, Icons.Default.BlurOn),
)

@Composable
fun ThemeModePicker(
    selectedMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val options = listOf(
        ThemeMode.LIGHT to (Icons.Default.WbSunny to Res.string.theme_light),
        ThemeMode.DARK to (Icons.Default.NightlightRound to Res.string.theme_dark),
        ThemeMode.DEFAULT to (Icons.Default.AutoAwesome to Res.string.theme_default),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            Text(text = stringResource(Res.string.theme_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { (mode, iconLabel) ->
                val (icon, labelRes) = iconLabel
                val selected = selectedMode == mode
                val label = stringResource(labelRes)
                AppearanceOptionCard(
                    selected = selected,
                    onClick = { onSelect(mode) },
                    modifier = Modifier
                        .width(132.dp)
                        .semantics { contentDescription = label },
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        ThemeModePreviewMockup(
                            mode = mode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModePreviewMockup(mode: ThemeMode, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
    ) {
        when (mode) {
            ThemeMode.LIGHT -> MiniAppChromePreview(
                background = Color(0xFFFFFBFE),
                surface = Color(0xFFF3EDF7),
                onSurface = Color(0xFF1C1B1F),
                accent = Color(0xFF6750A4),
            )
            ThemeMode.DARK -> MiniAppChromePreview(
                background = Color(0xFF1C1B1F),
                surface = Color(0xFF2B2930),
                onSurface = Color(0xFFE6E1E5),
                accent = Color(0xFFD0BCFF),
            )
            ThemeMode.DEFAULT -> Row(Modifier.fillMaxSize()) {
                MiniAppChromePreview(
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFF3EDF7),
                    onSurface = Color(0xFF1C1B1F),
                    accent = Color(0xFF6750A4),
                    modifier = Modifier.weight(1f),
                )
                MiniAppChromePreview(
                    background = Color(0xFF1C1B1F),
                    surface = Color(0xFF2B2930),
                    onSurface = Color(0xFFE6E1E5),
                    accent = Color(0xFFD0BCFF),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniAppChromePreview(
    background: Color,
    surface: Color,
    onSurface: Color,
    accent: Color,
    accentSecondary: Color = accent,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(accent),
        )
        Box(
            modifier = Modifier
                .padding(top = 24.dp, start = 8.dp, end = 8.dp)
                .fillMaxWidth(0.88f)
                .height(34.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(onSurface.copy(alpha = 0.32f)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(onSurface.copy(alpha = 0.20f)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.64f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(onSurface.copy(alpha = 0.14f)),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(accentSecondary),
        )
    }
}

@Composable
fun ColorThemePicker(
    selectedThemeKey: String,
    previewDark: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    settingsManager: SettingsManager? = null,
    customThemeRefreshNonce: Int = 0,
    includeCustomTheme: Boolean = false,
    onCustomThemeReselect: (() -> Unit)? = null,
    showHeader: Boolean = true,
) {
    val options = remember(includeCustomTheme) {
        if (includeCustomTheme) {
            defaultColorThemeOptions + ColorThemeOption("custom", Res.string.color_theme_custom, Icons.Default.Tune)
        } else {
            defaultColorThemeOptions
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            Text(text = stringResource(Res.string.color_theme_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                val selected = selectedThemeKey == option.key
                val label = stringResource(option.labelRes)
                AppearanceOptionCard(
                    selected = selected,
                    onClick = {
                        when {
                            selected && option.key == "custom" -> onCustomThemeReselect?.invoke()
                            !selected -> onSelect(option.key)
                        }
                    },
                    modifier = Modifier
                        .width(148.dp)
                        .semantics { contentDescription = label },
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        ThemeColorPreviewMockup(
                            themeKey = option.key,
                            previewDark = previewDark,
                            settingsManager = settingsManager,
                            refreshNonce = customThemeRefreshNonce,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ThemePalettePreview(
                            themeKey = option.key,
                            previewDark = previewDark,
                            settingsManager = settingsManager,
                            refreshNonce = customThemeRefreshNonce,
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorPreviewMockup(
    themeKey: String,
    previewDark: Boolean,
    settingsManager: SettingsManager?,
    refreshNonce: Int,
    modifier: Modifier = Modifier,
) {
    val chrome = remember(themeKey, previewDark, refreshNonce, settingsManager) {
        themePreviewChromeColors(themeKey, previewDark, settingsManager)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
    ) {
        when (themeKey) {
            "system" -> Row(Modifier.fillMaxSize()) {
                val lightChrome = themePreviewChromeColors("professional_blue", previewDark = false, settingsManager)
                val darkChrome = themePreviewChromeColors("neutral_purple", previewDark = true, settingsManager)
                MiniAppChromePreview(
                    background = lightChrome.background,
                    surface = lightChrome.surface,
                    onSurface = lightChrome.onSurface,
                    accent = lightChrome.accent,
                    accentSecondary = lightChrome.accentSecondary,
                    modifier = Modifier.weight(1f),
                )
                MiniAppChromePreview(
                    background = darkChrome.background,
                    surface = darkChrome.surface,
                    onSurface = darkChrome.onSurface,
                    accent = darkChrome.accent,
                    accentSecondary = darkChrome.accentSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
            "custom" -> Box(Modifier.fillMaxSize()) {
                MiniAppChromePreview(
                    background = chrome.background,
                    surface = chrome.surface,
                    onSurface = chrome.onSurface,
                    accent = chrome.accent,
                    accentSecondary = chrome.accentSecondary,
                )
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = chrome.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                )
            }
            "sunset_mist" -> {
                val swatches = resolveThemePaletteSwatches(themeKey, previewDark, settingsManager)
                Box(Modifier.fillMaxSize().background(chrome.background)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        swatches[0].copy(alpha = 0.35f),
                                        swatches[1].copy(alpha = 0.28f),
                                        chrome.background,
                                    ),
                                ),
                            ),
                    )
                    MiniAppChromePreview(
                        background = Color.Transparent,
                        surface = chrome.surface,
                        onSurface = chrome.onSurface,
                        accent = chrome.accent,
                        accentSecondary = chrome.accentSecondary,
                    )
                }
            }
            else -> MiniAppChromePreview(
                background = chrome.background,
                surface = chrome.surface,
                onSurface = chrome.onSurface,
                accent = chrome.accent,
                accentSecondary = chrome.accentSecondary,
            )
        }
    }
}

@Composable
fun ThemePalettePreview(
    themeKey: String,
    previewDark: Boolean,
    settingsManager: SettingsManager? = null,
    refreshNonce: Int = 0,
    modifier: Modifier = Modifier,
) {
    val swatches = remember(themeKey, previewDark, refreshNonce, settingsManager) {
        resolveThemePaletteSwatches(themeKey, previewDark, settingsManager)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        swatches.forEachIndexed { index, swatch ->
            val priority = index + 1
            val isPriorityColor = priority <= 3
            val isSunsetMistGradient = themeKey == "sunset_mist" && priority == 3
            Box(
                modifier = Modifier
                    .size(if (isPriorityColor) 24.dp else 18.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSunsetMistGradient) {
                            Brush.linearGradient(listOf(swatches[0], swatches[1]))
                        } else {
                            Brush.linearGradient(listOf(swatch, swatch))
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isPriorityColor) {
                    Text(
                        text = priority.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSunsetMistGradient) {
                            paletteNumberColorForBackground(blendForContrast(swatches[0], swatches[1]))
                        } else {
                            paletteNumberColorForBackground(swatch)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private data class ThemePreviewChromeColors(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val accent: Color,
    val accentSecondary: Color,
)

private fun resolveThemeColorScheme(
    themeKey: String,
    previewDark: Boolean,
    settingsManager: SettingsManager?,
): com.eventmanager.app.ui.theme.ColorScheme {
    val theme = ColorThemes.getThemeByName(if (themeKey == "custom") "custom" else themeKey)
    return if (previewDark) theme.darkColors else theme.lightColors
}

private fun themePreviewChromeColors(
    themeKey: String,
    previewDark: Boolean,
    settingsManager: SettingsManager?,
): ThemePreviewChromeColors {
    if (themeKey == "custom" && settingsManager != null) {
        val base = if (previewDark) ColorThemes.SUNSET_MIST.darkColors else ColorThemes.SUNSET_MIST.lightColors
        fun c(role: String, fallback: Color) = Color(
            settingsManager.getCustomThemeColor(previewDark, role, fallback.toArgb()),
        )
        val primary = c("primary", base.primary)
        val primaryContainer = c("primaryContainer", base.primaryContainer)
        val backgroundRaw = c("background", base.background)
        return ThemePreviewChromeColors(
            background = previewBackgroundFor(backgroundRaw, primaryContainer),
            surface = c("surfaceContainer", base.surfaceContainer),
            onSurface = c("onSurface", base.onSurface),
            accent = primary,
            accentSecondary = c("tertiary", base.tertiary),
        )
    }
    val scheme = resolveThemeColorScheme(themeKey, previewDark, settingsManager)
    return ThemePreviewChromeColors(
        background = previewBackgroundFor(scheme),
        surface = scheme.surfaceContainer,
        onSurface = scheme.onSurface,
        accent = scheme.primary,
        accentSecondary = scheme.tertiary,
    )
}

/** Tinted canvas so each palette reads at a glance (brown stays brown, blue reads blue, etc.). */
private fun previewBackgroundFor(background: Color, primaryContainer: Color): Color {
    if (isDistinctiveThemeCanvas(background)) return background
    return blendColors(background, primaryContainer, 0.58f)
}

private fun previewBackgroundFor(scheme: com.eventmanager.app.ui.theme.ColorScheme): Color =
    previewBackgroundFor(scheme.background, scheme.primaryContainer)

private fun isDistinctiveThemeCanvas(color: Color): Boolean {
    val spread = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
    return spread > 0.035f
}

private fun blendColors(base: Color, tint: Color, tintWeight: Float): Color {
    val weight = tintWeight.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1f - weight) + tint.red * weight,
        green = base.green * (1f - weight) + tint.green * weight,
        blue = base.blue * (1f - weight) + tint.blue * weight,
        alpha = 1f,
    )
}

private fun resolveThemePaletteSwatches(
    themeKey: String,
    previewDark: Boolean,
    settingsManager: SettingsManager?,
): List<Color> {
    if (themeKey == "custom" && settingsManager != null) {
        val roles = listOf("primary", "secondary", "tertiary", "surfaceContainerHigh", "background")
        return roles.map { role ->
            Color(
                settingsManager.getCustomThemeColor(
                    previewDark,
                    role,
                    customThemeFallbackArgb(previewDark, role),
                ),
            )
        }
    }
    val source = resolveThemeColorScheme(themeKey, previewDark, settingsManager)
    return listOf(
        source.primary,
        source.secondary,
        source.tertiary,
        source.surfaceContainerHigh,
        source.background,
    )
}

private fun customThemeFallbackArgb(isDark: Boolean, role: String): Int {
    val scheme = if (isDark) ColorThemes.SUNSET_MIST.darkColors else ColorThemes.SUNSET_MIST.lightColors
    val color = when (role) {
        "primary" -> scheme.primary
        "secondary" -> scheme.secondary
        "tertiary" -> scheme.tertiary
        "surfaceContainerHigh" -> scheme.surfaceContainerHigh
        "background" -> scheme.background
        else -> scheme.primary
    }
    return color.toArgb()
}

private fun Color.toArgb(): Int {
    val a = (alpha * 255.999f).toInt()
    val r = (red * 255.999f).toInt()
    val g = (green * 255.999f).toInt()
    val b = (blue * 255.999f).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun paletteNumberColorForBackground(background: Color): Color {
    return if (background.luminance() < 0.45f) Color.White else Color(0xFF111111)
}

private fun blendForContrast(primary: Color, secondary: Color): Color {
    return Color(
        red = (primary.red + secondary.red) / 2f,
        green = (primary.green + secondary.green) / 2f,
        blue = (primary.blue + secondary.blue) / 2f,
        alpha = 1f,
    )
}

@Composable
fun DesktopAdminNavLayoutPicker(
    selectedLayout: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        "bottom" to (Icons.Default.ViewAgenda to Res.string.desktop_admin_nav_layout_bottom),
        "left" to (Icons.Default.ViewSidebar to Res.string.desktop_admin_nav_layout_left),
        "right" to (Icons.Default.ViewColumn to Res.string.desktop_admin_nav_layout_right),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.desktop_admin_nav_layout_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { (layout, iconLabel) ->
                val (icon, labelRes) = iconLabel
                val selected = selectedLayout.equals(layout, ignoreCase = true)
                val label = stringResource(labelRes)
                AppearanceOptionCard(
                    selected = selected,
                    onClick = { onSelect(layout) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = label },
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        AdminNavLayoutPreviewMockup(
                            layout = layout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminNavLayoutPreviewMockup(layout: String, modifier: Modifier = Modifier) {
    val frame = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val nav = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    val navItem = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
    val content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .background(frame)
            .padding(6.dp),
    ) {
        when (layout.lowercase()) {
            "bottom" -> {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .fillMaxWidth(if (it == 1) 0.7f else 0.9f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(content),
                            )
                        }
                    }
                    NavBarPreview(
                        horizontal = true,
                        navColor = nav,
                        itemColor = navItem,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            "left" -> {
                Row(Modifier.fillMaxSize()) {
                    NavBarPreview(
                        horizontal = false,
                        navColor = nav,
                        itemColor = navItem,
                        modifier = Modifier
                            .width(22.dp)
                            .fillMaxHeight(),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .fillMaxWidth(if (it == 1) 0.65f else 0.85f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(content),
                            )
                        }
                    }
                }
            }
            else -> {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .fillMaxWidth(if (it == 1) 0.65f else 0.85f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(content),
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    NavBarPreview(
                        horizontal = false,
                        navColor = nav,
                        itemColor = navItem,
                        modifier = Modifier
                            .width(22.dp)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarPreview(
    horizontal: Boolean,
    navColor: Color,
    itemColor: Color,
    modifier: Modifier = Modifier,
) {
    if (horizontal) {
        Row(
            modifier = modifier
                .height(16.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(navColor)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) {
                Box(
                    Modifier
                        .size(if (it == 1) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(itemColor.copy(alpha = if (it == 1) 1f else 0.65f)),
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(5.dp))
                .background(navColor)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(4) {
                Box(
                    Modifier
                        .size(if (it == 1) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(itemColor.copy(alpha = if (it == 1) 1f else 0.65f)),
                )
            }
        }
    }
}

@Composable
fun ScrollBehaviorPicker(
    scrollBehavior: String,
    onScrollBehaviorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stickyEnabled by remember(scrollBehavior) {
        mutableStateOf(scrollBehavior == SettingsManager.STICKY_FILTERS)
    }
    val isHeaderPinned = scrollBehavior == SettingsManager.HEADER_PINNED
    val isFullScroll = scrollBehavior == SettingsManager.FULL_SCROLL || scrollBehavior == SettingsManager.STICKY_FILTERS

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = stringResource(Res.string.scroll_behavior_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScrollBehaviorOptionCard(
                selected = isHeaderPinned,
                title = stringResource(Res.string.scroll_behavior_list_only_title),
                preview = { ScrollBehaviorPreviewMockup(fixedHeader = true, stickyFilters = false) },
                onClick = { onScrollBehaviorChange(SettingsManager.HEADER_PINNED) },
                modifier = Modifier.weight(1f),
            )
            ScrollBehaviorOptionCard(
                selected = isFullScroll,
                title = stringResource(Res.string.scroll_behavior_full_page_title),
                preview = {
                    ScrollBehaviorPreviewMockup(
                        fixedHeader = false,
                        stickyFilters = scrollBehavior == SettingsManager.STICKY_FILTERS,
                    )
                },
                onClick = { onScrollBehaviorChange(SettingsManager.FULL_SCROLL) },
                modifier = Modifier.weight(1f),
            )
        }

        if (isFullScroll) {
            Spacer(Modifier.height(10.dp))
            val stickyLabel = stringResource(Res.string.scroll_behavior_sticky_filters_option)
            AppearanceOptionCard(
                selected = stickyEnabled,
                onClick = {
                    stickyEnabled = !stickyEnabled
                    onScrollBehaviorChange(
                        if (stickyEnabled) SettingsManager.STICKY_FILTERS else SettingsManager.FULL_SCROLL,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = stickyLabel },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ScrollBehaviorPreviewMockup(fixedHeader = false, stickyFilters = stickyEnabled)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pin,
                            contentDescription = null,
                            tint = if (stickyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Switch(
                            checked = stickyEnabled,
                            onCheckedChange = { checked ->
                                stickyEnabled = checked
                                onScrollBehaviorChange(
                                    if (checked) SettingsManager.STICKY_FILTERS else SettingsManager.FULL_SCROLL,
                                )
                            },
                            modifier = Modifier.scale(0.85f),
                        )
                    }
                }
                Text(
                    text = stickyLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (stickyEnabled) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (stickyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ScrollBehaviorOptionCard(
    selected: Boolean,
    title: String,
    preview: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppearanceOptionCard(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = title },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                preview()
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ScrollBehaviorPreviewMockup(
    fixedHeader: Boolean,
    stickyFilters: Boolean,
    modifier: Modifier = Modifier,
) {
    val header = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val filters = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
    val row = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(6.dp),
    ) {
        if (fixedHeader) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(header),
                )
                Row(
                    Modifier.padding(start = 2.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Pin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(filters),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.85f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(row),
                        )
                    }
                }
            }
        } else if (stickyFilters) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(header.copy(alpha = 0.25f)),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(filters),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    repeat(4) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (it == 0) 0.55f else 0.85f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(row),
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(header.copy(alpha = 0.35f)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(filters.copy(alpha = 0.35f)),
                    )
                    repeat(4) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (it == 2) 0.6f else 0.85f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(row),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}
