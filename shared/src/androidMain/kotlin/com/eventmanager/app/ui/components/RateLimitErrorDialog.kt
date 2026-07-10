package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.R

@Composable
fun RateLimitErrorDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(context.getString(R.string.rate_limit_dialog_title))
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                text = "⚠️ ${context.getString(R.string.rate_limit_too_many_requests)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = context.getString(R.string.rate_limit_wait_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Text(
                        text = context.getString(R.string.rate_limit_current_limits),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LimitInfoItem(
                            context.getString(R.string.rate_limit_read_requests),
                            context.getString(R.string.rate_limit_read_value),
                        )
                        LimitInfoItem(
                            context.getString(R.string.rate_limit_write_requests),
                            context.getString(R.string.rate_limit_write_value),
                        )
                        LimitInfoItem(
                            context.getString(R.string.rate_limit_per_user_requests),
                            context.getString(R.string.rate_limit_per_user_value),
                        )
                        LimitInfoItem(
                            context.getString(R.string.rate_limit_per_100_seconds),
                            context.getString(R.string.rate_limit_per_100_value),
                        )
                    }

                    Text(
                        text = context.getString(R.string.rate_limit_what_to_do),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ActionItem("⏰", context.getString(R.string.rate_limit_action_wait))
                        ActionItem("🔄", context.getString(R.string.rate_limit_action_auto_retry))
                        ActionItem("⚙️", context.getString(R.string.rate_limit_action_reduce_freq))
                        ActionItem("📊", context.getString(R.string.rate_limit_action_check_quota))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "💡 ${context.getString(R.string.rate_limit_tips_title)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = context.getString(R.string.rate_limit_tips_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onRetry,
                    enabled = true
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.rate_limit_retry_sync))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(context.getString(R.string.close))
                }
            },
            modifier = modifier
        )
    }
}

@Composable
private fun LimitInfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ActionItem(
    icon: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
