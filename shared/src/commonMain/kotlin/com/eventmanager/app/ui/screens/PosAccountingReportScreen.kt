package com.eventmanager.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.SalesSheetItem
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.reports.PosAccountingReport
import com.eventmanager.app.data.reports.PosAccountingReportBuilder
import com.eventmanager.app.data.reports.PosReportPeriod
import com.eventmanager.app.data.reports.PosReportPeriodMode
import com.eventmanager.app.data.reports.PosReportVenueScope
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.utils.DateTimeUtils
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.PlatformBackHandler
import com.eventmanager.app.platform.PlatformFileManager
import com.eventmanager.app.platform.isDesktop
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.cancel
import com.eventmanager.app.resources.ok
import com.eventmanager.app.resources.pos_report_closure_override
import com.eventmanager.app.resources.pos_report_closure_time
import com.eventmanager.app.resources.pos_report_evening_help
import com.eventmanager.app.resources.pos_report_generate
import com.eventmanager.app.resources.pos_report_generating
import com.eventmanager.app.resources.pos_report_mode_evening
import com.eventmanager.app.resources.pos_report_mode_period
import com.eventmanager.app.resources.pos_report_period_end
import com.eventmanager.app.resources.pos_report_period_help
import com.eventmanager.app.resources.pos_report_period_start
import com.eventmanager.app.resources.pos_report_preview_cash
import com.eventmanager.app.resources.pos_report_preview_credit
import com.eventmanager.app.resources.pos_report_preview_transfers
import com.eventmanager.app.resources.pos_report_select_date
import com.eventmanager.app.resources.pos_report_venue_scope_all
import com.eventmanager.app.resources.pos_report_venue_scope_global
import com.eventmanager.app.resources.pos_venue_selector_label
import com.eventmanager.app.resources.pos_venue_global
import com.eventmanager.app.resources.select_time
import com.eventmanager.app.resources.pos_report_title
import com.eventmanager.app.resources.select_date
import com.eventmanager.app.ui.components.PosReportExportBridge
import com.eventmanager.app.ui.components.PosReportPreviewDialog
import com.eventmanager.app.ui.utils.isTablet
import com.eventmanager.app.utils.PosReportPdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.util.Calendar
import java.util.TimeZone

