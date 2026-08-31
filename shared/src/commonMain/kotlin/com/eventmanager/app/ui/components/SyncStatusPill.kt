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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
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
import com.eventmanager.app.data.remote.BackendType
import com.eventmanager.app.data.remote.FirebaseSyncStatus
import com.eventmanager.app.data.remote.FirebaseSyncTransport
import com.eventmanager.app.data.sync.DateFormatUtils
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.firebase_pill_failed_pending
import com.eventmanager.app.resources.firebase_pill_line
import com.eventmanager.app.resources.firebase_pill_live
import com.eventmanager.app.resources.firebase_pill_not_setup
import com.eventmanager.app.resources.firebase_pill_offline
import com.eventmanager.app.resources.firebase_pill_pending
import com.eventmanager.app.resources.firebase_pill_pull
import com.eventmanager.app.resources.firebase_pill_refresh_cd
import com.eventmanager.app.resources.last_update_line
import com.eventmanager.app.resources.last_update_none_line
import com.eventmanager.app.resources.manual_sync_now
import com.eventmanager.app.resources.syncing
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val FIREBASE_LIVE_RECENT_MS = 120_000L

@Composable
fun SyncStatusPill(
    viewModel: EventManagerViewModel,
    modifier: Modifier = Modifier,
    onSync: (() -> Unit)? = null,
) {
    if (viewModel.getActiveBackendType() == BackendType.FIREBASE) {
        FirebaseSyncStatusPill(
            viewModel = viewModel,
            modifier = modifier,
            onSync = onSync,
        )
    } else {
        SheetsSyncStatusPill(
            viewModel = viewModel,
            modifier = modifier,
            onSync = onSync,
        )
    }
}

@Composable
private fun SheetsSyncStatusPill(
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

    val label = if (lastSyncTime <= 0L) {
        stringResource(Res.string.last_update_none_line)
    } else {
        stringResource(Res.string.last_update_line, timeAgoLabel.orEmpty())
    }
    val isHealthy = lastSyncTime > 0L

    SyncStatusPillCard(
        modifier = modifier,
        isSyncing = isSyncing,
        onClick = {
            if (onSync != null) {
                onSync()
            } else {
                viewModel.performDifferentialFullSync()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = stringResource(Res.string.manual_sync_now),
                modifier = Modifier.size(16.dp),
                tint = if (isHealthy) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        label = label,
        labelColor = if (isHealthy) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun FirebaseSyncStatusPill(
    viewModel: EventManagerViewModel,
    modifier: Modifier = Modifier,
    onSync: (() -> Unit)? = null,
) {
    val platformContext = LocalPlatformContext.current
    val isSyncing by viewModel.isSyncing.collectAsState()
    val firebaseStatus by viewModel.firebaseSyncStatus.collectAsState()
    var syncPillTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(firebaseStatus.lastActivityAt, firebaseStatus.mode) {
        if (firebaseStatus.lastActivityAt <= 0L) return@LaunchedEffect
        while (true) {
            delay(30_000L)
            syncPillTick++
        }
    }

    val timeAgoLabel = remember(firebaseStatus.lastActivityAt, syncPillTick) {
        if (firebaseStatus.lastActivityAt <= 0L) {
            null
        } else {
            DateFormatUtils.formatSyncPillTimeAgo(platformContext, firebaseStatus.lastActivityAt)
        }
    }

    val label = firebasePillLabel(firebaseStatus, timeAgoLabel)
    val hasPending = firebaseStatus.pendingWriteCount > 0
    val hasFailedPending = firebaseStatus.failedPendingWriteCount > 0
    val isLiveHealthy = firebaseStatus.mode == FirebaseSyncTransport.LIVE &&
        !hasPending &&
        !hasFailedPending &&
        (firebaseStatus.lastActivityAt <= 0L ||
            System.currentTimeMillis() - firebaseStatus.lastActivityAt <= FIREBASE_LIVE_RECENT_MS)

    val iconVector = when {
        hasFailedPending -> Icons.Default.SyncProblem
        hasPending -> Icons.Default.SyncProblem
        firebaseStatus.mode == FirebaseSyncTransport.LIVE -> Icons.Default.CloudDone
        firebaseStatus.mode == FirebaseSyncTransport.PULL -> Icons.Default.CloudQueue
        else -> Icons.Default.CloudOff
    }
    val iconTint = when {
        hasFailedPending -> MaterialTheme.colorScheme.error
        hasPending -> MaterialTheme.colorScheme.error
        isLiveHealthy -> MaterialTheme.colorScheme.primary
        firebaseStatus.mode == FirebaseSyncTransport.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val labelColor = when {
        hasFailedPending -> MaterialTheme.colorScheme.error
        hasPending -> MaterialTheme.colorScheme.error
        isLiveHealthy || firebaseStatus.mode != FirebaseSyncTransport.OFFLINE ->
            MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    SyncStatusPillCard(
        modifier = modifier,
        isSyncing = isSyncing,
        onClick = {
            if (onSync != null) {
                onSync()
            } else {
                viewModel.performDifferentialFullSync()
            }
        },
        icon = {
            Icon(
                imageVector = iconVector,
                contentDescription = stringResource(Res.string.firebase_pill_refresh_cd),
                modifier = Modifier.size(16.dp),
                tint = iconTint,
            )
        },
        label = label,
        labelColor = labelColor,
    )
}

@Composable
private fun firebasePillLabel(
    status: FirebaseSyncStatus,
    timeAgoLabel: String?,
): String {
    if (status.failedPendingWriteCount > 0) {
        return stringResource(Res.string.firebase_pill_failed_pending, status.failedPendingWriteCount)
    }
    if (status.pendingWriteCount > 0) {
        return stringResource(Res.string.firebase_pill_pending, status.pendingWriteCount)
    }
    when (status.mode) {
        FirebaseSyncTransport.OFFLINE -> {
            return if (!status.orgConfigured) {
                stringResource(Res.string.firebase_pill_not_setup)
            } else {
                stringResource(Res.string.firebase_pill_offline)
            }
        }
        FirebaseSyncTransport.PULL -> {
            if (status.lastActivityAt > 0L && timeAgoLabel != null) {
                return stringResource(Res.string.firebase_pill_line, timeAgoLabel)
            }
            return stringResource(Res.string.firebase_pill_pull)
        }
        FirebaseSyncTransport.LIVE -> {
            if (status.lastActivityAt <= 0L) {
                return stringResource(Res.string.firebase_pill_live)
            }
            val recent = System.currentTimeMillis() - status.lastActivityAt <= FIREBASE_LIVE_RECENT_MS
            if (recent) {
                return stringResource(Res.string.firebase_pill_live)
            }
            return stringResource(Res.string.firebase_pill_line, timeAgoLabel.orEmpty())
        }
    }
}

@Composable
private fun SyncStatusPillCard(
    modifier: Modifier = Modifier,
    isSyncing: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    labelColor: androidx.compose.ui.graphics.Color,
) {
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
                onClick = onClick,
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
                icon()
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
            }
        }
    }
}
