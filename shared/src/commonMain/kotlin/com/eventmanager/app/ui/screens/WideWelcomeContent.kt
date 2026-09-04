package com.eventmanager.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.admin_mode
import com.eventmanager.app.resources.nunito_black
import com.eventmanager.app.resources.nunito_bold
import com.eventmanager.app.resources.nunito_extrabold
import com.eventmanager.app.resources.nunito_light
import com.eventmanager.app.resources.nunito_light_italic
import com.eventmanager.app.resources.nunito_medium
import com.eventmanager.app.resources.nunito_regular
import com.eventmanager.app.resources.nunito_regular_italic
import com.eventmanager.app.resources.nunito_semibold
import com.eventmanager.app.resources.pos_welcome_button
import com.eventmanager.app.resources.ticket_check_mode
import com.eventmanager.app.resources.welcome_desktop_tagline
import com.eventmanager.app.resources.welcome_pos_hint
import com.eventmanager.app.resources.welcome_ticketing_hint
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource

@Composable
fun rememberWideWelcomeFontFamily(): FontFamily {
    val light = Font(Res.font.nunito_light, weight = FontWeight.Light)
    val regular = Font(Res.font.nunito_regular, weight = FontWeight.Normal)
    val medium = Font(Res.font.nunito_medium, weight = FontWeight.Medium)
    val semiBold = Font(Res.font.nunito_semibold, weight = FontWeight.SemiBold)
    val bold = Font(Res.font.nunito_bold, weight = FontWeight.Bold)
    val extraBold = Font(Res.font.nunito_extrabold, weight = FontWeight.ExtraBold)
    val black = Font(Res.font.nunito_black, weight = FontWeight.Black)
    val lightItalic = Font(Res.font.nunito_light_italic, weight = FontWeight.Light, style = FontStyle.Italic)
    val regularItalic = Font(Res.font.nunito_regular_italic, weight = FontWeight.Normal, style = FontStyle.Italic)
    val extraLight = Font(Res.font.nunito_light, weight = FontWeight.ExtraLight)
    val extraLightItalic = Font(Res.font.nunito_light_italic, weight = FontWeight.ExtraLight, style = FontStyle.Italic)
    return remember(
        light, regular, medium, semiBold, bold, extraBold, black,
        lightItalic, regularItalic, extraLight, extraLightItalic,
    ) {
        FontFamily(
            light, regular, medium, semiBold, bold, extraBold, black,
            lightItalic, regularItalic, extraLight, extraLightItalic,
        )
    }
}

/**
 * Dual-card welcome chooser used on desktop and large tablets.
 * Callers own backgrounds / org switcher / platform-specific sync banners.
 */
@Composable
fun WideWelcomeContent(
    appName: String,
    onAdminSelected: () -> Unit,
    onTicketCheckSelected: () -> Unit,
    onPosSelected: () -> Unit,
    modifier: Modifier = Modifier,
    hoverEnabled: Boolean = false,
    syncSlot: @Composable () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkWelcomeUi = colorScheme.surface.luminance() < 0.5f
    val welcomeTaglineColor = if (isDarkWelcomeUi) {
        colorScheme.onSurface.copy(alpha = 0.92f)
    } else {
        colorScheme.primary.copy(alpha = 0.78f)
    }
    val welcomeAdminContentColor = if (isDarkWelcomeUi) {
        colorScheme.onSurface.copy(alpha = 0.9f)
    } else {
        colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    val welcomeFont = rememberWideWelcomeFontFamily()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WideWelcomeBrand(appName = appName, fontFamily = welcomeFont)

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(Res.string.welcome_desktop_tagline),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = welcomeFont,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.8.sp,
            ),
            color = welcomeTaglineColor,
            textAlign = TextAlign.Center,
        )

        syncSlot()

        Spacer(Modifier.height(36.dp))

        Row(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WideWelcomeModeCard(
                title = stringResource(Res.string.ticket_check_mode),
                subtitle = stringResource(Res.string.welcome_ticketing_hint),
                icon = Icons.Default.ConfirmationNumber,
                emphasized = true,
                fontFamily = welcomeFont,
                onClick = onTicketCheckSelected,
                hoverEnabled = hoverEnabled,
                modifier = Modifier.weight(1f),
            )
            WideWelcomeModeCard(
                title = stringResource(Res.string.pos_welcome_button),
                subtitle = stringResource(Res.string.welcome_pos_hint),
                icon = Icons.Default.PointOfSale,
                emphasized = false,
                fontFamily = welcomeFont,
                onClick = onPosSelected,
                hoverEnabled = hoverEnabled,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(28.dp))

        TextButton(
            onClick = onAdminSelected,
            colors = ButtonDefaults.textButtonColors(
                contentColor = welcomeAdminContentColor,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.admin_mode),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = welcomeFont,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                ),
            )
        }
    }
}