private const val VENUE_FILTER_ALL = "__ALL__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosAccountingReportScreen(
    transfers: List<AccountTransfer>,
    salesItems: List<SalesSheetItem>,
    venues: List<VenueEntity> = emptyList(),
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val platformContext = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val settingsOffset = remember { settingsManager.getDateChangeOffsetHours() }
    val currencyCode = remember { settingsManager.getCurrencyCode() }
    val defaultClosure = remember(settingsOffset) {
        PosAccountingReportBuilder.defaultClosureFromOffset(settingsOffset)
    }

    var mode by remember { mutableStateOf(PosReportPeriodMode.EVENING) }
    var eveningDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var periodStartMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var periodEndMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var overrideClosure by remember { mutableStateOf(false) }
    var closureHour by remember { mutableIntStateOf(defaultClosure.first) }
    var closureMinute by remember { mutableIntStateOf(defaultClosure.second) }

    var venueFilterKey by remember { mutableStateOf(VENUE_FILTER_ALL) }
    val activeVenues = remember(venues) { venues.filter { it.isActive } }

    var isGenerating by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewReport by remember { mutableStateOf<PosAccountingReport?>(null) }

    val period = remember(
        mode, eveningDateMs, periodStartMs, periodEndMs,
        settingsOffset, overrideClosure, closureHour, closureMinute,
        venueFilterKey, activeVenues,
    ) {
        val (venueScope, venueName, venueLabel) = resolveVenueFilter(venueFilterKey, activeVenues)
        buildPeriod(
            mode = mode,
            eveningDateMs = eveningDateMs,
            periodStartMs = periodStartMs,
            periodEndMs = periodEndMs,
            settingsOffset = settingsOffset,
            closureHour = if (overrideClosure || mode == PosReportPeriodMode.EVENING) closureHour else defaultClosure.first,
            closureMinute = if (overrideClosure || mode == PosReportPeriodMode.EVENING) closureMinute else defaultClosure.second,
            venueScope = venueScope,
            venueName = venueName,
            venueLabel = venueLabel,
        )
    }

    val liveReport = remember(transfers, salesItems, period, currencyCode) {
        PosAccountingReportBuilder.build(
            transfers = transfers,
            salesItems = salesItems,
            period = period,
            currencyCode = currencyCode,
        )
    }

    LaunchedEffect(settingsOffset, overrideClosure) {
        if (!overrideClosure) {
            closureHour = defaultClosure.first
            closureMinute = defaultClosure.second
        }
    }

    val reportTitle = stringResource(Res.string.pos_report_title)

    // Must register after the root admin BackHandler so system back returns to
    // dashboard instead of exiting admin (same pattern as job/venue management).
    PlatformBackHandler {
        if (previewFile != null) {
            previewFile = null
            previewReport = null
        } else {
            onBack()
        }
    }

    if (previewFile != null && previewReport != null) {
        PosReportPreviewDialog(
            file = previewFile!!,
            report = previewReport!!,
            onDismiss = {
                previewFile = null
                previewReport = null
            },
            onShare = {
                PosReportExportBridge.shareReport(platformContext, previewFile!!, reportTitle)
            },
            onOpen = { PosReportExportBridge.openReport(platformContext, previewFile!!) },
            onSaveAs = if (!isPhone) {
                {
                    scope.launch {
                        PosReportExportBridge.saveReportToUserLocation(
                            platformContext,
                            previewFile!!,
                            previewFile!!.name,
                        )
                    }
                }
            } else null,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.pos_report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == PosReportPeriodMode.EVENING,
                    onClick = { mode = PosReportPeriodMode.EVENING },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(Res.string.pos_report_mode_evening)) }
                SegmentedButton(
                    selected = mode == PosReportPeriodMode.PERIOD,
                    onClick = { mode = PosReportPeriodMode.PERIOD },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(Res.string.pos_report_mode_period)) }
            }

            ReportVenueFilterCard(
                venueFilterKey = venueFilterKey,
                activeVenues = activeVenues,
                onVenueFilterKeyChanged = { venueFilterKey = it },
            )

            when (mode) {
                PosReportPeriodMode.EVENING -> {
                    DateOnlyPicker(
                        label = stringResource(Res.string.pos_report_select_date),
                        selectedMs = eveningDateMs,
                        onSelected = { eveningDateMs = it },
                    )
                    ClosureCard(
                        closureHour = closureHour,
                        closureMinute = closureMinute,
                        overrideClosure = overrideClosure,
                        onOverrideChanged = { overrideClosure = it },
                        onTimeChanged = { h, m ->
                            closureHour = h
                            closureMinute = m
                        },
                    )
                    HelpCard(
                        stringResource(
                            Res.string.pos_report_evening_help,
                            PosAccountingReportBuilder.formatDateOnly(eveningDateMs),
                            period.closureLabel ?: "",
                            period.label,
                        ),
                    )
                }
                PosReportPeriodMode.PERIOD -> {
                    DateOnlyPicker(
                        label = stringResource(Res.string.pos_report_period_start),
                        selectedMs = periodStartMs,
                        onSelected = { periodStartMs = it },
                    )
                    DateOnlyPicker(
                        label = stringResource(Res.string.pos_report_period_end),
                        selectedMs = periodEndMs,
                        onSelected = { periodEndMs = it },
                    )
                    HelpCard(stringResource(Res.string.pos_report_period_help, period.label))
                }
            }

            PreviewStatsCard(
                transferCount = liveReport.totalTransferCount,
                posCount = liveReport.totalPosSalesCount,
                cashTotal = liveReport.totalCashCollected,
                creditTotal = liveReport.totalCreditUsed,
                currency = currencyCode,
            )

            OutlinedButton(
                onClick = {
                    if (isGenerating) return@OutlinedButton
                    isGenerating = true
                    scope.launch {
                        try {
                            val report = liveReport
                            val file = withContext(Dispatchers.IO) {
                                val cache = PlatformFileManager(platformContext).getCacheDirectory()
                                PosReportPdfRenderer.render(report, cache)
                            }
                            previewReport = report
                            previewFile = file
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isGenerating && liveReport.totalTransferCount >= 0,
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(Res.string.pos_report_generating))
                } else {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.pos_report_generate))
                }
            }
        }
    }
}

