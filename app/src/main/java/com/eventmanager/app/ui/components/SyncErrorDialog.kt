package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmanager.app.R

/**
 * Dialog to display sync errors with detailed information
 * Includes a "do not tell me again today" checkbox
 */
@Composable
fun SyncErrorDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
    errorMessage: String,
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
            },
            text = {
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
                                text = "Error Details:",
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
                                text = "💡 What to do:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            
                            val advice = getErrorAdvice(errorMessage)
                            Text(
                                text = advice,
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
                        onRetry()
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sync_error_retry))
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
 * Get contextual advice based on the error message
 */
private fun getErrorAdvice(errorMessage: String): String {
    return when {
        errorMessage.contains("429", ignoreCase = true) || 
        errorMessage.contains("rate limit", ignoreCase = true) ||
        errorMessage.contains("Rate limit", ignoreCase = true) -> {
            "• Wait 1-2 minutes before retrying\n" +
            "• The app will automatically retry with backoff\n" +
            "• Consider reducing sync frequency in settings"
        }
        errorMessage.contains("authentication", ignoreCase = true) ||
        errorMessage.contains("auth", ignoreCase = true) ||
        errorMessage.contains("credential", ignoreCase = true) -> {
            "• Check your service account key file\n" +
            "• Verify the key is uploaded in Settings\n" +
            "• Re-upload the key if needed"
        }
        errorMessage.contains("permission", ignoreCase = true) ||
        errorMessage.contains("forbidden", ignoreCase = true) ||
        errorMessage.contains("403", ignoreCase = true) -> {
            "• Share your spreadsheet with the service account email\n" +
            "• Grant Editor access to the service account\n" +
            "• Check the account email in your service key file"
        }
        errorMessage.contains("network", ignoreCase = true) ||
        errorMessage.contains("connection", ignoreCase = true) ||
        errorMessage.contains("timeout", ignoreCase = true) ||
        errorMessage.contains("internet connection", ignoreCase = true) ||
        errorMessage.contains("Unable to resolve host", ignoreCase = true) ||
        errorMessage.contains("No address associated with hostname", ignoreCase = true) ||
        errorMessage.contains("Wi-Fi", ignoreCase = true) ||
        errorMessage.contains("mobile data", ignoreCase = true) -> {
            "• Check your internet connection\n" +
            "• Verify Wi-Fi or mobile data is enabled\n" +
            "• Try again when connection is stable\n" +
            "• Make sure you're not in airplane mode"
        }
        errorMessage.contains("not found", ignoreCase = true) ||
        errorMessage.contains("404", ignoreCase = true) -> {
            "• Check the Spreadsheet ID in Settings\n" +
            "• Verify the sheet names exist\n" +
            "• Ensure the spreadsheet is accessible"
        }
        else -> {
            "• Try the sync again\n" +
            "• Check your Google Sheets settings\n" +
            "• Visit Settings to verify your configuration"
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
