package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.R
import android.app.Activity
import android.content.Context
import android.os.Process
import kotlin.system.exitProcess
import kotlinx.coroutines.launch

/**
 * Dialog to prompt app restart when changes require it
 * For example: app icon changes require a full restart to appear in the launcher
 */
@Composable
fun AppRestartDialog(
    isVisible: Boolean,
    titleResId: Int = R.string.app_icon_restart_title,
    messageResId: Int = R.string.app_icon_restart_message,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasRestarted by remember { mutableStateOf(false) }
    
    // Reset restart flag when dialog becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            hasRestarted = false
        }
    }
    
    if (isVisible && !hasRestarted) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        stringResource(titleResId),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            text = {
                Text(
                    stringResource(messageResId),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Left,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Mark as restarted to prevent multiple calls
                        hasRestarted = true
                        // Dismiss dialog first
                        onDismiss()
                        // Then restart after a brief delay to allow dialog to close
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(200)
                            restartApp(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .height(44.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 6.dp)
                    )
                    Text(
                        stringResource(R.string.app_icon_restart_now),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .height(44.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.app_icon_restart_later),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Restarts the application gracefully
 * This is necessary for icon changes to take effect (launcher needs to re-read component state)
 */
fun restartApp(context: Context) {
    val activity = context as? Activity
    if (activity != null) {
        try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                
                Thread(Runnable {
                    Thread.sleep(300)
                    exitProcess(0)
                }).start()
            }
        } catch (e: Exception) {
            try {
                activity.recreate()
            } catch (ex: Exception) {
                println("Error restarting: ${ex.message}")
            }
        }
    }
}

