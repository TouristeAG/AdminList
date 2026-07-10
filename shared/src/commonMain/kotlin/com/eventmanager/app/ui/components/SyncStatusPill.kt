package com.eventmanager.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.sync.DateFormatUtils
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.last_update_line
import com.eventmanager.app.resources.last_update_none_line
import com.eventmanager.app.resources.manual_sync_now
import com.eventmanager.app.resources.syncing
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun SyncStatusPill(
    viewModel: EventManagerViewModel,
    modifier: Modifier = Modifier,
    onSync: (() -> Unit)? = null,
) {
    val platformContext = LocalPlatformContext.current
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    var syncPillTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(lastSyncTime) {
        if (lastSyncTime <= 0L) return@LaunchedEffect
        while (true) {
            delay(30_000L)
            syncPillTick++
        }
    }

    val timeAgoLabel = remember(lastSyncTime, syncPillTick) {
        if (lastSyncTime <= 0L) {
            null
        } else {
            DateFormatUtils.formatSyncPillTimeAgo(platformContext, lastSyncTime)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isSyncing) 0.95f else 1f,
        animationSpec = tween(100),
        label = "sync_pill_scale",
    )

    Card(
        modifier = modifier
            .padding(4.dp)
            .clickable(
                enabled = !isSyncing,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (onSync != null) {
                        onSync()
                    } else {
                        viewModel.performDifferentialFullSync()
                    }
                },
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSyncing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed && !isSyncing) 4.dp else 2.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .scale(scale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.syncing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = stringResource(Res.string.manual_sync_now),
                    modifier = Modifier.size(16.dp),
                    tint = if (lastSyncTime > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = if (lastSyncTime <= 0L) {
                        stringResource(Res.string.last_update_none_line)
                    } else {
                        stringResource(Res.string.last_update_line, timeAgoLabel.orEmpty())
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lastSyncTime > 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