@Composable
private fun PreviewStatsCard(
    transferCount: Int,
    posCount: Int,
    cashTotal: Double,
    creditTotal: Double,
    currency: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.pos_report_preview_transfers, transferCount, posCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(Res.string.pos_report_preview_cash, formatMoney(cashTotal, currency)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(Res.string.pos_report_preview_credit, formatMoney(creditTotal, currency)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HelpCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClosureCard(
    closureHour: Int,
    closureMinute: Int,
    overrideClosure: Boolean,
    onOverrideChanged: (Boolean) -> Unit,
    onTimeChanged: (Int, Int) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        stringResource(Res.string.pos_report_closure_time),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        PosAccountingReportBuilder.formatClosureLabel(closureHour, closureMinute),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(checked = overrideClosure, onCheckedChange = onOverrideChanged)
            }
            Text(
                stringResource(Res.string.pos_report_closure_override),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (overrideClosure) {
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(PosAccountingReportBuilder.formatClosureLabel(closureHour, closureMinute))
                }
            }
        }
    }
    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = closureHour,
            initialMinute = closureMinute,
            is24Hour = true,
        )
        val useDial = LocalPlatformContext.current.isDesktop || isTablet()
        PosReportTimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChanged(state.hour, state.minute)
                    showTimePicker = false
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        ) {
            // Dial TimePicker needs a wide dialog; under phone resolution scaling it
            // collapses. Use digital TimeInput on phone (same AlertDialog pattern as
            // the rest of the Android app).
            if (useDial) TimePicker(state = state) else TimeInput(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOnlyPicker(
    label: String,
    selectedMs: Long,
    onSelected: (Long) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(DateTimeUtils.formatGenevaDateOnly(selectedMs))
            }
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedMs)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(stringResource(Res.string.select_date)) },
            text = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    DatePicker(state = state)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateMs ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Zurich"))
                        cal.timeInMillis = dateMs
                        cal.set(Calendar.HOUR_OF_DAY, 12)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        onSelected(cal.timeInMillis)
                    }
                    showPicker = false
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

private fun buildPeriod(
    mode: PosReportPeriodMode,
    eveningDateMs: Long,
    periodStartMs: Long,
    periodEndMs: Long,
    settingsOffset: Int,
    closureHour: Int,
    closureMinute: Int,
    venueScope: PosReportVenueScope = PosReportVenueScope.ALL,
    venueName: String? = null,
    venueLabel: String? = null,
): PosReportPeriod {
    val (start, end) = when (mode) {
        PosReportPeriodMode.EVENING -> PosAccountingReportBuilder.computeEveningRange(
            eveningDateMs, settingsOffset, closureHour, closureMinute,
        )
        PosReportPeriodMode.PERIOD -> {
            val s = minOf(periodStartMs, periodEndMs)
            val e = maxOf(periodStartMs, periodEndMs)
            PosAccountingReportBuilder.computePeriodRange(s, e, settingsOffset)
        }
    }
    val closureLabel = PosAccountingReportBuilder.formatClosureLabel(closureHour, closureMinute)
    return PosReportPeriod(
        mode = mode,
        startMs = start,
        endMs = end,
        label = PosAccountingReportBuilder.formatPeriodLabel(start, end),
        closureLabel = if (mode == PosReportPeriodMode.EVENING) closureLabel else null,
        settingsOffsetHours = settingsOffset,
        closureHour = closureHour,
        closureMinute = closureMinute,
        venueScope = venueScope,
        venueName = venueName,
        venueLabel = venueLabel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportVenueFilterCard(
    venueFilterKey: String,
    activeVenues: List<VenueEntity>,
    onVenueFilterKeyChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (venueFilterKey) {
        VENUE_FILTER_ALL -> stringResource(Res.string.pos_report_venue_scope_all)
        com.eventmanager.app.data.models.PosVenueScope.GLOBAL -> stringResource(Res.string.pos_report_venue_scope_global)
        else -> venueFilterKey
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.pos_venue_selector_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.pos_report_venue_scope_all)) },
                        onClick = {
                            onVenueFilterKeyChanged(VENUE_FILTER_ALL)
                            expanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.pos_report_venue_scope_global)) },
                        onClick = {
                            onVenueFilterKeyChanged(com.eventmanager.app.data.models.PosVenueScope.GLOBAL)
                            expanded = false
                        },
                    )
                    activeVenues.forEach { venue ->
                        DropdownMenuItem(
                            text = { Text(venue.name) },
                            onClick = {
                                onVenueFilterKeyChanged(venue.name)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun resolveVenueFilter(
    venueFilterKey: String,
    activeVenues: List<VenueEntity>,
): Triple<PosReportVenueScope, String?, String?> = when (venueFilterKey) {
    VENUE_FILTER_ALL -> Triple(PosReportVenueScope.ALL, null, null)
    com.eventmanager.app.data.models.PosVenueScope.GLOBAL -> Triple(
        PosReportVenueScope.GLOBAL,
        com.eventmanager.app.data.models.PosVenueScope.GLOBAL,
        com.eventmanager.app.data.models.PosVenueScope.GLOBAL,
    )
    else -> Triple(PosReportVenueScope.VENUE, venueFilterKey, venueFilterKey)
}

private fun formatMoney(value: Double, currency: String): String =
    String.format("%.2f %s", value, currency)

@Composable
private fun PosReportTimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.select_time)) },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}
