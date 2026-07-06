package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.combinedClickable
import android.os.Vibrator
import androidx.compose.foundation.indication
import androidx.compose.material3.Icon
import com.eventmanager.app.R
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.eventmanager.app.data.sync.formatRelativeSinceSync
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel

/**
 * Data class for a recent activity item
 */
data class RecentActivityItem(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val timestamp: Long,
    val badge: String,
    val badgeColor: androidx.compose.ui.graphics.Color,
    val badgeIcon: ImageVector? = null
)

/**
 * Displays a section with multiple recent activity items in a modern card layout
 */
@Composable
fun RecentActivitySection(
    title: String,
    icon: ImageVector,
    activities: List<RecentActivityItem>,
    isPhone: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (activities.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 16.dp else 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isPhone) 12.dp else 16.dp)
            ) {
                // Icon with background
                Box(
                    modifier = Modifier
                        .size(if (isPhone) 36.dp else 40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(if (isPhone) 10.dp else 12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.width(if (isPhone) 12.dp else 16.dp))
                
                Text(
                    text = title,
                    style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Activity items - optimized to avoid unnecessary recompositions
            // Use key() to help Compose identify items efficiently
            activities.forEachIndexed { index, activity ->
                key(activity.id) {
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                .padding(vertical = if (isPhone) 8.dp else 12.dp)
                        )
                    }
                    
                    RecentActivityItemCard(
                        activity = activity,
                        isPhone = isPhone
                    )
                }
            }
        }
    }
}

/**
 * Individual activity item card
 */
@Composable
fun RecentActivityItemCard(
    activity: RecentActivityItem,
    isPhone: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (isPhone) 6.dp else 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Name and subtitle
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = if (isPhone) 8.dp else 12.dp)
        ) {
            Text(
                text = activity.name,
                style = if (isPhone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!activity.subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activity.subtitle,
                    style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right side: Badge and time
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = if (isPhone) 8.dp else 12.dp)
        ) {
            // Badge
            BadgeChip(
                text = activity.badge,
                color = activity.badgeColor,
                icon = activity.badgeIcon,
                isPhone = isPhone
            )

            // Time
            TimeDisplay(
                timestamp = activity.timestamp,
                isPhone = isPhone
            )
        }
    }
}

/**
 * Styled badge chip
 */
@Composable
fun BadgeChip(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    icon: ImageVector? = null,
    isPhone: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(if (isPhone) 8.dp else 10.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            color.copy(alpha = 0.3f)
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = if (isPhone) 8.dp else 10.dp,
                vertical = if (isPhone) 4.dp else 6.dp
            )
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 14.dp else 16.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            Text(
                text = text,
                style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

/**
 * Thread-safe cached SimpleDateFormat instance for date formatting
 * SimpleDateFormat is thread-safe for reading (format operations)
 */
private val dateFormatterCache = ThreadLocal.withInitial {
    SimpleDateFormat("MMM d", Locale.getDefault())
}

/**
 * Displays formatted time ago
 */
@Composable
fun TimeDisplay(
    timestamp: Long,
    isPhone: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Use remember to cache the formatted string and avoid recomputation on every recomposition
    val timeAgo = remember(timestamp) {
        formatTimeAgo(timestamp)
    }
    
    Text(
        text = timeAgo,
        style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        fontWeight = FontWeight.Medium
    )
}

/**
 * Formats timestamp to human readable "time ago" format
 * Optimized to reuse cached SimpleDateFormat instance
 */
fun formatTimeAgo(timestamp: Long): String {
    val formatter = dateFormatterCache.get()
    return formatter.format(Date(timestamp))
}
