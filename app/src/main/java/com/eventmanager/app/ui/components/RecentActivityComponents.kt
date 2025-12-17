package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.eventmanager.app.data.models.CounterData
import com.eventmanager.app.data.repository.EventManagerRepository

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

            // Activity items
            activities.forEachIndexed { index, activity ->
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
 * Displays formatted time ago
 */
@Composable
fun TimeDisplay(
    timestamp: Long,
    isPhone: Boolean = true,
    modifier: Modifier = Modifier
) {
    val timeAgo = formatTimeAgo(timestamp)
    
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
 */
fun formatTimeAgo(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
    return formatter.format(date)
}

/**
 * Safe vibration helper that handles permission gracefully
 */
private fun safeVibrate(vibrator: Vibrator?, duration: Long) {
    try {
        vibrator?.vibrate(duration)
    } catch (e: SecurityException) {
        // Vibrate permission not granted, silently ignore
    } catch (e: Exception) {
        // Any other vibration error, silently ignore
    }
}

/**
 * People Counter component with improved design and safe vibration
 * - Modern card design aligned with app's design language
 * - Smooth animations and visual feedback
 * - Safe vibration handling with permission checks
 * - Long-press support for bulk operations (+10/-10)
 * - Persistent storage with last modified timestamp
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeopleCounter(
    isPhone: Boolean = true,
    modifier: Modifier = Modifier,
    repository: com.eventmanager.app.data.repository.EventManagerRepository? = null
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator }
    
    // Load counter from database if repository is available
    val counterFlow = remember(repository) {
        repository?.getCounter() ?: flowOf(null)
    }
    val counterData by counterFlow.collectAsState(null)
    
    var peopleCount by remember { mutableStateOf(0) }
    var lastModified by remember { mutableStateOf(0L) }
    var lastAction by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    // Update local state when counterData changes
    LaunchedEffect(counterData) {
        counterData?.let {
            peopleCount = it.count
            lastModified = it.lastModified
        }
    }
    
    // Animation for counter scale
    val scale by animateFloatAsState(
        targetValue = if (lastAction == "increment" || lastAction == "decrement") 1.05f else 1f,
        label = "counterScale"
    )
    
    // Animation state for buttons
    var minusPressed by remember { mutableStateOf(false) }
    var plusPressed by remember { mutableStateOf(false) }
    var resetPressed by remember { mutableStateOf(false) }
    
    val minusScale by animateFloatAsState(
        targetValue = if (minusPressed) 0.95f else 1f,
        label = "minusScale"
    )
    val plusScale by animateFloatAsState(
        targetValue = if (plusPressed) 0.95f else 1f,
        label = "plusScale"
    )
    val resetScale by animateFloatAsState(
        targetValue = if (resetPressed) 0.95f else 1f,
        label = "resetScale"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 20.dp else 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 20.dp else 24.dp)
        ) {
            // Title Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isPhone) 16.dp else 20.dp)
            ) {
                // Icon badge
                Box(
                    modifier = Modifier
                        .size(if (isPhone) 44.dp else 52.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(if (isPhone) 12.dp else 14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(if (isPhone) 26.dp else 30.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    text = context.getString(R.string.people_counter_title),
                    style = if (isPhone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Counter Display Section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isPhone) 12.dp else 16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(if (isPhone) 16.dp else 20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isPhone) 24.dp else 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = peopleCount.toString(),
                        style = if (isPhone) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.scale(scale)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
            
            // Last Modified Display
            if (lastModified > 0) {
                Text(
                    text = "Last modified: ${formatCounterTime(lastModified)}",
                    style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isPhone) 8.dp else 12.dp),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 20.dp))
            
            // Control Buttons Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus Button with long-press support for -10
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isPhone) 56.dp else 64.dp)
                        .combinedClickable(
                            onClick = {
                                if (peopleCount > 0) {
                                    peopleCount--
                                    lastAction = "decrement"
                                    lastModified = System.currentTimeMillis()
                                    safeVibrate(vibrator, 5)
                                    repository?.let {
                                        coroutineScope.launch {
                                            it.updateCounter(peopleCount)
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                if (peopleCount >= 10) {
                                    peopleCount -= 10
                                    lastAction = "decrement"
                                    lastModified = System.currentTimeMillis()
                                    safeVibrate(vibrator, 8)
                                    repository?.let {
                                        coroutineScope.launch {
                                            it.updateCounter(peopleCount)
                                        }
                                    }
                                }
                            }
                        )
                        .scale(minusScale),
                    shape = RoundedCornerShape(if (isPhone) 14.dp else 16.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = null,
                            modifier = Modifier.size(if (isPhone) 24.dp else 28.dp),
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
                
                // Plus Button with long-press support for +10
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isPhone) 56.dp else 64.dp)
                        .combinedClickable(
                            onClick = {
                                peopleCount++
                                lastAction = "increment"
                                lastModified = System.currentTimeMillis()
                                safeVibrate(vibrator, 5)
                                repository?.let {
                                    coroutineScope.launch {
                                        it.updateCounter(peopleCount)
                                    }
                                }
                            },
                            onLongClick = {
                                peopleCount += 10
                                lastAction = "increment"
                                lastModified = System.currentTimeMillis()
                                safeVibrate(vibrator, 8)
                                repository?.let {
                                    coroutineScope.launch {
                                        it.updateCounter(peopleCount)
                                    }
                                }
                            }
                        )
                        .scale(plusScale),
                    shape = RoundedCornerShape(if (isPhone) 14.dp else 16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(if (isPhone) 24.dp else 28.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 12.dp else 16.dp))
            
            // Reset Button - Long press only
            var isResetting by remember { mutableStateOf(false) }
            val longPressDuration = 600L // 600ms long-press duration
            
            val animatedResetProgress by animateFloatAsState(
                targetValue = if (isResetting) 1f else 0f,
                animationSpec = tween(durationMillis = longPressDuration.toInt(), easing = LinearEasing),
                label = "resetProgress"
            )
            
            // Auto-trigger action when animation completes
            LaunchedEffect(animatedResetProgress) {
                if (animatedResetProgress >= 0.99f && isResetting) {
                    // Progress animation completed, trigger the action
                    peopleCount = 0
                    lastAction = "reset"
                    lastModified = System.currentTimeMillis()
                    safeVibrate(vibrator, 10)
                    isResetting = false
                    repository?.let {
                        coroutineScope.launch {
                            it.resetCounter()
                        }
                    }
                }
            }
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isPhone) 48.dp else 56.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isResetting = true
                                tryAwaitRelease()
                                isResetting = false
                            }
                        )
                    }
                    .scale(resetScale),
                shape = RoundedCornerShape(if (isPhone) 12.dp else 14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    // Progress fill background - fills entire button
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedResetProgress)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                    
                    // Button content on top
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = context.getString(R.string.people_counter_reset),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Format counter timestamp to readable string
 */
fun formatCounterTime(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val now = java.util.Date()
    val diffMs = now.time - date.time
    
    return when {
        diffMs < 1000 -> "just now"
        diffMs < 60000 -> "${diffMs / 1000}s ago"
        diffMs < 3600000 -> "${diffMs / 60000}m ago"
        diffMs < 86400000 -> "${diffMs / 3600000}h ago"
        else -> java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(date)
    }
}
