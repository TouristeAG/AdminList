package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmanager.app.ui.utils.isTablet

@Composable
fun StatCardV2(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emoji: String? = null,
    modifier: Modifier = Modifier,
    isPhone: Boolean = !isTablet(),
    compact: Boolean = false,
    onTripleTap: (() -> Unit)? = null
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    LaunchedEffect(tapCount, lastTapTime) {
        if (tapCount > 0 && tapCount < 3) {
            kotlinx.coroutines.delay(500)
            if (System.currentTimeMillis() - lastTapTime >= 500) tapCount = 0
        }
    }

    Card(
        modifier = modifier
            .height(
                when {
                    compact && isPhone -> 100.dp
                    compact -> 124.dp
                    isPhone -> 140.dp
                    else -> 160.dp
                }
            )
            .then(
                if (!isPhone) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp)
                    )
                } else Modifier
            )
            .then(
                if (onTripleTap != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 500) {
                                tapCount++
                                if (tapCount >= 3) { onTripleTap(); tapCount = 0 }
                            } else tapCount = 1
                            lastTapTime = now
                        }
                    }
                } else Modifier
            ),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPhone) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPhone) 2.dp else 6.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(
                when {
                    compact && isPhone -> 10.dp
                    compact -> 12.dp
                    isPhone -> 14.dp
                    else -> 16.dp
                }
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(
                    when {
                        compact && isPhone -> 28.dp
                        compact -> 34.dp
                        isPhone -> 36.dp
                        else -> 44.dp
                    }
                )
                    .background(
                        if (isPhone) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    emoji != null -> Text(
                        emoji,
                        fontSize = when {
                            compact && isPhone -> 14.sp
                            compact -> 18.sp
                            isPhone -> 18.sp
                            else -> 22.sp
                        }
                    )
                    icon != null -> Icon(
                        icon,
                        null,
                        Modifier.size(
                            when {
                                compact && isPhone -> 14.dp
                                compact -> 18.dp
                                isPhone -> 18.dp
                                else -> 22.dp
                            }
                        ),
                        tint = if (isPhone) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }
            Spacer(Modifier.height(
                when {
                    compact && isPhone -> 4.dp
                    compact -> 6.dp
                    isPhone -> 8.dp
                    else -> 10.dp
                }
            ))
            Text(
                value,
                style = when {
                    compact && isPhone -> MaterialTheme.typography.titleLarge
                    compact -> MaterialTheme.typography.headlineSmall
                    isPhone -> MaterialTheme.typography.headlineSmall
                    else -> MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(
                when {
                    compact -> 2.dp
                    isPhone -> 4.dp
                    else -> 6.dp
                }
            ))
            Text(
                title,
                style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = if (isPhone) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
