package com.eventmanager.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.animated_background_description
import com.eventmanager.app.resources.animated_background_title
import com.eventmanager.app.resources.billeterie_background_animation_description
import com.eventmanager.app.resources.billeterie_background_animation_title
import com.eventmanager.app.resources.pos_background_animation_description
import com.eventmanager.app.resources.pos_background_animation_title
import com.eventmanager.app.resources.background_animation_arches
import com.eventmanager.app.resources.background_animation_none
import com.eventmanager.app.resources.background_animation_opacity_apply
import com.eventmanager.app.resources.background_animation_opacity_description
import com.eventmanager.app.resources.background_animation_opacity_title
import com.eventmanager.app.resources.background_animation_topographic
import com.eventmanager.app.ui.platform.AppAppearanceState
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

private data class BackgroundAnimationOption(
    val key: String,
    val labelRes: org.jetbrains.compose.resources.StringResource,
    val icon: ImageVector,
)

private val backgroundAnimationOptions = listOf(
    BackgroundAnimationOption(
        key = BackgroundAnimationStyle.NONE,
        labelRes = Res.string.background_animation_none,
        icon = Icons.Default.Block,
    ),
    BackgroundAnimationOption(
        key = BackgroundAnimationStyle.ARCHES,
        labelRes = Res.string.background_animation_arches,
        icon = Icons.Default.AllInclusive,
    ),
    BackgroundAnimationOption(
        key = BackgroundAnimationStyle.TOPOGRAPHIC,
        labelRes = Res.string.background_animation_topographic,
        icon = Icons.Default.Landscape,
    ),
)

