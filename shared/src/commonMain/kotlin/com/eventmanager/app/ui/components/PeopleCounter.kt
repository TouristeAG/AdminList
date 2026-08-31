package com.eventmanager.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.remote.BackendType
import com.eventmanager.app.data.sync.DateFormatUtils
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.vibrateShort
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * People counter per venue, synced to Google Sheets (venues tab columns E–G).
 * Single-writer arbitration via "Priority Device ID" (column F) on the sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeopleCounter(
    isPhone: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: EventManagerViewModel
) {
    val platformContext = LocalPlatformContext.current
    val settingsManager = remember(platformContext) { settingsManagerFor(platformContext) }
    val deviceId = remember { settingsManager.getOrCreatePersistentDeviceId() }

    val venues by viewModel.venues.collectAsState()
    val selectedVenueId by viewModel.peopleCounterSelectedVenueId.collectAsState()
    val priority by viewModel.peopleCounterPriority.collectAsState()
    val hint by viewModel.peopleCounterUiHint.collectAsState()
    val prioritySwitchInteraction = remember { MutableInteractionSource() }
    val isFirebaseBackend = viewModel.getActiveBackendType() == BackendType.FIREBASE

    val peopleCounterTitle = stringResource(Res.string.people_counter_title)
    val peopleCounterHint = stringResource(Res.string.people_counter_hint)
    val peopleCounterPriority = stringResource(Res.string.people_counter_priority)
    val peopleCounterNoVenues = stringResource(Res.string.people_counter_no_venues)
    val peopleCounterSheetSynced = stringResource(Res.string.people_counter_sheet_synced)
    val peopleCounterReset = stringResource(Res.string.people_counter_reset)
    val peopleCounterNoPriorityReadonly = stringResource(Res.string.people_counter_no_priority_readonly)
    val peopleCounterPriorityFirebaseUnknown =
        stringResource(Res.string.people_counter_priority_firebase_account_unknown)

    LaunchedEffect(hint) {
        if (hint == null) return@LaunchedEffect
        delay(10_000L)
        viewModel.clearPeopleCounterUiHint()
    }

    LaunchedEffect(prioritySwitchInteraction) {
        var holdJob: Job? = null
        prioritySwitchInteraction.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    holdJob?.cancel()
                    holdJob = launch {
                        delay(3_000L)
                        viewModel.forceTakePeopleCounterPriority()
                    }
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    holdJob?.cancel()
                    holdJob = null
                }
            }
        }
    }

    val activeVenues = remember(venues) { venues.filter { it.isActive }.sortedBy { it.name } }
    val selectedVenue = remember(venues, selectedVenueId) { venues.find { it.id == selectedVenueId } }
    val writerId = selectedVenue?.peopleCounterWriterDeviceId?.trim().orEmpty()
    val writerAccountEmail = selectedVenue?.peopleCounterWriterAccountEmail?.trim().orEmpty()
    val anotherDeviceHasPriority = writerId.isNotEmpty() && writerId != deviceId
    val canEdit = selectedVenue != null && priority && (writerId.isEmpty() || writerId == deviceId)

    LaunchedEffect(venues) {
        viewModel.reconcilePeopleCounterAfterVenuesChanged()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            viewModel.refreshVenuesForPeopleCounterQuietly()
        }
    }

    var lastAction by remember { mutableStateOf("") }
    val scale by animateFloatAsState(
        targetValue = if (lastAction == "increment" || lastAction == "decrement") 1.05f else 1f,
        label = "counterScale"
    )
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

    val count = selectedVenue?.peopleCounterCount ?: 0
    val sheetMod = selectedVenue?.peopleCounterLastModified ?: 0L

    var sheetClockTick by remember { mutableStateOf(0) }
    LaunchedEffect(sheetMod) {
        if (sheetMod <= 0L) return@LaunchedEffect
        while (true) {
            delay(1000L)
            sheetClockTick++
        }
    }

    val resetCountAnim = remember { Animatable(0f) }
    var isResetCountAnimation by remember { mutableStateOf(false) }
    var resetProgressFireConsumed by remember { mutableStateOf(false) }

    var isResetting by remember { mutableStateOf(false) }
    val longPressDuration = 600L
    val animatedResetProgress by animateFloatAsState(
        targetValue = if (isResetting) 1f else 0f,
        animationSpec = tween(durationMillis = longPressDuration.toInt(), easing = LinearEasing),
        label = "resetProgress"
    )

    LaunchedEffect(selectedVenueId) {
        if (isResetCountAnimation) {
            resetCountAnim.stop()
            resetCountAnim.snapTo(0f)
            isResetCountAnimation = false
        }
        resetProgressFireConsumed = false
    }

    LaunchedEffect(isResetting, animatedResetProgress) {
        if (!isResetting && animatedResetProgress < 0.2f) {
            resetProgressFireConsumed = false
        }
    }

    LaunchedEffect(canEdit, selectedVenueId) {
        snapshotFlow {
            Triple(
                animatedResetProgress,
                isResetting,
                selectedVenue?.id ?: -1L
            )
        }.collect { (progress, resetting, _) ->
            if (progress < 0.99f || !resetting || !canEdit || selectedVenue == null || resetProgressFireConsumed) {
                return@collect
            }
            resetProgressFireConsumed = true
            lastAction = "reset"
            vibrateShort(platformContext)
            isResetting = false
            val venue = selectedVenue ?: return@collect
            val venueId = venue.id
            val start = venue.peopleCounterCount.coerceAtLeast(0)
            if (start > 0) {
                isResetCountAnimation = true
                resetCountAnim.snapTo(start.toFloat())
                try {
                    resetCountAnim.animateTo(
                        0f,
                        animationSpec = tween(
                            durationMillis = 420,
                            easing = LinearEasing
                        )
                    )
                } finally {
                    isResetCountAnimation = false
                    resetCountAnim.snapTo(0f)
                }
            }
            viewModel.resetPeopleCounterForVenue(venueId)
        }
    }

    val displayedCount = if (isResetCountAnimation) {
        resetCountAnim.value.toInt().coerceAtLeast(0)
    } else {
        count
    }

    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 26.dp else 30.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
        ),
        border = BorderStroke(
            1.dp,
            scheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 18.dp else 22.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = if (isPhone) 6.dp else 8.dp)
            ) {
                activeVenues.forEach { v ->
                    FilterChip(
                        selected = v.id == selectedVenueId,
                        onClick = { viewModel.setPeopleCounterSelectedVenueId(v.id) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (viewModel.isFirebaseAllOrgsMode() && v.firebaseOrgId.isNotBlank()) {
                                    OrgColorDot(orgId = v.firebaseOrgId, viewModel = viewModel, size = 8.dp)
                                }
                                Text(
                                    v.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isPhone) 10.dp else 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isPhone) 48.dp else 56.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    scheme.primaryContainer,
                                    scheme.secondaryContainer.copy(alpha = 0.85f)
                                )
                            ),
                            shape = RoundedCornerShape(if (isPhone) 14.dp else 16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(if (isPhone) 28.dp else 32.dp),
                        tint = scheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peopleCounterTitle,
                        style = if (isPhone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = peopleCounterHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Text(
                        text = peopleCounterPriority,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = if (isPhone) 100.dp else 120.dp)
                    )
                    Box(
                        modifier = Modifier
                            .scale(0.78f)
                            .wrapContentSize(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Switch(
                            checked = priority,
                            onCheckedChange = { viewModel.setPeopleCounterPriority(it) },
                            interactionSource = prioritySwitchInteraction,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = scheme.onPrimary,
                                checkedTrackColor = scheme.primary,
                                uncheckedThumbColor = scheme.outline,
                                uncheckedTrackColor = scheme.surfaceContainerHighest,
                                uncheckedBorderColor = scheme.outline.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }

            val showPriorityHint = hint != null &&
                !(anotherDeviceHasPriority &&
                    (hint == PeopleCounterUiHint.AnotherDeviceBlocked ||
                        hint == PeopleCounterUiHint.PriorityLost))
            if (showPriorityHint) {
                Text(
                    text = peopleCounterHintMessage(hint!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            if (anotherDeviceHasPriority) {
                Text(
                    text = peopleCounterNoPriorityReadonly,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isFirebaseBackend && writerAccountEmail.isNotBlank()) 4.dp else 8.dp)
                )
                if (isFirebaseBackend) {
                    Text(
                        text = if (writerAccountEmail.isNotBlank()) {
                            stringResource(Res.string.people_counter_priority_firebase_account, writerAccountEmail)
                        } else {
                            peopleCounterPriorityFirebaseUnknown
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }

            if (activeVenues.isEmpty()) {
                Text(
                    text = peopleCounterNoVenues,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isPhone) 12.dp else 16.dp)
                        .alpha(if (canEdit) 1f else 0.55f),
                    color = scheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(if (isPhone) 16.dp else 20.dp),
                    tonalElevation = 1.dp,
                    border = BorderStroke(
                        1.5.dp,
                        scheme.outline.copy(alpha = 0.35f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isPhone) 24.dp else 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayedCount.toString(),
                            style = if (isPhone) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (canEdit) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.scale(scale)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))

                if (sheetMod > 0L) {
                    val formatted = remember(sheetMod, sheetClockTick) {
                        DateFormatUtils.formatRelativeSinceSync(platformContext, sheetMod)
                    }
                    Text(
                        text = "$peopleCounterSheetSynced: $formatted",
                        style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isPhone) 8.dp else 12.dp)
                            .clickable { viewModel.resyncPeopleCounterLastUpdatedLine() },
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isPhone) 56.dp else 64.dp)
                            .alpha(if (canEdit) 1f else 0.45f)
                            .combinedClickable(
                                onClick = {
                                    if (!canEdit || selectedVenue == null) return@combinedClickable
                                    if (count > 0) {
                                        lastAction = "decrement"
                                        vibrateShort(platformContext)
                                        viewModel.adjustPeopleCounterCount(selectedVenue.id, -1)
                                    }
                                },
                                onLongClick = {
                                    if (!canEdit || selectedVenue == null) return@combinedClickable
                                    if (count >= 10) {
                                        lastAction = "decrement"
                                        vibrateShort(platformContext)
                                        viewModel.adjustPeopleCounterCount(selectedVenue.id, -10)
                                    }
                                }
                            )
                            .scale(minusScale),
                        shape = RoundedCornerShape(if (isPhone) 14.dp else 16.dp),
                        color = scheme.secondaryContainer,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = null,
                                modifier = Modifier.size(if (isPhone) 24.dp else 28.dp),
                                tint = scheme.onSecondaryContainer
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isPhone) 56.dp else 64.dp)
                            .alpha(if (canEdit) 1f else 0.45f)
                            .combinedClickable(
                                onClick = {
                                    if (!canEdit || selectedVenue == null) return@combinedClickable
                                    lastAction = "increment"
                                    vibrateShort(platformContext)
                                    viewModel.adjustPeopleCounterCount(selectedVenue.id, 1)
                                },
                                onLongClick = {
                                    if (!canEdit || selectedVenue == null) return@combinedClickable
                                    lastAction = "increment"
                                    vibrateShort(platformContext)
                                    viewModel.adjustPeopleCounterCount(selectedVenue.id, 10)
                                }
                            )
                            .scale(plusScale),
                        shape = RoundedCornerShape(if (isPhone) 14.dp else 16.dp),
                        color = scheme.primary,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(if (isPhone) 24.dp else 28.dp),
                                tint = scheme.onPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isPhone) 12.dp else 16.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isPhone) 48.dp else 56.dp)
                        .alpha(if (canEdit) 1f else 0.45f)
                        .pointerInput(canEdit, selectedVenue) {
                            if (!canEdit || selectedVenue == null) return@pointerInput
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
                    color = scheme.surfaceContainerHigh,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(scheme.surfaceContainerHigh)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedResetProgress)
                                .background(scheme.primaryContainer.copy(alpha = 0.65f))
                        )
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
                                tint = scheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = peopleCounterReset,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun peopleCounterHintMessage(hint: PeopleCounterUiHint): String = when (hint) {
    PeopleCounterUiHint.NeedSheets -> stringResource(Res.string.people_counter_need_sheets)
    PeopleCounterUiHint.SelectVenue -> stringResource(Res.string.people_counter_select_venue)
    PeopleCounterUiHint.AnotherDeviceBlocked -> stringResource(Res.string.people_counter_another_device)
    PeopleCounterUiHint.NoSheetRow -> stringResource(Res.string.people_counter_no_sheet_row)
    PeopleCounterUiHint.PriorityLost -> stringResource(Res.string.people_counter_priority_lost)
}
