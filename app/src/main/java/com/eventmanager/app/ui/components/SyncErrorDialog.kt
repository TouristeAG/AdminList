package com.eventmanager.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmanager.app.R
import kotlinx.coroutines.delay

/**
 * Dialog state for tracking sync retry flow
 */
private enum class SyncDialogState {
    ERROR,      // Showing error message
    RETRYING,   // Retry in progress
    SUCCESS     // Sync succeeded
}

/**
 * Animated checkmark component - lightweight, similar to Google Pay validation
 * Uses simple Canvas drawing with path animation for performance
 */
@Composable
private fun AnimatedCheckmark(
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary
) {
    // Animation progress from 0 to 1
    val animationProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            // Animate the checkmark drawing
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing
                )
            )
        } else {
            // No animation - show immediately
            animationProgress.snapTo(1f)
        }
    }
    
    // Scale animation for the circle background
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (animationsEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            snap()
        },
        label = "checkmark_scale"
    )
    
    Box(
        modifier = modifier
            .size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Circle background
        Box(
            modifier = Modifier
                .size((72 * scale).dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
        )
        
        // Checkmark drawn on Canvas
        Canvas(
            modifier = Modifier.size(48.dp)
        ) {
            val progress = animationProgress.value
            val strokeWidth = 4.dp.toPx()
            
            // Checkmark path: starts from left-center, goes down-center, then up-right
            // Point 1: Start (left side)
            val p1 = Offset(size.width * 0.2f, size.height * 0.5f)
            // Point 2: Bottom of checkmark
            val p2 = Offset(size.width * 0.4f, size.height * 0.7f)
            // Point 3: End (right-top)
            val p3 = Offset(size.width * 0.8f, size.height * 0.3f)
            
            // Calculate total length of the two line segments
            val segment1Length = kotlin.math.sqrt(
                (p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)
            )
            val segment2Length = kotlin.math.sqrt(
                (p3.x - p2.x) * (p3.x - p2.x) + (p3.y - p2.y) * (p3.y - p2.y)
            )
            val totalLength = segment1Length + segment2Length
            
            // Current drawing length based on progress
            val currentLength = totalLength * progress
            
            if (currentLength > 0) {
                if (currentLength <= segment1Length) {
                    // Drawing first segment
                    val ratio = currentLength / segment1Length
                    val endX = p1.x + (p2.x - p1.x) * ratio
                    val endY = p1.y + (p2.y - p1.y) * ratio
                    drawLine(
                        color = color,
                        start = p1,
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                } else {
                    // Draw complete first segment
                    drawLine(
                        color = color,
                        start = p1,
                        end = p2,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    
                    // Draw second segment partially
                    val remainingLength = currentLength - segment1Length
                    val ratio = remainingLength / segment2Length
                    val endX = p2.x + (p3.x - p2.x) * ratio
                    val endY = p2.y + (p3.y - p2.y) * ratio
                    drawLine(
                        color = color,
                        start = p2,
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Dialog to display sync errors with detailed information
 * Includes a "do not tell me again today" checkbox
 * Now handles retry flow with loading and success states
 */
@Composable
fun SyncErrorDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
    errorMessage: String,
    modifier: Modifier = Modifier,
    onDontTellTodayChanged: (Boolean) -> Unit = {},
    // New parameters for retry flow
    isSyncing: Boolean = false,
    animationsEnabled: Boolean = true,
    wasDeviceSleeping: Boolean = false
) {
    var dontTellToday by remember { mutableStateOf(false) }
    var dialogState by remember { mutableStateOf(SyncDialogState.ERROR) }
    var wasRetrying by remember { mutableStateOf(false) }
    
    // Track sync state changes to detect success after retry
    LaunchedEffect(isSyncing) {
        if (wasRetrying && !isSyncing && dialogState == SyncDialogState.RETRYING) {
            // Sync just finished after we triggered retry
            // Check if error is cleared (success) or still has error
            if (errorMessage.isEmpty()) {
                // Success! Show success state
                dialogState = SyncDialogState.SUCCESS
            } else {
                // Still error, go back to error state
                dialogState = SyncDialogState.ERROR
            }
        }
    }
    
    // Auto-dismiss after showing success animation
    LaunchedEffect(dialogState) {
        if (dialogState == SyncDialogState.SUCCESS) {
            // Wait for animation to complete, then auto-dismiss
            delay(1800L)
            onDismiss()
        }
        // Track retrying state
        if (dialogState == SyncDialogState.RETRYING) {
            wasRetrying = true
        }
    }
    
    // Reset state when dialog becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            dialogState = SyncDialogState.ERROR
            wasRetrying = false
            dontTellToday = false
        }
    }
    
    if (isVisible) {
        AlertDialog(
            onDismissRequest = {
                if (dialogState != SyncDialogState.RETRYING) {
                    if (dontTellToday) {
                        onDontTellTodayChanged(true)
                    }
                    onDismiss()
                }
            },
            title = {
                when (dialogState) {
                    SyncDialogState.ERROR -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                stringResource(R.string.sync_error_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    SyncDialogState.RETRYING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                stringResource(R.string.sync_retrying),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    SyncDialogState.SUCCESS -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.sync_success_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            text = {
                when (dialogState) {
                    SyncDialogState.ERROR -> {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Brief message
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.sync_error_occurred),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            
                            // Special message if device was sleeping
                            if (wasDeviceSleeping) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.sync_error_sleep_resume_detail),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            
                            // Error details
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.sync_error_details_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Text(
                                        text = errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 8
                                    )
                                }
                            }
                            
                            // Advice based on error type
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.sync_error_what_to_do),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    
                                    val adviceResId = getErrorAdviceResId(errorMessage)
                                    Text(
                                        text = stringResource(adviceResId),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            // Do not tell again checkbox
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = dontTellToday,
                                    onCheckedChange = { dontTellToday = it },
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    stringResource(R.string.sync_error_dont_tell_today),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    SyncDialogState.RETRYING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = stringResource(R.string.sync_retrying),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    SyncDialogState.SUCCESS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Animated checkmark
                            AnimatedCheckmark(
                                animationsEnabled = animationsEnabled,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                text = stringResource(R.string.sync_success_message),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when (dialogState) {
                    SyncDialogState.ERROR -> {
                        Button(
                            onClick = {
                                if (dontTellToday) {
                                    onDontTellTodayChanged(true)
                                }
                                dialogState = SyncDialogState.RETRYING
                                wasRetrying = false // Reset before starting
                                onRetry()
                                // Mark that we're now in retry mode after calling onRetry
                                wasRetrying = true
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sync_error_retry))
                        }
                    }
                    SyncDialogState.RETRYING -> {
                        // No confirm button while retrying
                    }
                    SyncDialogState.SUCCESS -> {
                        // No confirm button on success (auto-dismiss)
                    }
                }
            },
            dismissButton = {
                when (dialogState) {
                    SyncDialogState.ERROR -> {
                        TextButton(
                            onClick = {
                                if (dontTellToday) {
                                    onDontTellTodayChanged(true)
                                }
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(R.string.sync_error_dismiss))
                        }
                    }
                    SyncDialogState.RETRYING -> {
                        // No dismiss button while retrying
                    }
                    SyncDialogState.SUCCESS -> {
                        // No dismiss button on success (auto-dismiss)
                    }
                }
            },
            modifier = modifier
        )
    }
}

/**
 * Determine if an error should be shown to the user
 * Filters out non-critical errors like local validation issues
 */
fun shouldShowSyncError(errorMessage: String?): Boolean {
    if (errorMessage == null) return false
    
    // List of non-critical errors that should NOT show the popup
    val ignoredPatterns = listOf(
        "already exists",  // Local duplicate detection
        "duplicate",       // Local duplicate key violations
        "integrity constraint", // Database constraint violations
        "does not exist",   // Not found in local DB (not an API error)
    )
    
    return !ignoredPatterns.any { pattern ->
        errorMessage.contains(pattern, ignoreCase = true)
    }
}

/**
 * Get contextual advice resource ID based on the error message
 */
private fun getErrorAdviceResId(errorMessage: String): Int {
    return when {
        errorMessage.contains("429", ignoreCase = true) || 
        errorMessage.contains("rate limit", ignoreCase = true) ||
        errorMessage.contains("Rate limit", ignoreCase = true) -> {
            R.string.sync_advice_rate_limit
        }
        errorMessage.contains("authentication", ignoreCase = true) ||
        errorMessage.contains("auth", ignoreCase = true) ||
        errorMessage.contains("credential", ignoreCase = true) -> {
            R.string.sync_advice_authentication
        }
        errorMessage.contains("permission", ignoreCase = true) ||
        errorMessage.contains("forbidden", ignoreCase = true) ||
        errorMessage.contains("403", ignoreCase = true) -> {
            R.string.sync_advice_permission
        }
        errorMessage.contains("network", ignoreCase = true) ||
        errorMessage.contains("connection", ignoreCase = true) ||
        errorMessage.contains("timeout", ignoreCase = true) ||
        errorMessage.contains("internet connection", ignoreCase = true) ||
        errorMessage.contains("Unable to resolve host", ignoreCase = true) ||
        errorMessage.contains("No address associated with hostname", ignoreCase = true) ||
        errorMessage.contains("Wi-Fi", ignoreCase = true) ||
        errorMessage.contains("mobile data", ignoreCase = true) -> {
            R.string.sync_advice_network
        }
        errorMessage.contains("not found", ignoreCase = true) ||
        errorMessage.contains("404", ignoreCase = true) -> {
            R.string.sync_advice_not_found
        }
        else -> {
            R.string.sync_advice_generic
        }
    }
}

/**
 * Check if error is related to device time/date being incorrect
 */
fun isDeviceTimeError(errorMessage: String?): Boolean {
    if (errorMessage == null) return false
    
    val timeErrorPatterns = listOf(
        "invalid token",
        "token expired",
        "clock skew",
        "certificate verification failed",
        "certificate error",
        "request timestamp",
        "request token has expired",
        "invalid_grant",
        "401",
        "unauthorized"
    )
    
    return timeErrorPatterns.any { pattern ->
        errorMessage.contains(pattern, ignoreCase = true)
    }
}

/**
 * Device Time Error Dialog - Special popup for time/date sync issues
 */
@Composable
fun DeviceTimeErrorDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    onDontTellTodayChanged: (Boolean) -> Unit = {}
) {
    var dontTellToday by remember { mutableStateOf(false) }
    
    if (isVisible) {
        AlertDialog(
            onDismissRequest = {
                if (dontTellToday) {
                    onDontTellTodayChanged(true)
                }
                onDismiss()
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.sync_error_device_time_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Warning message
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.sync_error_device_time_message),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    // Solution steps
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📱 " + stringResource(R.string.sync_error_device_time_solution),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    // Do not tell again checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = dontTellToday,
                            onCheckedChange = { dontTellToday = it },
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.sync_error_dont_tell_today),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dontTellToday) {
                            onDontTellTodayChanged(true)
                        }
                        onOpenSettings()
                    }
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sync_error_device_time_open_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (dontTellToday) {
                            onDontTellTodayChanged(true)
                        }
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.sync_error_dismiss))
                }
            },
            modifier = modifier
        )
    }
}

/**
 * Warning dialog for sync errors after device sleep/resume
 * Shows a simple message directing users to use the resync button at the bottom
 */
@Composable
fun SleepResumeSyncWarningDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        stringResource(R.string.sync_warning_sleep_resume_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Warning message
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.sync_warning_sleep_resume_message),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss
                ) {
                    Text(stringResource(R.string.sync_error_dismiss))
                }
            },
            modifier = modifier
        )
    }
}