@Composable
fun WideWelcomeBrand(
    appName: String,
    fontFamily: FontFamily,
) {
    val colorScheme = MaterialTheme.colorScheme
    val splitAt = appName.indexOf("List").takeIf { it > 0 } ?: appName.length
    val lead = appName.substring(0, splitAt)
    val trail = appName.substring(splitAt)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Normal,
                        color = colorScheme.onSurface,
                        letterSpacing = 0.8.sp,
                    ),
                ) {
                    append(lead)
                }
                if (trail.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Light,
                            fontStyle = FontStyle.Italic,
                            color = colorScheme.primary,
                            letterSpacing = 2.4.sp,
                        ),
                    ) {
                        append(trail)
                    }
                }
            },
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = fontFamily,
                fontSize = 58.sp,
                lineHeight = 62.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colorScheme.primary.copy(alpha = 0.55f)),
        )
    }
}

@Composable
fun WideWelcomeModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    emphasized: Boolean,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hoverEnabled: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isHoveredRaw by interactionSource.collectIsHoveredAsState()
    val isHovered = hoverEnabled && isHoveredRaw
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.975f
            isHovered -> 1.015f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 160),
        label = "welcomeModeCardScale",
    )
    val shape = RoundedCornerShape(24.dp)

    val containerColor = when {
        emphasized && isHovered -> colorScheme.primary
        emphasized -> colorScheme.primaryContainer
        isHovered -> colorScheme.surfaceContainerHigh
        else -> colorScheme.surface
    }
    val contentColor = when {
        emphasized && isHovered -> colorScheme.onPrimary
        emphasized -> colorScheme.onPrimaryContainer
        else -> colorScheme.onSurface
    }
    val mutedColor = when {
        emphasized && isHovered -> colorScheme.onPrimary.copy(alpha = 0.82f)
        emphasized -> colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
        else -> colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
    }
    val iconWellColor = when {
        emphasized && isHovered -> colorScheme.onPrimary.copy(alpha = 0.16f)
        emphasized -> colorScheme.primary.copy(alpha = 0.16f)
        isHovered -> colorScheme.primary.copy(alpha = 0.14f)
        else -> colorScheme.primary.copy(alpha = 0.10f)
    }
    val borderColor = when {
        emphasized && isHovered -> colorScheme.onPrimary.copy(alpha = 0.18f)
        emphasized -> colorScheme.primary.copy(alpha = 0.28f)
        isHovered -> colorScheme.primary.copy(alpha = 0.35f)
        else -> colorScheme.outline.copy(alpha = 0.28f)
    }

    Box(
        modifier = modifier
            .height(196.dp)
            .graphicsLayer {
                if (scale != 1f) {
                    scaleX = scale
                    scaleY = scale
                }
            }
            .then(
                if (isHovered) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = colorScheme.primary.copy(alpha = 0.12f),
                        spotColor = colorScheme.primary.copy(alpha = 0.18f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .then(if (hoverEnabled) Modifier.hoverable(interactionSource) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconWellColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (emphasized && isHovered) contentColor else colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp,
                    ),
                    color = contentColor,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.15.sp,
                        lineHeight = 21.sp,
                    ),
                    color = mutedColor,
                )
            }
        }
    }
}