@Composable
fun BackgroundAnimationSettingsSection(
    settingsManager: SettingsManager,
    isDesktop: Boolean,
    modifier: Modifier = Modifier,
    target: BackgroundAnimationSettingsTarget = BackgroundAnimationSettingsTarget.Admin,
    showSectionHeader: Boolean = true,
) {
    val readStyle = {
        when (target) {
            BackgroundAnimationSettingsTarget.Admin -> settingsManager.getBackgroundAnimationStyle()
            BackgroundAnimationSettingsTarget.Billeterie -> settingsManager.getBilleterieBackgroundAnimationStyle()
            BackgroundAnimationSettingsTarget.Pos -> settingsManager.getPosBackgroundAnimationStyle()
        }
    }
    val writeStyle = { style: String ->
        when (target) {
            BackgroundAnimationSettingsTarget.Admin -> settingsManager.setBackgroundAnimationStyle(style)
            BackgroundAnimationSettingsTarget.Billeterie -> settingsManager.setBilleterieBackgroundAnimationStyle(style)
            BackgroundAnimationSettingsTarget.Pos -> settingsManager.setPosBackgroundAnimationStyle(style)
        }
    }
    val readOpacity = {
        when (target) {
            BackgroundAnimationSettingsTarget.Admin -> settingsManager.getBackgroundAnimationOpacity()
            BackgroundAnimationSettingsTarget.Billeterie -> settingsManager.getBilleterieBackgroundAnimationOpacity()
            BackgroundAnimationSettingsTarget.Pos -> settingsManager.getPosBackgroundAnimationOpacity()
        }
    }
    val writeOpacity = { opacity: Float ->
        when (target) {
            BackgroundAnimationSettingsTarget.Admin -> settingsManager.setBackgroundAnimationOpacity(opacity)
            BackgroundAnimationSettingsTarget.Billeterie -> settingsManager.setBilleterieBackgroundAnimationOpacity(opacity)
            BackgroundAnimationSettingsTarget.Pos -> settingsManager.setPosBackgroundAnimationOpacity(opacity)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showSectionHeader) {
            Text(
                text = stringResource(
                    when (target) {
                        BackgroundAnimationSettingsTarget.Billeterie -> Res.string.billeterie_background_animation_title
                        BackgroundAnimationSettingsTarget.Pos -> Res.string.pos_background_animation_title
                        BackgroundAnimationSettingsTarget.Admin -> Res.string.animated_background_title
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    when (target) {
                        BackgroundAnimationSettingsTarget.Billeterie -> Res.string.billeterie_background_animation_description
                        BackgroundAnimationSettingsTarget.Pos -> Res.string.pos_background_animation_description
                        BackgroundAnimationSettingsTarget.Admin -> Res.string.animated_background_description
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }

        var selectedStyle by remember(target) { mutableStateOf(readStyle()) }
        var appliedOpacity by remember(target) { mutableFloatStateOf(readOpacity()) }
        var pendingOpacity by remember(target) { mutableFloatStateOf(readOpacity()) }

        BackgroundAnimationStylePicker(
            selectedStyle = selectedStyle,
            settingsManager = settingsManager,
            isDesktop = isDesktop,
            onSelect = { style ->
                selectedStyle = style
                writeStyle(style)
                if (BackgroundAnimationStyle.isEnabled(style)) {
                    val defaultOpacity = BackgroundAnimationStyle.defaultOpacity(style)
                    appliedOpacity = defaultOpacity
                    pendingOpacity = defaultOpacity
                    writeOpacity(defaultOpacity)
                }
                AppAppearanceState.refreshNonce++
            },
        )

        Spacer(Modifier.height(20.dp))

        val animationEnabled = BackgroundAnimationStyle.isEnabled(selectedStyle)
        val hasUnsavedOpacity = animationEnabled && abs(pendingOpacity - appliedOpacity) > 0.001f

        Text(
            text = stringResource(Res.string.background_animation_opacity_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(Res.string.background_animation_opacity_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Opacity,
                contentDescription = null,
                tint = if (animationEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(20.dp),
            )
            Slider(
                value = pendingOpacity,
                onValueChange = { pendingOpacity = it },
                enabled = animationEnabled,
                valueRange = 0.05f..1f,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(pendingOpacity * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (animationEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.width(44.dp),
            )
        }

        if (hasUnsavedOpacity) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    appliedOpacity = pendingOpacity
                    writeOpacity(pendingOpacity)
                    AppAppearanceState.refreshNonce++
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.background_animation_opacity_apply))
            }
        }
    }
}

@Composable
fun BackgroundAnimationStylePicker(
    selectedStyle: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    settingsManager: SettingsManager? = null,
    isDesktop: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        backgroundAnimationOptions.forEach { option ->
            val selected = selectedStyle == option.key
            BackgroundAnimationStyleCard(
                option = option,
                selected = selected,
                settingsManager = settingsManager,
                isDesktop = isDesktop,
                onClick = { onSelect(option.key) },
            )
        }
    }
}

@Composable
private fun BackgroundAnimationStyleCard(
    option: BackgroundAnimationOption,
    selected: Boolean,
    settingsManager: SettingsManager?,
    isDesktop: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val label = stringResource(option.labelRes)
    val previewShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 12.dp, bottomEnd = 12.dp)

    Card(
        modifier = Modifier
            .width(212.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.75f),
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 2.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(previewShape)
                    .background(colorScheme.background),
            ) {
                BackgroundAnimationPreview(
                    style = option.key,
                    settingsManager = settingsManager,
                    isDesktop = isDesktop,
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surface.copy(alpha = 0.92f))
                        .border(1.dp, colorScheme.outline.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) colorScheme.primary else colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun BackgroundAnimationPreview(
    style: String,
    settingsManager: SettingsManager?,
    isDesktop: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        when (BackgroundAnimationStyle.fromStored(style)) {
            BackgroundAnimationStyle.NONE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                )
            }
            else -> {
                if (settingsManager != null) {
                    AppBackgroundAnimation(
                        style = style,
                        opacity = BackgroundAnimationStyle.defaultOpacity(style),
                        settingsManager = settingsManager,
                        isDesktop = isDesktop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
