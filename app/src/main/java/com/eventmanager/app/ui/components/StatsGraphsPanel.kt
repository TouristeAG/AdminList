package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.eventmanager.app.data.models.*
import com.eventmanager.app.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.round
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import com.eventmanager.app.utils.GraphExportUtils
import com.eventmanager.app.ui.utils.isTablet
import android.content.Intent
import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip

enum class TimePeriod(val displayName: String, val days: Long, val unitLabel: String) {
    ONE_WEEK("1 Week", 7, "Day"),
    TWO_WEEKS("2 Weeks", 14, "Day"),
    ONE_MONTH("1 Month", 30, "Day"),
    SIX_MONTHS("6 Months", 180, "Week"),
    ONE_YEAR("1 Year", 365, "Month"),
    MAX("All Time", 0, "Dynamic")
}

/**
 * Data class for graph data points
 */
data class DataPoint(
    val label: String,
    val value: Float,
    val timestamp: Long
)

data class InteractionState(
    val isPressed: Boolean = false,
    val xPosition: Float = 0f,
    val hoveredPointIndex: Int = -1,
    val hoveredValue: Float = 0f,
    val hoveredLabel: String = ""
)

@Composable
fun StatsGraphsPanel(
    volunteers: List<Volunteer>,
    guests: List<Guest>,
    jobs: List<Job>,
    venues: List<VenueEntity> = emptyList(),
    jobTypeConfigs: List<JobTypeConfig> = emptyList(),
    isPhone: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { com.eventmanager.app.data.sync.SettingsManager(context) }
    val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
    
    // Load saved time period or default to ONE_MONTH
    val savedPeriodName = remember { settingsManager.getSelectedGraphTimePeriod() }
    val savedPeriod = remember { 
        try {
            TimePeriod.valueOf(savedPeriodName)
        } catch (e: Exception) {
            TimePeriod.ONE_MONTH
        }
    }
    
    var selectedPeriod by remember { mutableStateOf(savedPeriod) }
    
    // Save time period whenever it changes
    LaunchedEffect(selectedPeriod) {
        settingsManager.saveSelectedGraphTimePeriod(selectedPeriod.name)
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with title
        Text(
            text = context.getString(R.string.stats_and_graphs),
            style = if (isPhone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = if (isPhone) 4.dp else 8.dp)
        )

        // Time Period Selector
        TimePeriodSelector(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = { selectedPeriod = it },
            isPhone = isPhone
        )

        // Active Volunteers Section
        SectionHeader(
            title = context.getString(R.string.active_volunteers),
            description = context.getString(R.string.active_volunteers_description),
            isPhone = isPhone
        )
        ActiveVolunteersGraph(
            volunteers = volunteers,
            jobs = jobs,
            timePeriod = selectedPeriod,
            isPhone = isPhone
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Shift Statistics Section
        SectionHeader(
            title = context.getString(R.string.shift_statistics),
            description = context.getString(R.string.shift_statistics_description),
            isPhone = isPhone
        )
        ShiftStatisticsGraph(
            jobs = jobs,
            venues = venues,
            timePeriod = selectedPeriod,
            isPhone = isPhone
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Guest List Statistics Section
        SectionHeader(
            title = context.getString(R.string.guest_list_statistics),
            description = context.getString(R.string.guest_list_statistics_description),
            isPhone = isPhone
        )
        GuestListStatisticsGraph(
            volunteers = volunteers,
            guests = guests,
            jobs = jobs,
            jobTypeConfigs = jobTypeConfigs,
            timePeriod = selectedPeriod,
            isPhone = isPhone
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Gender Parity Section
        SectionHeader(
            title = context.getString(R.string.gender_parity),
            description = context.getString(R.string.gender_parity_description),
            isPhone = isPhone
        )
        GenderParityGraph(
            volunteers = volunteers,
            isPhone = isPhone
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Age Distribution Section
        SectionHeader(
            title = context.getString(R.string.age_distribution),
            description = context.getString(R.string.age_distribution_description),
            isPhone = isPhone
        )
        AgeDistributionGraph(
            volunteers = volunteers,
            isPhone = isPhone
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Free Drinks Statistics Section
        SectionHeader(
            title = context.getString(R.string.free_drinks_statistics),
            description = context.getString(R.string.free_drinks_statistics_description),
            isPhone = isPhone
        )
        FreeDrinksGraph(
            volunteers = volunteers,
            jobs = jobs,
            jobTypeConfigs = jobTypeConfigs,
            timePeriod = selectedPeriod,
            isPhone = isPhone
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    description: String,
    isPhone: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isPhone) 4.dp else 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = if (isPhone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimePeriodSelector(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit,
    isPhone: Boolean = true
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = if (isPhone) 4.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TimePeriod.values().forEach { period ->
                Surface(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .wrapContentWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedPeriod == period)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    onClick = { onPeriodSelected(period) }
                ) {
                    Text(
                        text = period.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selectedPeriod == period) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selectedPeriod == period)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveVolunteersGraph(
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    timePeriod: TimePeriod,
    isPhone: Boolean = true
) {
    val context = LocalContext.current
    val dataPoints by produceState(
        initialValue = emptyList<DataPoint>(),
        volunteers,
        jobs,
        timePeriod
    ) {
        value = calculateActiveVolunteersData(volunteers, jobs, timePeriod)
    }

    if (dataPoints.isEmpty()) return

    var showExportDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportType by remember { mutableStateOf<ExportType?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportType?>(null) }

    GraphCard(
        title = context.getString(R.string.active_volunteers),
        icon = Icons.Default.Group,
        dataPoints = dataPoints,
        timePeriod = timePeriod,
        isPhone = isPhone,
        valueFormatter = { it.toInt().toString() },
        yAxisLabel = context.getString(R.string.count),
        description = context.getString(R.string.active_volunteers_graph_description),
        onLongPress = { showExportDialog = true }
    )

    // Export options dialog
    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onExportXLSX = {
                showExportDialog = false
                pendingExportType = ExportType.XLSX
                exportType = ExportType.XLSX
                isExporting = true
            },
            onExportJPG = {
                showExportDialog = false
                pendingExportType = ExportType.JPG
                exportType = ExportType.JPG
                isExporting = true
            }
        )
    }

    // Handle export in LaunchedEffect
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    LaunchedEffect(isExporting, pendingExportType) {
        if (isExporting && pendingExportType != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val trendPoints = calculateTrendLine(dataPoints)
                    val graphBitmap = GraphExportUtils.renderGraphAsBitmap(
                        dataPoints = dataPoints,
                        trendPoints = trendPoints,
                        title = context.getString(R.string.active_volunteers),
                        timePeriod = timePeriod,
                        density = density
                    )
                    
                    val file = if (pendingExportType == ExportType.XLSX) {
                        GraphExportUtils.exportToXLSX(
                            context = context,
                            title = context.getString(R.string.active_volunteers),
                            dataPoints = dataPoints,
                            trendPoints = trendPoints,
                            timePeriod = timePeriod,
                            graphBitmap = graphBitmap
                        )
                    } else {
                        GraphExportUtils.exportToJPG(context, graphBitmap, context.getString(R.string.active_volunteers))
                    }
                    
                    withContext(Dispatchers.Main) {
                        exportedFile = file
                        isExporting = false
                        pendingExportType = null
                        showPreviewDialog = true
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        pendingExportType = null
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Preview and share dialog
    if (showPreviewDialog && exportedFile != null && exportType != null) {
        PreviewDialog(
            file = exportedFile!!,
            exportType = exportType!!,
            onDismiss = {
                showPreviewDialog = false
                exportedFile = null
                exportType = null
            },
            onShare = {
                shareFile(context, exportedFile!!, exportType!!, context.getString(R.string.active_volunteers))
            }
        )
    }

    // Loading indicator
    if (isExporting) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.exporting),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private enum class ExportType {
    XLSX, JPG
}

/**
 * Helper composable that wraps GraphCard with export functionality
 */
@Composable
private fun GraphCardWithExport(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    dataPoints: List<DataPoint>,
    timePeriod: TimePeriod,
    isPhone: Boolean = true,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    yAxisLabel: String = "",
    description: String? = null
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportType by remember { mutableStateOf<ExportType?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportType?>(null) }

    GraphCard(
        title = title,
        icon = icon,
        dataPoints = dataPoints,
        timePeriod = timePeriod,
        isPhone = isPhone,
        valueFormatter = valueFormatter,
        yAxisLabel = yAxisLabel,
        description = description,
        onLongPress = { showExportDialog = true }
    )

    // Export options dialog
    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onExportXLSX = {
                showExportDialog = false
                pendingExportType = ExportType.XLSX
                exportType = ExportType.XLSX
                isExporting = true
            },
            onExportJPG = {
                showExportDialog = false
                pendingExportType = ExportType.JPG
                exportType = ExportType.JPG
                isExporting = true
            }
        )
    }

    // Handle export in LaunchedEffect
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    LaunchedEffect(isExporting, pendingExportType) {
        if (isExporting && pendingExportType != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val trendPoints = calculateTrendLine(dataPoints)
                    val graphBitmap = GraphExportUtils.renderGraphAsBitmap(
                        dataPoints = dataPoints,
                        trendPoints = trendPoints,
                        title = title,
                        timePeriod = timePeriod,
                        density = density
                    )
                    
                    val file = if (pendingExportType == ExportType.XLSX) {
                        GraphExportUtils.exportToXLSX(
                            context = context,
                            title = title,
                            dataPoints = dataPoints,
                            trendPoints = trendPoints,
                            timePeriod = timePeriod,
                            graphBitmap = graphBitmap
                        )
                    } else {
                        GraphExportUtils.exportToJPG(context, graphBitmap, title)
                    }
                    
                    withContext(Dispatchers.Main) {
                        exportedFile = file
                        isExporting = false
                        pendingExportType = null
                        showPreviewDialog = true
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        pendingExportType = null
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Preview and share dialog
    if (showPreviewDialog && exportedFile != null && exportType != null) {
        PreviewDialog(
            file = exportedFile!!,
            exportType = exportType!!,
            onDismiss = {
                showPreviewDialog = false
                exportedFile = null
                exportType = null
            },
            onShare = {
                shareFile(context, exportedFile!!, exportType!!, title)
            }
        )
    }

    // Loading indicator
    if (isExporting) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.exporting),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun FreeDrinksGraph(
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    timePeriod: TimePeriod,
    isPhone: Boolean = true
) {
    val context = LocalContext.current
    val settingsManager = remember { com.eventmanager.app.data.sync.SettingsManager(context) }
    val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
    val dataPoints by produceState(
        initialValue = emptyList<DataPoint>(),
        volunteers,
        jobs,
        jobTypeConfigs,
        timePeriod,
        offsetHours
    ) {
        value = calculateFreeDrinksData(volunteers, jobs, jobTypeConfigs, timePeriod, offsetHours)
    }

    if (dataPoints.isEmpty()) return

    GraphCardWithExport(
        title = context.getString(R.string.free_drinks_statistics),
        icon = Icons.Default.LocalDrink,
        dataPoints = dataPoints,
        timePeriod = timePeriod,
        isPhone = isPhone,
        valueFormatter = { it.toInt().toString() },
        yAxisLabel = context.getString(R.string.free_drinks),
        description = context.getString(R.string.free_drinks_statistics_description)
    )
}

@Composable
private fun ShiftStatisticsGraph(
    jobs: List<Job>,
    venues: List<VenueEntity>,
    timePeriod: TimePeriod,
    isPhone: Boolean = true
) {
    val context = LocalContext.current
    
    // Memoize active venues to avoid filtering on every recomposition
    val activeVenues = remember(venues) {
        venues.filter { it.isActive }
    }
    
    // Create dynamic data for each venue on background thread
    val allVenueData by produceState(
        initialValue = emptyList<Pair<String, List<DataPoint>>>(),
        jobs,
        activeVenues,
        timePeriod
    ) {
        value = withContext(Dispatchers.Default) {
            activeVenues.map { venue ->
                Pair(
                    venue.name,
                    calculateVenueShiftData(jobs, venue.name, timePeriod)
                )
            }
        }
    }
    
    val totalData by produceState(
        initialValue = emptyList<DataPoint>(),
        jobs,
        timePeriod
    ) {
        value = withContext(Dispatchers.Default) {
            calculateTotalShiftData(jobs, timePeriod)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Show graphs for each active venue
        allVenueData.forEach { (venueName, dataPoints) ->
            if (dataPoints.isNotEmpty()) {
                GraphCardWithExport(
                    title = venueName,
                    icon = Icons.Default.Build,
                    dataPoints = dataPoints,
                    timePeriod = timePeriod,
                    isPhone = isPhone,
                    valueFormatter = { it.toInt().toString() },
                    yAxisLabel = context.getString(R.string.count),
                    description = context.getString(R.string.venue_shifts_description, venueName)
                )
            }
        }

        // Total graph - always show if there's data
        if (totalData.isNotEmpty()) {
            GraphCardWithExport(
                title = context.getString(R.string.total),
                icon = Icons.Default.BarChart,
                dataPoints = totalData,
                timePeriod = timePeriod,
                isPhone = isPhone,
                valueFormatter = { it.toInt().toString() },
                yAxisLabel = context.getString(R.string.count),
                description = context.getString(R.string.total_shifts_description)
            )
        }
    }
}

@Composable
private fun GuestListStatisticsGraph(
    volunteers: List<Volunteer>,
    guests: List<Guest>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    timePeriod: TimePeriod,
    isPhone: Boolean = true
) {
    val context = LocalContext.current
    val settingsManager = remember { com.eventmanager.app.data.sync.SettingsManager(context) }
    val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
    
    // Calculate volunteer guest list data (historical) - run on background thread
    val volunteerGuestData by produceState(
        initialValue = emptyList<DataPoint>(),
        volunteers,
        jobs,
        jobTypeConfigs,
        timePeriod,
        offsetHours
    ) {
        value = withContext(Dispatchers.Default) {
            calculateVolunteerGuestListData(volunteers, jobs, jobTypeConfigs, timePeriod, offsetHours)
        }
    }
    
    // Calculate volunteer invites data (historical) - run on background thread
    val volunteerInvitesData by produceState(
        initialValue = emptyList<DataPoint>(),
        volunteers,
        jobs,
        jobTypeConfigs,
        timePeriod,
        offsetHours
    ) {
        value = withContext(Dispatchers.Default) {
            calculateVolunteerInvitesData(volunteers, jobs, jobTypeConfigs, timePeriod, offsetHours)
        }
    }
    
    // Calculate total data (volunteers + invites) - lightweight, can run on main thread
    val totalGuestData by produceState(
        initialValue = emptyList<DataPoint>(),
        volunteerGuestData,
        volunteerInvitesData
    ) {
        value = calculateTotalGuestListData(volunteerGuestData, volunteerInvitesData)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Show volunteer guest list graph
        if (volunteerGuestData.isNotEmpty()) {
            GraphCardWithExport(
                title = context.getString(R.string.volunteers),
                icon = Icons.Default.People,
                dataPoints = volunteerGuestData,
                timePeriod = timePeriod,
                isPhone = isPhone,
                valueFormatter = { it.toInt().toString() },
                yAxisLabel = context.getString(R.string.count),
                description = context.getString(R.string.volunteer_guests_description)
            )
        }

        // Show volunteer invites graph
        if (volunteerInvitesData.isNotEmpty()) {
            GraphCardWithExport(
                title = context.getString(R.string.invitations),
                icon = Icons.Default.Star,
                dataPoints = volunteerInvitesData,
                timePeriod = timePeriod,
                isPhone = isPhone,
                valueFormatter = { it.toInt().toString() },
                yAxisLabel = context.getString(R.string.count),
                description = context.getString(R.string.volunteer_invites_description)
            )
        }

        // Show total graph
        if (totalGuestData.isNotEmpty()) {
            GraphCardWithExport(
                title = context.getString(R.string.total),
                icon = Icons.Default.BarChart,
                dataPoints = totalGuestData,
                timePeriod = timePeriod,
                isPhone = isPhone,
                valueFormatter = { it.toInt().toString() },
                yAxisLabel = context.getString(R.string.count),
                description = context.getString(R.string.total_guests_description)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GenderParityGraph(
    volunteers: List<Volunteer>,
    isPhone: Boolean = true
) {
    val context = LocalContext.current
    
    // Filter to only active volunteers
    val activeVolunteers = volunteers.filter { it.isActive }
    
    // Calculate gender distribution
    val genderData = remember(activeVolunteers) {
        calculateGenderDistribution(activeVolunteers)
    }
    
    if (genderData.totalCount == 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isPhone) 16.dp else 20.dp)
                    .height(if (isPhone) 200.dp else 240.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.no_gender_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
    // Export state
    var showExportDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportType by remember { mutableStateOf<ExportType?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportType?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .combinedClickable(
                onClick = { /* Regular click does nothing */ },
                onLongClick = { showExportDialog = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isPhone) 36.dp else 40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                
                Text(
                    text = context.getString(R.string.gender_distribution),
                    style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Pie chart and legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pie chart
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .padding(8.dp)
                ) {
                    PieChart(
                        data = genderData.segments,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Legend
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genderData.segments.forEach { segment ->
                        val genderLabel = when (segment.gender) {
                            Gender.FEMALE -> context.getString(R.string.gender_female)
                            Gender.MALE -> context.getString(R.string.gender_male)
                            Gender.NON_BINARY -> context.getString(R.string.gender_non_binary)
                            Gender.OTHER -> context.getString(R.string.gender_other)
                            Gender.PREFER_NOT_TO_DISCLOSE -> context.getString(R.string.gender_prefer_not_to_disclose)
                            null -> context.getString(R.string.unspecified)
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(segment.color, shape = RoundedCornerShape(4.dp))
                            )
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = genderLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${segment.count} (${String.format("%.1f", segment.percentage)}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Export options dialog
    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onExportXLSX = {
                showExportDialog = false
                pendingExportType = ExportType.XLSX
                exportType = ExportType.XLSX
                isExporting = true
            },
            onExportJPG = {
                showExportDialog = false
                pendingExportType = ExportType.JPG
                exportType = ExportType.JPG
                isExporting = true
            }
        )
    }

    // Handle export in LaunchedEffect
    val scope = rememberCoroutineScope()
    LaunchedEffect(isExporting, pendingExportType) {
        if (isExporting && pendingExportType != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (pendingExportType == ExportType.XLSX) {
                        // Export gender distribution as table
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val fileName = "gender_distribution_${timestamp}.xlsx"
                        val file = File(context.cacheDir, fileName)
                    
                    val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
                    val sheet = workbook.createSheet("Gender Distribution")
                    
                    // Title
                    val titleRow = sheet.createRow(0)
                    val titleCell = titleRow.createCell(0)
                    titleCell.setCellValue(context.getString(R.string.gender_distribution))
                    val titleStyle = workbook.createCellStyle()
                    val titleFont = workbook.createFont()
                    titleFont.bold = true
                    titleFont.fontHeightInPoints = 16
                    titleStyle.setFont(titleFont)
                    titleCell.cellStyle = titleStyle
                    
                    // Headers
                    val headerRow = sheet.createRow(2)
                    headerRow.createCell(0).setCellValue("Gender")
                    headerRow.createCell(1).setCellValue("Count")
                    headerRow.createCell(2).setCellValue("Percentage")
                    
                    // Data rows
                    genderData.segments.forEachIndexed { index, segment ->
                        val row = sheet.createRow(3 + index)
                        val genderLabel = when (segment.gender) {
                            Gender.FEMALE -> context.getString(R.string.gender_female)
                            Gender.MALE -> context.getString(R.string.gender_male)
                            Gender.NON_BINARY -> context.getString(R.string.gender_non_binary)
                            Gender.OTHER -> context.getString(R.string.gender_other)
                            Gender.PREFER_NOT_TO_DISCLOSE -> context.getString(R.string.gender_prefer_not_to_disclose)
                            null -> context.getString(R.string.unspecified)
                        }
                        row.createCell(0).setCellValue(genderLabel)
                        row.createCell(1).setCellValue(segment.count.toDouble())
                        row.createCell(2).setCellValue("${String.format("%.1f", segment.percentage)}%")
                    }
                    
                        // Auto-size columns
                        sheet.setColumnWidth(0, 5000)
                        sheet.setColumnWidth(1, 3000)
                        sheet.setColumnWidth(2, 3000)
                        
                        FileOutputStream(file).use { outputStream ->
                            workbook.write(outputStream)
                        }
                        workbook.close()
                        
                        withContext(Dispatchers.Main) {
                            exportedFile = file
                            isExporting = false
                            pendingExportType = null
                            showPreviewDialog = true
                        }
                    } else {
                        // Export as JPG
                        val segments = genderData.segments.map { segment ->
                            val genderLabel = when (segment.gender) {
                                Gender.FEMALE -> context.getString(R.string.gender_female)
                                Gender.MALE -> context.getString(R.string.gender_male)
                                Gender.NON_BINARY -> context.getString(R.string.gender_non_binary)
                                Gender.OTHER -> context.getString(R.string.gender_other)
                                Gender.PREFER_NOT_TO_DISCLOSE -> context.getString(R.string.gender_prefer_not_to_disclose)
                                null -> context.getString(R.string.unspecified)
                            }
                            Pair(genderLabel, Pair(segment.percentage, segment.color.toArgb()))
                        }
                        
                        val graphBitmap = GraphExportUtils.renderPieChartAsBitmap(
                            segments = segments,
                            title = context.getString(R.string.gender_distribution)
                        )
                        
                        val file = GraphExportUtils.exportToJPG(
                            context = context,
                            bitmap = graphBitmap,
                            title = context.getString(R.string.gender_distribution)
                        )
                        
                        withContext(Dispatchers.Main) {
                            exportedFile = file
                            isExporting = false
                            pendingExportType = null
                            showPreviewDialog = true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        pendingExportType = null
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Preview and share dialog
    if (showPreviewDialog && exportedFile != null && exportType != null) {
        PreviewDialog(
            file = exportedFile!!,
            exportType = exportType!!,
            onDismiss = {
                showPreviewDialog = false
                exportedFile = null
                exportType = null
            },
            onShare = {
                shareFile(context, exportedFile!!, exportType!!, context.getString(R.string.gender_distribution))
            }
        )
    }

    // Loading indicator
    if (isExporting) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.exporting),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

data class GenderSegment(
    val gender: Gender?,
    val count: Int,
    val percentage: Float,
    val color: Color,
    val label: String
)

data class GenderDistribution(
    val segments: List<GenderSegment>,
    val totalCount: Int
)

private fun calculateGenderDistribution(volunteers: List<Volunteer>): GenderDistribution {
    val totalCount = volunteers.size
    if (totalCount == 0) {
        return GenderDistribution(emptyList(), 0)
    }
    
    // Count by gender
    val genderCounts = mutableMapOf<Gender?, Int>()
    volunteers.forEach { volunteer ->
        genderCounts[volunteer.gender] = (genderCounts[volunteer.gender] ?: 0) + 1
    }
    
    // Define colors for each gender
    val genderColors = mapOf(
        Gender.FEMALE to Color(0xFFFF6B9D), // Pink
        Gender.MALE to Color(0xFF4ECDC4), // Teal
        Gender.NON_BINARY to Color(0xFFFFE66D), // Yellow
        Gender.OTHER to Color(0xFF95E1D3), // Light green
        Gender.PREFER_NOT_TO_DISCLOSE to Color(0xFFC7CEEA), // Light purple
        null to Color(0xFFB8B8B8) // Gray for unspecified
    )
    
    val segments = genderCounts.map { (gender, count) ->
        val percentage = (count.toFloat() / totalCount) * 100f
        GenderSegment(
            gender = gender,
            count = count,
            percentage = percentage,
            color = genderColors[gender] ?: Color.Gray,
            label = "" // Will be set in composable with context
        )
    }.sortedByDescending { it.count }
    
    return GenderDistribution(segments, totalCount)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgeDistributionGraph(
    volunteers: List<Volunteer>,
    isPhone: Boolean = true
) {
    val context = LocalContext.current
    
    // Filter to only active volunteers
    val activeVolunteers = volunteers.filter { it.isActive }
    
    // Calculate age distribution
    val ageData = remember(activeVolunteers) {
        calculateAgeDistribution(activeVolunteers)
    }
    
    if (ageData.totalCount == 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isPhone) 16.dp else 20.dp)
                    .height(if (isPhone) 200.dp else 240.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.no_age_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
    // Export state
    var showExportDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportType by remember { mutableStateOf<ExportType?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportType?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .combinedClickable(
                onClick = { /* Regular click does nothing */ },
                onLongClick = { showExportDialog = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isPhone) 36.dp else 40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Text(
                    text = context.getString(R.string.age_distribution_title),
                    style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Pie chart and legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pie chart
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .padding(8.dp)
                ) {
                    AgePieChart(
                        data = ageData.segments,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Legend
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ageData.segments.forEach { segment ->
                        val ageLabel = when (segment.ageRange) {
                            AgeRange.UNDER_18 -> context.getString(R.string.age_under_18)
                            AgeRange.AGE_18_20 -> context.getString(R.string.age_18_20)
                            AgeRange.AGE_21_23 -> context.getString(R.string.age_21_23)
                            AgeRange.AGE_24_26 -> context.getString(R.string.age_24_26)
                            AgeRange.AGE_27_30 -> context.getString(R.string.age_27_30)
                            AgeRange.OVER_31 -> context.getString(R.string.age_over_31)
                            AgeRange.UNKNOWN -> context.getString(R.string.age_unknown)
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(segment.color, shape = RoundedCornerShape(4.dp))
                            )
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = ageLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${segment.count} (${String.format("%.1f", segment.percentage)}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Export options dialog
    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onExportXLSX = {
                showExportDialog = false
                pendingExportType = ExportType.XLSX
                exportType = ExportType.XLSX
                isExporting = true
            },
            onExportJPG = {
                showExportDialog = false
                pendingExportType = ExportType.JPG
                exportType = ExportType.JPG
                isExporting = true
            }
        )
    }

    // Handle export in LaunchedEffect
    val scope = rememberCoroutineScope()
    LaunchedEffect(isExporting, pendingExportType) {
        if (isExporting && pendingExportType != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (pendingExportType == ExportType.XLSX) {
                        // Export age distribution as table
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val fileName = "age_distribution_${timestamp}.xlsx"
                        val file = File(context.cacheDir, fileName)
                    
                    val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
                    val sheet = workbook.createSheet("Age Distribution")
                    
                    // Title
                    val titleRow = sheet.createRow(0)
                    val titleCell = titleRow.createCell(0)
                    titleCell.setCellValue(context.getString(R.string.age_distribution_title))
                    val titleStyle = workbook.createCellStyle()
                    val titleFont = workbook.createFont()
                    titleFont.bold = true
                    titleFont.fontHeightInPoints = 16
                    titleStyle.setFont(titleFont)
                    titleCell.cellStyle = titleStyle
                    
                    // Headers
                    val headerRow = sheet.createRow(2)
                    headerRow.createCell(0).setCellValue("Age Range")
                    headerRow.createCell(1).setCellValue("Count")
                    headerRow.createCell(2).setCellValue("Percentage")
                    
                    // Data rows
                    ageData.segments.forEachIndexed { index, segment ->
                        val row = sheet.createRow(3 + index)
                        val ageLabel = when (segment.ageRange) {
                            AgeRange.UNDER_18 -> context.getString(R.string.age_under_18)
                            AgeRange.AGE_18_20 -> context.getString(R.string.age_18_20)
                            AgeRange.AGE_21_23 -> context.getString(R.string.age_21_23)
                            AgeRange.AGE_24_26 -> context.getString(R.string.age_24_26)
                            AgeRange.AGE_27_30 -> context.getString(R.string.age_27_30)
                            AgeRange.OVER_31 -> context.getString(R.string.age_over_31)
                            AgeRange.UNKNOWN -> context.getString(R.string.age_unknown)
                        }
                        row.createCell(0).setCellValue(ageLabel)
                        row.createCell(1).setCellValue(segment.count.toDouble())
                        row.createCell(2).setCellValue("${String.format("%.1f", segment.percentage)}%")
                    }
                    
                        // Auto-size columns
                        sheet.setColumnWidth(0, 5000)
                        sheet.setColumnWidth(1, 3000)
                        sheet.setColumnWidth(2, 3000)
                        
                        FileOutputStream(file).use { outputStream ->
                            workbook.write(outputStream)
                        }
                        workbook.close()
                        
                        withContext(Dispatchers.Main) {
                            exportedFile = file
                            isExporting = false
                            pendingExportType = null
                            showPreviewDialog = true
                        }
                    } else {
                        // Export as JPG
                        val segments = ageData.segments.map { segment ->
                            val ageLabel = when (segment.ageRange) {
                                AgeRange.UNDER_18 -> context.getString(R.string.age_under_18)
                                AgeRange.AGE_18_20 -> context.getString(R.string.age_18_20)
                                AgeRange.AGE_21_23 -> context.getString(R.string.age_21_23)
                                AgeRange.AGE_24_26 -> context.getString(R.string.age_24_26)
                                AgeRange.AGE_27_30 -> context.getString(R.string.age_27_30)
                                AgeRange.OVER_31 -> context.getString(R.string.age_over_31)
                                AgeRange.UNKNOWN -> context.getString(R.string.age_unknown)
                            }
                            Pair(ageLabel, Pair(segment.percentage, segment.color.toArgb()))
                        }
                        
                        val graphBitmap = GraphExportUtils.renderPieChartAsBitmap(
                            segments = segments,
                            title = context.getString(R.string.age_distribution_title)
                        )
                        
                        val file = GraphExportUtils.exportToJPG(
                            context = context,
                            bitmap = graphBitmap,
                            title = context.getString(R.string.age_distribution_title)
                        )
                        
                        withContext(Dispatchers.Main) {
                            exportedFile = file
                            isExporting = false
                            pendingExportType = null
                            showPreviewDialog = true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        pendingExportType = null
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Preview and share dialog
    if (showPreviewDialog && exportedFile != null && exportType != null) {
        PreviewDialog(
            file = exportedFile!!,
            exportType = exportType!!,
            onDismiss = {
                showPreviewDialog = false
                exportedFile = null
                exportType = null
            },
            onShare = {
                shareFile(context, exportedFile!!, exportType!!, context.getString(R.string.age_distribution_title))
            }
        )
    }

    // Loading indicator
    if (isExporting) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.exporting),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

enum class AgeRange {
    UNDER_18,      // < 18
    AGE_18_20,     // 18-20
    AGE_21_23,     // 21-23
    AGE_24_26,     // 24-26
    AGE_27_30,     // 27-30
    OVER_31,       // > 31
    UNKNOWN        // No date of birth or invalid
}

data class AgeSegment(
    val ageRange: AgeRange,
    val count: Int,
    val percentage: Float,
    val color: Color,
    val label: String
)

data class AgeDistribution(
    val segments: List<AgeSegment>,
    val totalCount: Int
)

private fun calculateAge(dateOfBirth: String): Int? {
    if (dateOfBirth.isEmpty()) return null
    
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val birthDate = format.parse(dateOfBirth) ?: return null
        val today = Calendar.getInstance()
        val birthCalendar = Calendar.getInstance().apply { time = birthDate }
        
        var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
        
        // Check if birthday hasn't occurred this year
        if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        
        age
    } catch (e: Exception) {
        null
    }
}

private fun getAgeRange(age: Int?): AgeRange {
    if (age == null) return AgeRange.UNKNOWN
    
    return when {
        age < 18 -> AgeRange.UNDER_18
        age in 18..20 -> AgeRange.AGE_18_20
        age in 21..23 -> AgeRange.AGE_21_23
        age in 24..26 -> AgeRange.AGE_24_26
        age in 27..30 -> AgeRange.AGE_27_30
        age >= 31 -> AgeRange.OVER_31
        else -> AgeRange.UNKNOWN
    }
}

private fun calculateAgeDistribution(volunteers: List<Volunteer>): AgeDistribution {
    val totalCount = volunteers.size
    if (totalCount == 0) {
        return AgeDistribution(emptyList(), 0)
    }
    
    // Count by age range
    val ageRangeCounts = mutableMapOf<AgeRange, Int>()
    volunteers.forEach { volunteer ->
        val age = calculateAge(volunteer.dateOfBirth)
        val ageRange = getAgeRange(age)
        ageRangeCounts[ageRange] = (ageRangeCounts[ageRange] ?: 0) + 1
    }
    
    // Define colors for each age range
    val ageColors = mapOf(
        AgeRange.UNDER_18 to Color(0xFFFF6B9D), // Pink
        AgeRange.AGE_18_20 to Color(0xFF4ECDC4), // Teal
        AgeRange.AGE_21_23 to Color(0xFFFFE66D), // Yellow
        AgeRange.AGE_24_26 to Color(0xFF95E1D3), // Light green
        AgeRange.AGE_27_30 to Color(0xFFC7CEEA), // Light purple
        AgeRange.OVER_31 to Color(0xFFA8E6CF), // Mint green
        AgeRange.UNKNOWN to Color(0xFFB8B8B8) // Gray
    )
    
    val segments = ageRangeCounts.map { (ageRange, count) ->
        val percentage = (count.toFloat() / totalCount) * 100f
        AgeSegment(
            ageRange = ageRange,
            count = count,
            percentage = percentage,
            color = ageColors[ageRange] ?: Color.Gray,
            label = "" // Will be set in composable with context
        )
    }.sortedByDescending { it.count }
    
    return AgeDistribution(segments, totalCount)
}

@Composable
private fun AgePieChart(
    data: List<AgeSegment>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = canvasSize / 2f * 0.85f // Leave some padding
        
        var startAngle = -90f // Start from top
        
        data.forEach { segment ->
            val sweepAngle = (segment.percentage / 100f) * 360f
            
            drawArc(
                color = segment.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            )
            
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun PieChart(
    data: List<GenderSegment>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = canvasSize / 2f * 0.85f // Leave some padding
        
        var startAngle = -90f // Start from top
        
        data.forEach { segment ->
            val sweepAngle = (segment.percentage / 100f) * 360f
            
            drawArc(
                color = segment.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            )
            
            startAngle += sweepAngle
        }
    }
}

/**
 * Multi-line graph showing multiple data series
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiLineGraph(
    label: String,
    seriesData: List<Triple<String, List<DataPoint>, Color>>,
    timePeriod: TimePeriod,
    isPhone: Boolean = true
) {
    val context = LocalContext.current
    if (seriesData.isEmpty() || seriesData.all { it.second.isEmpty() }) return

    val height = if (isPhone) 200.dp else 240.dp
    val allDataPoints = remember(seriesData) { 
        seriesData.flatMap { it.second } 
    }
    val maxValue = remember(allDataPoints) {
        (allDataPoints.maxOfOrNull { it.value } ?: 0f).let { if (it == 0f) 1f else it * 1.15f }
    }
    val minValue = 0f
    
    // Calculate trend lines for each series
    val seriesDataWithTrends = remember(seriesData) {
        seriesData.map { (name, dataPoints, color) ->
            Triple(name, dataPoints, calculateTrendLine(dataPoints)) to color
        }
    }

    var interactionState by remember(seriesData.size) { mutableStateOf(InteractionState()) }
    
    // Reset interaction state when data changes (use timestamps to detect actual data changes)
    LaunchedEffect(
        seriesData.size, 
        seriesData.firstOrNull()?.second?.size ?: 0,
        seriesData.firstOrNull()?.second?.firstOrNull()?.timestamp,
        seriesData.firstOrNull()?.second?.lastOrNull()?.timestamp
    ) {
        interactionState = InteractionState()
    }

    // Export state - export first series
    val firstSeriesData = seriesData.firstOrNull()?.second ?: emptyList()
    var showExportDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportType by remember { mutableStateOf<ExportType?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportType?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .combinedClickable(
                onClick = { /* Regular click does nothing */ },
                onLongClick = { showExportDialog = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        // Graph with Y-axis labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(32.dp)
                    .height(height)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = maxValue.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = ((maxValue + minValue) / 2f).toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = minValue.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Graph container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
                    .pointerInput(
                        seriesData.size, 
                        seriesData.firstOrNull()?.second?.size ?: 0,
                        seriesData.firstOrNull()?.second?.firstOrNull()?.timestamp,
                        seriesData.firstOrNull()?.second?.lastOrNull()?.timestamp
                    ) {
                        val currentDataPoints = seriesData.firstOrNull()?.second ?: emptyList()
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        val offset = event.changes.first().position
                                        val pointIndex = calculateNearestPointIndex(offset.x, size.width.toFloat(), currentDataPoints)
                                        if (pointIndex >= 0 && pointIndex < currentDataPoints.size) {
                                            interactionState = InteractionState(
                                                isPressed = true,
                                                xPosition = offset.x,
                                                hoveredPointIndex = pointIndex,
                                                hoveredValue = 0f,
                                                hoveredLabel = currentDataPoints[pointIndex].label
                                            )
                                        }
                                    }
                                    PointerEventType.Move -> {
                                        // Update tooltip as finger moves
                                        if (interactionState.isPressed) {
                                            val offset = event.changes.first().position
                                            val pointIndex = calculateNearestPointIndex(offset.x, size.width.toFloat(), currentDataPoints)
                                            if (pointIndex >= 0 && pointIndex < currentDataPoints.size) {
                                                interactionState = InteractionState(
                                                    isPressed = true,
                                                    xPosition = offset.x,
                                                    hoveredPointIndex = pointIndex,
                                                    hoveredValue = 0f,
                                                    hoveredLabel = currentDataPoints[pointIndex].label
                                                )
                                            }
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        interactionState = InteractionState(isPressed = false)
                                    }
                                    else -> {}
                                }
                                
                                // Consume the event to prevent propagation to parent swipe handlers
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            ) {
                if (seriesData.isNotEmpty() && seriesData[0].second.size >= 2) {
                    MultiLineGraphContent(
                        seriesDataWithTrends = seriesDataWithTrends,
                        maxValue = maxValue,
                        minValue = minValue,
                        interactionState = interactionState
                    )

                    // Show tooltip when pressed
                    if (interactionState.isPressed && interactionState.hoveredPointIndex >= 0) {
                        MultiLineGraphTooltip(
                            seriesData = seriesData,
                            pointIndex = interactionState.hoveredPointIndex,
                            label = interactionState.hoveredLabel,
                            xPosition = interactionState.xPosition
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.not_enough_data),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // X-axis labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = seriesData.firstOrNull()?.second?.firstOrNull()?.label ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Start,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = seriesData.firstOrNull()?.second?.lastOrNull()?.label ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.End,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
        }
    }

    // Export options dialog
    if (showExportDialog && firstSeriesData.isNotEmpty()) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onExportXLSX = {
                showExportDialog = false
                pendingExportType = ExportType.XLSX
                exportType = ExportType.XLSX
                isExporting = true
            },
            onExportJPG = {
                showExportDialog = false
                pendingExportType = ExportType.JPG
                exportType = ExportType.JPG
                isExporting = true
            }
        )
    }

    // Handle export in LaunchedEffect
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    LaunchedEffect(isExporting, pendingExportType) {
        if (isExporting && pendingExportType != null && firstSeriesData.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val trendPoints = calculateTrendLine(firstSeriesData)
                    val graphBitmap = GraphExportUtils.renderGraphAsBitmap(
                        dataPoints = firstSeriesData,
                        trendPoints = trendPoints,
                        title = label,
                        timePeriod = timePeriod,
                        density = density
                    )
                    
                    val file = if (pendingExportType == ExportType.XLSX) {
                        GraphExportUtils.exportToXLSX(
                            context = context,
                            title = label,
                            dataPoints = firstSeriesData,
                            trendPoints = trendPoints,
                            timePeriod = timePeriod,
                            graphBitmap = graphBitmap
                        )
                    } else {
                        GraphExportUtils.exportToJPG(context, graphBitmap, label)
                    }
                    
                    withContext(Dispatchers.Main) {
                        exportedFile = file
                        isExporting = false
                        pendingExportType = null
                        showPreviewDialog = true
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        pendingExportType = null
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Preview and share dialog
    if (showPreviewDialog && exportedFile != null && exportType != null) {
        PreviewDialog(
            file = exportedFile!!,
            exportType = exportType!!,
            onDismiss = {
                showPreviewDialog = false
                exportedFile = null
                exportType = null
            },
            onShare = {
                shareFile(context, exportedFile!!, exportType!!, label)
            }
        )
    }

    // Loading indicator
    if (isExporting) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.exporting),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Draws multiple line series on the same graph with trend lines
 */
@Composable
private fun MultiLineGraphContent(
    seriesDataWithTrends: List<Pair<Triple<String, List<DataPoint>, List<DataPoint>>, Color>>,
    maxValue: Float,
    minValue: Float,
    interactionState: InteractionState
) {
    val valueRange = maxValue - minValue
    // Compute colors first (this is Composable context)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
    // Cache only the PathEffect
    val dashPathEffect = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f) }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasHeight = size.height
        val canvasWidth = size.width
        
        if (seriesDataWithTrends.isEmpty()) return@Canvas
        // Draw grid lines
        drawLine(
            color = gridColor,
            start = Offset(0f, canvasHeight / 3),
            end = Offset(canvasWidth, canvasHeight / 3),
            strokeWidth = 0.5f
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, 2 * canvasHeight / 3),
            end = Offset(canvasWidth, 2 * canvasHeight / 3),
            strokeWidth = 0.5f
        )

        // Draw all series
        seriesDataWithTrends.forEach { (seriesTriple, color) ->
            val (name, dataPoints, trendPoints) = seriesTriple
            
            // Only draw if this series has at least 2 points
            if (dataPoints.size >= 2) {
                val seriesPointCount = dataPoints.size
                
                // Draw trend line first (behind) - dashed line
                val trendColor = color.copy(alpha = 0.5f)
                for (i in 0 until seriesPointCount - 1) {
                    val x1 = (i.toFloat() / (seriesPointCount - 1)) * canvasWidth
                    val y1 = canvasHeight - ((trendPoints[i].value - minValue) / valueRange) * canvasHeight
                    val x2 = ((i + 1).toFloat() / (seriesPointCount - 1)) * canvasWidth
                    val y2 = canvasHeight - ((trendPoints[i + 1].value - minValue) / valueRange) * canvasHeight
                    drawLine(
                        color = trendColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 1.5f,
                        pathEffect = dashPathEffect
                    )
                }
                
                // Draw exact value line
                for (i in 0 until seriesPointCount - 1) {
                    val x1 = (i.toFloat() / (seriesPointCount - 1)) * canvasWidth
                    val y1 = canvasHeight - ((dataPoints[i].value - minValue) / valueRange) * canvasHeight
                    val x2 = ((i + 1).toFloat() / (seriesPointCount - 1)) * canvasWidth
                    val y2 = canvasHeight - ((dataPoints[i + 1].value - minValue) / valueRange) * canvasHeight
                    drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 2.5f)
                }

                // Draw points
                for (i in dataPoints.indices) {
                    val x = (i.toFloat() / (seriesPointCount - 1)) * canvasWidth
                    val y = canvasHeight - ((dataPoints[i].value - minValue) / valueRange) * canvasHeight
                    drawCircle(color = color, radius = 3.5f, center = Offset(x, y))
                }
            }
        }

        // Draw crosshair if interacting
        if (interactionState.isPressed) {
            drawLine(
                color = seriesDataWithTrends.firstOrNull()?.second?.copy(alpha = 0.6f) ?: androidx.compose.ui.graphics.Color.Gray,
                start = Offset(interactionState.xPosition, 0f),
                end = Offset(interactionState.xPosition, canvasHeight),
                strokeWidth = 1.5f
            )
        }
    }
}

/**
 * Multi-line tooltip showing values for all series at the selected point
 */
@Composable
private fun MultiLineGraphTooltip(
    seriesData: List<Triple<String, List<DataPoint>, Color>>,
    pointIndex: Int,
    label: String,
    xPosition: Float
) {
    // Check if we have valid data to display
    val hasValidData = seriesData.any { it.second.indices.contains(pointIndex) }
    
    if (!hasValidData) return
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Show label if available
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 10.sp
                )
            }
            
            // Values for each series
            seriesData.forEach { (seriesName, dataPoints, color) ->
                if (pointIndex in dataPoints.indices) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color, shape = RoundedCornerShape(1.dp))
                        )
                        Text(
                            text = "${seriesName}: ${dataPoints[pointIndex].value.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculates the nearest point index to a given x position
 */
private fun calculateNearestPointIndex(xPosition: Float, width: Float, dataPoints: List<DataPoint>): Int {
    if (dataPoints.isEmpty()) return -1
    
    val xPercent = (xPosition / width).coerceIn(0f, 1f)
    val pointIndex = (xPercent * (dataPoints.size - 1).toFloat()).toInt()
        .coerceIn(0, dataPoints.size - 1)
    
    val nextIndex = pointIndex + 1
    return if (nextIndex < dataPoints.size) {
        val currentDistance = kotlin.math.abs(
            (pointIndex.toFloat() / (dataPoints.size - 1).toFloat()) - xPercent
        )
        val nextDistance = kotlin.math.abs(
            (nextIndex.toFloat() / (dataPoints.size - 1).toFloat()) - xPercent
        )
        if (nextDistance < currentDistance) nextIndex else pointIndex
    } else {
        pointIndex
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GraphCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    dataPoints: List<DataPoint>,
    timePeriod: TimePeriod,
    isPhone: Boolean = true,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    yAxisLabel: String = "",
    description: String? = null,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = { /* Regular click does nothing */ },
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isPhone) 36.dp else 40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(10.dp)
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
                    
                    Text(
                        text = title,
                        style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Description if provided
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = if (isPhone) 48.dp else 52.dp)
                    )
                }
            }

            // Graph area
            InteractiveLineGraph(
                label = title,
                dataPoints = dataPoints,
                timePeriod = timePeriod,
                isPhone = isPhone
            )
        }
    }
}

/**
 * Calculates an exponential moving average for trend analysis
 * Uses alpha=0.3 for smooth trend that shows general direction
 */
private fun calculateTrendLine(dataPoints: List<DataPoint>): List<DataPoint> {
    if (dataPoints.size < 2) return dataPoints
    
    val alpha = 0.3f // Smoothing factor (0.3 = 30% weight to current value, 70% to history)
    val trend = mutableListOf<DataPoint>()
    
    var ema = dataPoints[0].value
    trend.add(dataPoints[0])
    
    for (i in 1 until dataPoints.size) {
        ema = alpha * dataPoints[i].value + (1 - alpha) * ema
        trend.add(dataPoints[i].copy(value = ema))
    }
    
    return trend
}

/**
 * Interactive line graph with long-press tooltip support (like trading graphs)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InteractiveLineGraph(
    label: String,
    dataPoints: List<DataPoint>,
    timePeriod: TimePeriod,
    isPhone: Boolean = true
) {
    val context = LocalContext.current
    if (dataPoints.size < 2) return

    val height = if (isPhone) 180.dp else 220.dp
    val trendPoints = remember(dataPoints) { calculateTrendLine(dataPoints) }
    val maxValue = remember(dataPoints) {
        (dataPoints.maxOfOrNull { it.value } ?: 0f).let { if (it == 0f) 1f else it * 1.15f }
    }
    val minValue = 0f

    // Use a stable key based on data size to avoid excessive state resets during loading
    var interactionState by remember(dataPoints.size) { mutableStateOf(InteractionState()) }
    
    // Reset interaction state when data changes (use timestamps to detect actual data changes)
    LaunchedEffect(dataPoints.size, dataPoints.firstOrNull()?.timestamp, dataPoints.lastOrNull()?.timestamp) {
        interactionState = InteractionState()
    }

    // Export state
    var showExportDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportType by remember { mutableStateOf<ExportType?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportType?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .combinedClickable(
                onClick = { /* Regular click does nothing */ },
                onLongClick = { showExportDialog = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Graph with Y-axis labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(32.dp)
                    .height(height)
                    .padding(top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = maxValue.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = ((maxValue + minValue) / 2f).toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = minValue.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Graph container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
                    .pointerInput(dataPoints.size, dataPoints.firstOrNull()?.timestamp, dataPoints.lastOrNull()?.timestamp) {
                        val currentDataPoints = dataPoints
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        val offset = event.changes.first().position
                                        val pointIndex = calculateNearestPointIndex(offset.x, size.width.toFloat(), currentDataPoints)
                                        if (pointIndex >= 0 && pointIndex < currentDataPoints.size) {
                                            val dataPoint = currentDataPoints[pointIndex]
                                            interactionState = InteractionState(
                                                isPressed = true,
                                                xPosition = offset.x,
                                                hoveredPointIndex = pointIndex,
                                                hoveredValue = dataPoint.value,
                                                hoveredLabel = dataPoint.label
                                            )
                                        }
                                    }
                                    PointerEventType.Move -> {
                                        // Update tooltip as finger moves
                                        if (interactionState.isPressed) {
                                            val offset = event.changes.first().position
                                            val pointIndex = calculateNearestPointIndex(offset.x, size.width.toFloat(), currentDataPoints)
                                            if (pointIndex >= 0 && pointIndex < currentDataPoints.size) {
                                                val dataPoint = currentDataPoints[pointIndex]
                                                interactionState = InteractionState(
                                                    isPressed = true,
                                                    xPosition = offset.x,
                                                    hoveredPointIndex = pointIndex,
                                                    hoveredValue = dataPoint.value,
                                                    hoveredLabel = dataPoint.label
                                                )
                                            }
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        interactionState = InteractionState(isPressed = false)
                                    }
                                    else -> {}
                                }
                                
                                // Consume the event to prevent propagation to parent swipe handlers
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            ) {
                if (dataPoints.size >= 2) {
                    InteractiveLineGraphContent(
                        dataPoints = dataPoints,
                        trendPoints = trendPoints,
                        color = MaterialTheme.colorScheme.primary,
                        maxValue = maxValue,
                        minValue = minValue,
                        interactionState = interactionState
                    )

                    // Show tooltip when pressed
                    if (interactionState.isPressed && interactionState.hoveredPointIndex >= 0) {
                        GraphTooltip(
                            value = interactionState.hoveredValue,
                            label = interactionState.hoveredLabel,
                            valueFormatter = { it.toInt().toString() },
                            xPosition = interactionState.xPosition
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.not_enough_data),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // X-axis labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dataPoints.firstOrNull()?.label ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Start,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = dataPoints.lastOrNull()?.label ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.End,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
        }
    }

    // Export options dialog
    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onExportXLSX = {
                showExportDialog = false
                pendingExportType = ExportType.XLSX
                exportType = ExportType.XLSX
                isExporting = true
            },
            onExportJPG = {
                showExportDialog = false
                pendingExportType = ExportType.JPG
                exportType = ExportType.JPG
                isExporting = true
            }
        )
    }

    // Handle export in LaunchedEffect
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    LaunchedEffect(isExporting, pendingExportType) {
        if (isExporting && pendingExportType != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val graphBitmap = GraphExportUtils.renderGraphAsBitmap(
                        dataPoints = dataPoints,
                        trendPoints = trendPoints,
                        title = label,
                        timePeriod = timePeriod,
                        density = density
                    )
                    
                    val file = if (pendingExportType == ExportType.XLSX) {
                        GraphExportUtils.exportToXLSX(
                            context = context,
                            title = label,
                            dataPoints = dataPoints,
                            trendPoints = trendPoints,
                            timePeriod = timePeriod,
                            graphBitmap = graphBitmap
                        )
                    } else {
                        GraphExportUtils.exportToJPG(context, graphBitmap, label)
                    }
                    
                    withContext(Dispatchers.Main) {
                        exportedFile = file
                        isExporting = false
                        pendingExportType = null
                        showPreviewDialog = true
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        pendingExportType = null
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Preview and share dialog
    if (showPreviewDialog && exportedFile != null && exportType != null) {
        PreviewDialog(
            file = exportedFile!!,
            exportType = exportType!!,
            onDismiss = {
                showPreviewDialog = false
                exportedFile = null
                exportType = null
            },
            onShare = {
                shareFile(context, exportedFile!!, exportType!!, label)
            }
        )
    }

    // Loading indicator
    if (isExporting) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.exporting),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Tooltip showing exact value at interaction point
 */
@Composable
private fun GraphTooltip(
    value: Float,
    label: String,
    valueFormatter: (Float) -> String,
    xPosition: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                fontSize = 9.sp
            )
        }
    }
}

/**
 * Draws the interactive line graph with both absolute values and trend line
 */
@Composable
private fun InteractiveLineGraphContent(
    dataPoints: List<DataPoint>,
    trendPoints: List<DataPoint>,
    color: Color,
    maxValue: Float,
    minValue: Float,
    interactionState: InteractionState
) {
    val valueRange = maxValue - minValue
    // Compute colors first (this is Composable context)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
    // Cache only the PathEffect
    val trendColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
    val crosshairColor = color.copy(alpha = 0.6f)
    val dashPathEffect = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f) }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasHeight = size.height
        val canvasWidth = size.width
        val pointCount = dataPoints.size

        if (pointCount < 2) return@Canvas

        // Draw grid lines
        drawLine(
            color = gridColor,
            start = Offset(0f, canvasHeight / 3),
            end = Offset(canvasWidth, canvasHeight / 3),
            strokeWidth = 0.5f
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, 2 * canvasHeight / 3),
            end = Offset(canvasWidth, 2 * canvasHeight / 3),
            strokeWidth = 0.5f
        )

        // Draw trend line first (behind) - dashed line
        for (i in 0 until pointCount - 1) {
            val x1 = (i.toFloat() / (pointCount - 1)) * canvasWidth
            val y1 = canvasHeight - ((trendPoints[i].value - minValue) / valueRange) * canvasHeight
            val x2 = ((i + 1).toFloat() / (pointCount - 1)) * canvasWidth
            val y2 = canvasHeight - ((trendPoints[i + 1].value - minValue) / valueRange) * canvasHeight
            drawLine(
                color = trendColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2f,
                pathEffect = dashPathEffect
            )
        }

        // Draw absolute value line (main)
        for (i in 0 until pointCount - 1) {
            val x1 = (i.toFloat() / (pointCount - 1)) * canvasWidth
            val y1 = canvasHeight - ((dataPoints[i].value - minValue) / valueRange) * canvasHeight
            val x2 = ((i + 1).toFloat() / (pointCount - 1)) * canvasWidth
            val y2 = canvasHeight - ((dataPoints[i + 1].value - minValue) / valueRange) * canvasHeight
            drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 2.5f)
        }

        // Draw data points (only absolute values)
        for (i in dataPoints.indices) {
            val x = (i.toFloat() / (pointCount - 1)) * canvasWidth
            val y = canvasHeight - ((dataPoints[i].value - minValue) / valueRange) * canvasHeight
            drawCircle(color = color, radius = 3.5f, center = Offset(x, y))
        }

        // Draw crosshair if interacting
        if (interactionState.isPressed) {
            drawLine(
                color = crosshairColor,
                start = Offset(interactionState.xPosition, 0f),
                end = Offset(interactionState.xPosition, canvasHeight),
                strokeWidth = 1.5f
            )
        }
    }
}

// Data calculation functions
private fun getAggregationPeriodMs(timePeriod: TimePeriod): Long {
    return when (timePeriod) {
        TimePeriod.ONE_WEEK, TimePeriod.TWO_WEEKS, TimePeriod.ONE_MONTH -> 24 * 60 * 60 * 1000 // Daily
        TimePeriod.SIX_MONTHS, TimePeriod.ONE_YEAR, TimePeriod.MAX -> 7 * 24 * 60 * 60 * 1000 // Weekly
    }
}

private fun calculateActiveVolunteersData(
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    timePeriod: TimePeriod
): List<DataPoint> {
    val now = System.currentTimeMillis()
    val startTime = if (timePeriod == TimePeriod.MAX) {
        jobs.minOfOrNull { it.date } ?: (now - 365 * 24 * 60 * 60 * 1000)
    } else {
        now - timePeriod.days * 24 * 60 * 60 * 1000
    }

    val dateFormat = getDateFormat(timePeriod, startTime, now)
    val oneYearInMs = 365L * 24 * 60 * 60 * 1000
    val aggregationMs = getAggregationPeriodMs(timePeriod)
    
    // Generate data points using the aggregation period
    val dataPoints = mutableListOf<DataPoint>()
    var currentDate = startTime
    
    while (currentDate <= now) {
        // For this date, determine how many volunteers would be considered active
        // A volunteer is active if their most recent job (up to this date) is within 1 year of this date
        
        var activeCount = 0
        
        // Check each volunteer
        for (volunteer in volunteers) {
            // Find this volunteer's most recent job UP TO and INCLUDING this date
            val jobsUpToThisDate = jobs.filter { job ->
                job.volunteerId == volunteer.id && job.date <= currentDate
            }
            
            val mostRecentJobDate = jobsUpToThisDate.maxOfOrNull { it.date }
            
            if (mostRecentJobDate != null) {
                // Check if this job is within 1 year of the current date
                val oneYearBeforeThisDate = currentDate - oneYearInMs
                if (mostRecentJobDate >= oneYearBeforeThisDate) {
                    activeCount++
                }
            }
        }
        
        val label = dateFormat(currentDate)
        dataPoints.add(DataPoint(label, activeCount.toFloat(), currentDate))
        
        currentDate += aggregationMs
    }

    return dataPoints.ifEmpty { emptyList() }
}

private fun calculateFreeDrinksData(
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    timePeriod: TimePeriod,
    offsetHours: Int = 0
): List<DataPoint> {
    val now = System.currentTimeMillis()
    val startTime = if (timePeriod == TimePeriod.MAX) {
        jobs.minOfOrNull { it.date } ?: (now - 365 * 24 * 60 * 60 * 1000)
    } else {
        now - timePeriod.days * 24 * 60 * 60 * 1000
    }

    val dateFormat = getDateFormat(timePeriod, startTime, now)
    val aggregationMs = getAggregationPeriodMs(timePeriod)
    
    // Generate data points using the aggregation period
    val dataPoints = mutableListOf<DataPoint>()
    var currentDate = startTime
    
    while (currentDate <= now) {
        // For this date, calculate total free drinks available from all volunteers
        // Use the same logic as calculateTotalFreeDrinks but for each date in history
        var totalDrinks = 0
        
        // Check each volunteer
        for (volunteer in volunteers) {
            // Find this volunteer's jobs UP TO and INCLUDING this date
            val jobsUpToThisDate = jobs.filter { job ->
                job.volunteerId == volunteer.id && job.date <= currentDate
            }
            
            // Calculate benefit status at this point in time
            val benefitStatus = com.eventmanager.app.data.models.BenefitCalculator.calculateVolunteerBenefitStatus(
                volunteer = volunteer,
                jobs = jobsUpToThisDate,
                jobTypeConfigs = jobTypeConfigs,
                currentTime = currentDate,
                offsetHours = offsetHours
            )
            
            // Use the same logic as calculateTotalFreeDrinks: just check if benefits are active
            // The calculateVolunteerBenefitStatus already takes currentTime into account
            if (benefitStatus.benefits.isActive) {
                // Add their drink tokens
                totalDrinks += benefitStatus.benefits.drinkTokens
            }
        }
        
        val label = dateFormat(currentDate)
        dataPoints.add(DataPoint(label, totalDrinks.toFloat(), currentDate))
        
        currentDate += aggregationMs
    }

    return dataPoints.ifEmpty { emptyList() }
}

private fun calculateVenueShiftData(
    jobs: List<Job>,
    venueName: String,
    timePeriod: TimePeriod
): List<DataPoint> {
    val now = System.currentTimeMillis()
    val startTime = if (timePeriod == TimePeriod.MAX) {
        jobs.minOfOrNull { it.date } ?: (now - 365 * 24 * 60 * 60 * 1000)
    } else {
        now - timePeriod.days * 24 * 60 * 60 * 1000
    }

    val dateFormat = getDateFormat(timePeriod, startTime, now)
    val aggregationMs = getAggregationPeriodMs(timePeriod)
    
    // Generate data points using the aggregation period
    val dataPoints = mutableListOf<DataPoint>()
    var currentDate = startTime
    
    while (currentDate <= now) {
        // Count shifts for this venue in this aggregation period
        val periodEnd = currentDate + aggregationMs
        val shiftCount = jobs.count { job ->
            job.date >= currentDate && 
            job.date < periodEnd && 
            job.venueName.equals(venueName, ignoreCase = true)
        }
        
        val label = dateFormat(currentDate)
        dataPoints.add(DataPoint(label, shiftCount.toFloat(), currentDate))
        
        currentDate += aggregationMs
    }

    return dataPoints.ifEmpty { emptyList() }
}

private fun calculateTotalShiftData(
    jobs: List<Job>,
    timePeriod: TimePeriod
): List<DataPoint> {
    val now = System.currentTimeMillis()
    val startTime = if (timePeriod == TimePeriod.MAX) {
        jobs.minOfOrNull { it.date } ?: (now - 365 * 24 * 60 * 60 * 1000)
    } else {
        now - timePeriod.days * 24 * 60 * 60 * 1000
    }

    val dateFormat = getDateFormat(timePeriod, startTime, now)
    val aggregationMs = getAggregationPeriodMs(timePeriod)
    
    // Generate data points using the aggregation period
    val dataPoints = mutableListOf<DataPoint>()
    var currentDate = startTime
    
    while (currentDate <= now) {
        // Count all shifts in this aggregation period
        val periodEnd = currentDate + aggregationMs
        val shiftCount = jobs.count { job ->
            job.date >= currentDate && job.date < periodEnd
        }
        
        val label = dateFormat(currentDate)
        dataPoints.add(DataPoint(label, shiftCount.toFloat(), currentDate))
        
        currentDate += aggregationMs
    }

    return dataPoints.ifEmpty { emptyList() }
}

private fun calculateVolunteerGuestListData(
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    timePeriod: TimePeriod,
    offsetHours: Int = 0
): List<DataPoint> {
    val now = System.currentTimeMillis()
    val startTime = if (timePeriod == TimePeriod.MAX) {
        jobs.minOfOrNull { it.date } ?: (now - 365 * 24 * 60 * 60 * 1000)
    } else {
        now - timePeriod.days * 24 * 60 * 60 * 1000
    }

    val dateFormat = getDateFormat(timePeriod, startTime, now)
    val aggregationMs = getAggregationPeriodMs(timePeriod)
    
    // Generate data points using the aggregation period
    val dataPoints = mutableListOf<DataPoint>()
    var currentDate = startTime
    
    while (currentDate <= now) {
        // For this date, determine how many volunteers would have guest list access
        var volunteerCount = 0
        
        // Check each volunteer
        for (volunteer in volunteers) {
            // Find this volunteer's jobs UP TO and INCLUDING this date
            val jobsUpToThisDate = jobs.filter { job ->
                job.volunteerId == volunteer.id && job.date <= currentDate
            }
            
            // Calculate benefit status at this point in time
            val benefitStatus = com.eventmanager.app.data.models.BenefitCalculator.calculateVolunteerBenefitStatus(
                volunteer = volunteer,
                jobs = jobsUpToThisDate,
                jobTypeConfigs = jobTypeConfigs,
                currentTime = currentDate,
                offsetHours = offsetHours
            )
            
            // Check if volunteer has guest list access at this date
            if (benefitStatus.benefits.isActive && 
                benefitStatus.benefits.guestListAccess &&
                (benefitStatus.benefits.validUntil == null || currentDate < benefitStatus.benefits.validUntil)) {
                volunteerCount++
            }
        }
        
        val label = dateFormat(currentDate)
        dataPoints.add(DataPoint(label, volunteerCount.toFloat(), currentDate))
        
        currentDate += aggregationMs
    }

    return dataPoints.ifEmpty { emptyList() }
}

private fun calculateVolunteerInvitesData(
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    timePeriod: TimePeriod,
    offsetHours: Int = 0
): List<DataPoint> {
    val now = System.currentTimeMillis()
    val startTime = if (timePeriod == TimePeriod.MAX) {
        jobs.minOfOrNull { it.date } ?: (now - 365 * 24 * 60 * 60 * 1000)
    } else {
        now - timePeriod.days * 24 * 60 * 60 * 1000
    }

    val dateFormat = getDateFormat(timePeriod, startTime, now)
    val aggregationMs = getAggregationPeriodMs(timePeriod)
    
    // Generate data points using the aggregation period
    val dataPoints = mutableListOf<DataPoint>()
    var currentDate = startTime
    
    while (currentDate <= now) {
        // For this date, calculate total invites volunteers could give
        var totalInvites = 0
        
        // Check each volunteer
        for (volunteer in volunteers) {
            // Find this volunteer's jobs UP TO and INCLUDING this date
            val jobsUpToThisDate = jobs.filter { job ->
                job.volunteerId == volunteer.id && job.date <= currentDate
            }
            
            // Calculate benefit status at this point in time
            val benefitStatus = com.eventmanager.app.data.models.BenefitCalculator.calculateVolunteerBenefitStatus(
                volunteer = volunteer,
                jobs = jobsUpToThisDate,
                jobTypeConfigs = jobTypeConfigs,
                currentTime = currentDate,
                offsetHours = offsetHours
            )
            
            // Check if volunteer has guest list access at this date
            if (benefitStatus.benefits.isActive && 
                benefitStatus.benefits.guestListAccess &&
                (benefitStatus.benefits.validUntil == null || currentDate < benefitStatus.benefits.validUntil)) {
                // Add their invite count
                totalInvites += benefitStatus.benefits.inviteCount
            }
        }
        
        val label = dateFormat(currentDate)
        dataPoints.add(DataPoint(label, totalInvites.toFloat(), currentDate))
        
        currentDate += aggregationMs
    }

    return dataPoints.ifEmpty { emptyList() }
}

private fun calculateTotalGuestListData(
    volunteerGuestData: List<DataPoint>,
    volunteerInvitesData: List<DataPoint>
): List<DataPoint> {
    if (volunteerGuestData.isEmpty() && volunteerInvitesData.isEmpty()) {
        return emptyList()
    }
    
    // Create a map of timestamp to total value
    val totals = mutableMapOf<Long, Pair<String, Float>>() // timestamp -> (label, total)
    
    // Collect all unique timestamps
    val allTimestamps = mutableSetOf<Long>()
    allTimestamps.addAll(volunteerGuestData.map { it.timestamp })
    allTimestamps.addAll(volunteerInvitesData.map { it.timestamp })
    
    // Initialize all timestamps with their labels and zero totals
    allTimestamps.forEach { timestamp ->
        val label = volunteerGuestData.find { it.timestamp == timestamp }?.label
            ?: volunteerInvitesData.find { it.timestamp == timestamp }?.label
            ?: ""
        totals[timestamp] = Pair(label, 0f)
    }
    
    // Add volunteer guest data
    volunteerGuestData.forEach { point ->
        val existing = totals[point.timestamp]
        if (existing != null) {
            totals[point.timestamp] = Pair(existing.first, existing.second + point.value)
        } else {
            totals[point.timestamp] = Pair(point.label, point.value)
        }
    }
    
    // Add volunteer invites data
    volunteerInvitesData.forEach { point ->
        val existing = totals[point.timestamp]
        if (existing != null) {
            totals[point.timestamp] = Pair(existing.first, existing.second + point.value)
        } else {
            totals[point.timestamp] = Pair(point.label, point.value)
        }
    }
    
    return totals.map { (timestamp, labelAndTotal) ->
        DataPoint(labelAndTotal.first, labelAndTotal.second, timestamp)
    }.sortedBy { it.timestamp }
}

private fun getDateFormat(timePeriod: TimePeriod, startTime: Long, now: Long): (Long) -> String {
    return when (timePeriod) {
        TimePeriod.ONE_WEEK, TimePeriod.TWO_WEEKS, TimePeriod.ONE_MONTH -> {
            // Format as day (e.g., "Mon 5")
            { timestamp ->
                SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(timestamp))
            }
        }
        TimePeriod.SIX_MONTHS -> {
            // Format as week (e.g., "W12")
            { timestamp ->
                val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                "W${cal.get(Calendar.WEEK_OF_YEAR)}"
            }
        }
        TimePeriod.ONE_YEAR -> {
            // Format as month (e.g., "Jan")
            { timestamp ->
                SimpleDateFormat("MMM", Locale.getDefault()).format(Date(timestamp))
            }
        }
        TimePeriod.MAX -> {
            // Dynamic format based on data range
            val daysDiff = (now - startTime) / (24 * 60 * 60 * 1000)
            when {
                daysDiff <= 30 -> { timestamp ->
                    SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(timestamp))
                }
                daysDiff <= 180 -> { timestamp ->
                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    "W${cal.get(Calendar.WEEK_OF_YEAR)}"
                }
                else -> { timestamp ->
                    SimpleDateFormat("MMM", Locale.getDefault()).format(Date(timestamp))
                }
            }
        }
    }
}

/**
 * Calculates the nearest data point to a given x position on the graph
 * Returns a Pair of (pointIndex, pointValue)
 */
private fun calculateNearestPoint(xPosition: Float, width: Float, dataPoints: List<DataPoint>): Pair<Int, Float> {
    if (dataPoints.isEmpty()) return Pair(-1, 0f)
    
    val xPercent = (xPosition / width).coerceIn(0f, 1f)
    val pointIndex = (xPercent * (dataPoints.size - 1).toFloat()).toInt()
        .coerceIn(0, dataPoints.size - 1)
    
    // Check if we should use the next point for better accuracy
    val nextIndex = pointIndex + 1
    val nearestIndex = if (nextIndex < dataPoints.size) {
        val currentDistance = kotlin.math.abs(
            (pointIndex.toFloat() / (dataPoints.size - 1).toFloat()) - xPercent
        )
        val nextDistance = kotlin.math.abs(
            (nextIndex.toFloat() / (dataPoints.size - 1).toFloat()) - xPercent
        )
        if (nextDistance < currentDistance) nextIndex else pointIndex
    } else {
        pointIndex
    }
    
    return Pair(nearestIndex, dataPoints[nearestIndex].value)
}

/**
 * Export options dialog
 */
@Composable
private fun ExportOptionsDialog(
    onDismiss: () -> Unit,
    onExportXLSX: () -> Unit,
    onExportJPG: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints {
            val isLandscape = maxWidth > maxHeight
            val maxDialogWidth = if (isLandscape) maxWidth * 0.6f else maxWidth * 0.9f
            val maxDialogHeight = if (isLandscape) maxHeight * 0.8f else maxHeight * 0.9f
            
            Card(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .heightIn(max = maxDialogHeight)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Text(
                    text = context.getString(R.string.export_graph),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = context.getString(R.string.choose_export_format),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // XLSX option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExportXLSX() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.export_as_xlsx),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = context.getString(R.string.export_xlsx_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // JPG option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExportJPG() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.export_as_jpg),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = context.getString(R.string.export_jpg_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.cancel))
                }
            }
        }
        }
    }
}

/**
 * Preview dialog with share functionality
 */
@Composable
private fun PreviewDialog(
    file: File,
    exportType: ExportType,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints {
            val isLandscape = maxWidth > maxHeight
            val maxDialogWidth = if (isLandscape) maxWidth * 0.7f else maxWidth * 0.9f
            val maxDialogHeight = if (isLandscape) maxHeight * 0.85f else maxHeight * 0.95f
            val imageMaxHeight = if (isLandscape) maxHeight * 0.5f else maxHeight * 0.4f
            
            Card(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .heightIn(max = maxDialogHeight)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header with close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.export_complete),
                            style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = context.getString(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // Content area - scrollable if needed
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                
                        // Preview for JPG
                        if (exportType == ExportType.JPG && file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            bitmap?.let {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = imageMaxHeight)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = context.getString(R.string.graph_preview),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    
                                    // File info
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "🖼️",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = context.getString(R.string.file_name),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    modifier = Modifier.clickable {
                                                        openFile(context, file, exportType)
                                                    }
                                                )
                                                Text(
                                                    text = context.getString(R.string.file_size, formatFileSize(file.length())),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (exportType == ExportType.XLSX) {
                            // Show info for XLSX - adaptive layout
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (isLandscape) {
                                    // Landscape: horizontal layout
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Icon on the left
                                        Box(
                                            modifier = Modifier
                                                .size(100.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    RoundedCornerShape(16.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TableChart,
                                                contentDescription = null,
                                                modifier = Modifier.size(60.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        
                                        // Information on the right
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = context.getString(R.string.xlsx_export_complete),
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            
                                            Text(
                                                text = context.getString(R.string.xlsx_export_success_message),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            // File info
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "📄",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = context.getString(R.string.file_name),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            text = file.name,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            modifier = Modifier.clickable {
                                                                openFile(context, file, exportType)
                                                            }
                                                        )
                                                        Text(
                                                            text = context.getString(R.string.file_size, formatFileSize(file.length())),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Portrait: vertical layout
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Icon at top
                                        Box(
                                            modifier = Modifier
                                                .size(90.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    RoundedCornerShape(16.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TableChart,
                                                contentDescription = null,
                                                modifier = Modifier.size(50.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        
                                        // Information below
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = context.getString(R.string.xlsx_export_complete),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Center
                                            )
                                            
                                            Text(
                                                text = context.getString(R.string.xlsx_export_success_message),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            // File info
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "📄",
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = context.getString(R.string.file_name),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            text = file.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            modifier = Modifier.clickable {
                                                                openFile(context, file, exportType)
                                                            }
                                                        )
                                                        Text(
                                                            text = context.getString(R.string.file_size, formatFileSize(file.length())),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } // End of scrollable content
                    
                    // Share button
                    Button(
                        onClick = onShare,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            context.getString(R.string.share),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Format file size in human-readable format
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * Share file using Android share intent
 */
private fun shareFile(context: Context, file: File, exportType: ExportType, title: String = context.getString(R.string.active_volunteers_graph)) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val mimeType = when (exportType) {
            ExportType.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ExportType.JPG -> "image/jpeg"
        }
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_graph)))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Open file with Android "open with" dialog using ACTION_VIEW intent
 */
private fun openFile(context: Context, file: File, exportType: ExportType) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val mimeType = when (exportType) {
            ExportType.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ExportType.JPG -> "image/jpeg"
        }
        
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val chooserIntent = Intent.createChooser(viewIntent, context.getString(R.string.open_with))
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
