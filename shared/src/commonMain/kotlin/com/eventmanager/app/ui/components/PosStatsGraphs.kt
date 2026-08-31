package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import com.eventmanager.app.data.utils.PosHourPoint
import com.eventmanager.app.resources.pos_stats_balance_0_10
import com.eventmanager.app.resources.pos_stats_balance_10_50
import com.eventmanager.app.resources.pos_stats_balance_50_plus
import com.eventmanager.app.resources.pos_stats_balance_le_zero
import com.eventmanager.app.resources.pos_stats_balances_guests
import com.eventmanager.app.resources.pos_stats_balances_guests_description
import com.eventmanager.app.resources.pos_stats_balances_volunteers
import com.eventmanager.app.resources.pos_stats_balances_volunteers_description
import com.eventmanager.app.resources.pos_stats_bar_discount_savings
import com.eventmanager.app.resources.pos_stats_bar_discount_savings_description
import com.eventmanager.app.resources.pos_stats_credits_chf
import com.eventmanager.app.resources.pos_stats_credits_chf_description
import com.eventmanager.app.resources.pos_stats_credits_manual_chf
import com.eventmanager.app.resources.pos_stats_credits_reversal_chf
import com.eventmanager.app.resources.pos_stats_credits_shift_chf
import com.eventmanager.app.resources.pos_stats_peak_hours
import com.eventmanager.app.resources.pos_stats_peak_hours_description
import com.eventmanager.app.resources.pos_stats_repeat_1
import com.eventmanager.app.resources.pos_stats_repeat_10_plus
import com.eventmanager.app.resources.pos_stats_repeat_2_5
import com.eventmanager.app.resources.pos_stats_repeat_6_9
import com.eventmanager.app.resources.pos_stats_repeat_customers
import com.eventmanager.app.resources.pos_stats_repeat_customers_description
import com.eventmanager.app.resources.pos_stats_repeat_return_empty
import com.eventmanager.app.resources.pos_stats_repeat_return_rate
import com.eventmanager.app.resources.pos_stats_repeat_return_subtitle
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.PosVenueScope
import com.eventmanager.app.data.models.SalesCategory
import com.eventmanager.app.data.utils.PosCategoryProductBreakdown
import com.eventmanager.app.data.utils.PosDashboardSnapshot
import com.eventmanager.app.data.utils.PosDashboardStats
import com.eventmanager.app.data.utils.PosNamedAmount
import com.eventmanager.app.data.utils.PosSeriesPoint
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.isDesktop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.pos_stats_cash_personnel
import com.eventmanager.app.resources.pos_stats_cash_personnel_description
import com.eventmanager.app.resources.pos_stats_category_mix
import com.eventmanager.app.resources.pos_stats_category_mix_description
import com.eventmanager.app.resources.pos_stats_category_products
import com.eventmanager.app.resources.pos_stats_category_products_description
import com.eventmanager.app.resources.pos_stats_credit_used
import com.eventmanager.app.resources.pos_stats_credit_used_description
import com.eventmanager.app.resources.pos_stats_credits_granted
import com.eventmanager.app.resources.pos_stats_credits_granted_description
import com.eventmanager.app.resources.pos_stats_credits_manual
import com.eventmanager.app.resources.pos_stats_credits_manual_description
import com.eventmanager.app.resources.pos_stats_credits_shifts
import com.eventmanager.app.resources.pos_stats_credits_shifts_description
import com.eventmanager.app.resources.pos_stats_highest_credit_sale
import com.eventmanager.app.resources.pos_stats_highest_credit_sale_empty
import com.eventmanager.app.resources.pos_stats_highest_credit_sale_subtitle
import com.eventmanager.app.resources.pos_stats_holder_guest
import com.eventmanager.app.resources.pos_stats_holder_mix
import com.eventmanager.app.resources.pos_stats_holder_mix_description
import com.eventmanager.app.resources.pos_stats_holder_volunteer
import com.eventmanager.app.resources.pos_stats_no_data
import com.eventmanager.app.resources.pos_stats_other_products
import com.eventmanager.app.resources.pos_stats_sales_by_venue
import com.eventmanager.app.resources.pos_stats_sales_by_venue_description
import com.eventmanager.app.resources.pos_stats_sales_count
import com.eventmanager.app.resources.pos_stats_sales_count_description
import com.eventmanager.app.resources.pos_stats_top_credit_consumer
import com.eventmanager.app.resources.pos_stats_top_credit_consumer_empty
import com.eventmanager.app.resources.pos_stats_top_credit_consumer_subtitle
import com.eventmanager.app.resources.pos_venue_global
import com.eventmanager.app.resources.sales_category_bar
import com.eventmanager.app.resources.sales_category_entry
import com.eventmanager.app.resources.sales_category_merch
import com.eventmanager.app.resources.sales_category_other
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

private val CategoryColors = mapOf(
    SalesCategory.BAR to Color(0xFF5B8FF9),
    SalesCategory.ENTRY to Color(0xFFF6BD16),
    SalesCategory.MERCH to Color(0xFF5AD8A6),
    SalesCategory.OTHER to Color(0xFF9270CA),
)

internal val SlicePalette = listOf(
    Color(0xFF5B8FF9),
    Color(0xFFF6BD16),
    Color(0xFF5AD8A6),
    Color(0xFF9270CA),
    Color(0xFF6DC8EC),
    Color(0xFFFF6B9D),
    Color(0xFFB8B8B8),
)

@Composable
fun PosActivityGraphs(
    stats: PosDashboardSnapshot,
    timePeriod: TimePeriod,
    isPhone: Boolean,
    now: Long,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PosLineGraph(
            title = stringResource(Res.string.pos_stats_sales_count),
            description = stringResource(Res.string.pos_stats_sales_count_description),
            icon = Icons.Default.ShoppingCart,
            series = stats.salesCount,
            timePeriod = timePeriod,
            isPhone = isPhone,
            now = now,
        )
        PosLineGraph(
            title = stringResource(Res.string.pos_stats_credits_granted),
            description = stringResource(Res.string.pos_stats_credits_granted_description),
            icon = Icons.Default.Star,
            series = stats.creditsGrantedTotal,
            timePeriod = timePeriod,
            isPhone = isPhone,
            now = now,
        )
        PosLineGraph(
            title = stringResource(Res.string.pos_stats_credits_shifts),
            description = stringResource(Res.string.pos_stats_credits_shifts_description),
            icon = Icons.Default.Work,
            series = stats.creditsFromShifts,
            timePeriod = timePeriod,
            isPhone = isPhone,
            now = now,
        )
        PosLineGraph(
            title = stringResource(Res.string.pos_stats_credits_manual),
            description = stringResource(Res.string.pos_stats_credits_manual_description),
            icon = Icons.Default.Edit,
            series = stats.creditsManual,
            timePeriod = timePeriod,
            isPhone = isPhone,
            now = now,
        )
        PosLineGraph(
            title = stringResource(Res.string.pos_stats_credit_used),
            description = stringResource(Res.string.pos_stats_credit_used_description),
            icon = Icons.Default.AccountBalanceWallet,
            series = stats.creditUsedChf,
            timePeriod = timePeriod,
            isPhone = isPhone,
            now = now,
        )
        PosLineGraph(
            title = stringResource(Res.string.pos_stats_cash_personnel),
            description = stringResource(Res.string.pos_stats_cash_personnel_description),
            icon = Icons.Default.Payments,
            series = stats.cashPersonnelChf,
            timePeriod = timePeriod,
            isPhone = isPhone,
            now = now,
        )
        PosHourGraph(
            title = stringResource(Res.string.pos_stats_peak_hours),
            description = stringResource(Res.string.pos_stats_peak_hours_description),
            icon = Icons.Default.AccessTime,
            hours = stats.peakHourSales,
            isPhone = isPhone,
        )
        PosCreditsChfGraph(
            stats = stats,
            timePeriod = timePeriod,
            isPhone = isPhone,
            now = now,
        )
        PosLineGraph(
            title = stringResource(Res.string.pos_stats_bar_discount_savings),
            description = stringResource(Res.string.pos_stats_bar_discount_savings_description),
            icon = Icons.Default.Percent,
            series = stats.barDiscountSavings,
            timePeriod = timePeriod,
            isPhone = isPhone,
            now = now,
        )
    }
}

@Composable
fun PosMixGraphs(
    stats: PosDashboardSnapshot,
    isPhone: Boolean,
    currencyCode: String,
) {
    val emptyText = stringResource(Res.string.pos_stats_no_data)
    val otherLabel = stringResource(Res.string.pos_stats_other_products)
    val globalVenue = stringResource(Res.string.pos_venue_global)
    val volunteerLabel = stringResource(Res.string.pos_stats_holder_volunteer)
    val guestLabel = stringResource(Res.string.pos_stats_holder_guest)
    val barLabel = stringResource(Res.string.sales_category_bar)
    val entryLabel = stringResource(Res.string.sales_category_entry)
    val merchLabel = stringResource(Res.string.sales_category_merch)
    val otherCategoryLabel = stringResource(Res.string.sales_category_other)

    fun categoryLabel(key: String): String = when (key) {
        SalesCategory.BAR.name -> barLabel
        SalesCategory.ENTRY.name -> entryLabel
        SalesCategory.MERCH.name -> merchLabel
        else -> otherCategoryLabel
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatsPieCard(
            title = stringResource(Res.string.pos_stats_category_mix),
            description = stringResource(Res.string.pos_stats_category_mix_description),
            icon = Icons.Default.PointOfSale,
            slices = stats.revenueByCategory.toPieSlices(
                colorFor = { item, _ -> CategoryColors[runCatching { SalesCategory.valueOf(item.key) }.getOrNull()] ?: SlicePalette.last() },
                labelFor = { categoryLabel(it.key) },
                detailFor = { item, pct -> "${formatMoney(item.value, currencyCode)} (${formatPct(pct)})" },
            ),
            isPhone = isPhone,
            emptyText = emptyText,
        )

        stats.productsByCategory.forEach { breakdown ->
            val categoryTitle = categoryLabel(breakdown.category.name)
            StatsPieCard(
                title = stringResource(Res.string.pos_stats_category_products, categoryTitle),
                description = stringResource(Res.string.pos_stats_category_products_description),
                icon = Icons.Default.ShoppingCart,
                slices = breakdown.toProductSlices(otherLabel),
                isPhone = isPhone,
                emptyText = emptyText,
            )
        }

        StatsPieCard(
            title = stringResource(Res.string.pos_stats_sales_by_venue),
            description = stringResource(Res.string.pos_stats_sales_by_venue_description),
            icon = Icons.Default.Place,
            slices = stats.salesByVenue.toPieSlices(
                colorFor = { _, index -> SlicePalette[index % SlicePalette.size] },
                labelFor = { item ->
                    if (item.key.equals(PosVenueScope.GLOBAL, ignoreCase = true)) globalVenue else item.key
                },
                detailFor = { item, pct -> "${item.quantity} (${formatPct(pct)})" },
            ),
            isPhone = isPhone,
            emptyText = emptyText,
        )

        StatsPieCard(
            title = stringResource(Res.string.pos_stats_holder_mix),
            description = stringResource(Res.string.pos_stats_holder_mix_description),
            icon = Icons.Default.People,
            slices = stats.salesByHolderType.toPieSlices(
                colorFor = { item, _ ->
                    if (item.key == "VOLUNTEER") Color(0xFF5B8FF9) else Color(0xFFF6BD16)
                },
                labelFor = { item -> if (item.key == "VOLUNTEER") volunteerLabel else guestLabel },
                detailFor = { item, pct -> "${item.quantity} (${formatPct(pct)})" },
            ),
            isPhone = isPhone,
            emptyText = emptyText,
        )

        PosHighlightCard(
            title = stringResource(Res.string.pos_stats_highest_credit_sale),
            icon = Icons.Default.EmojiEvents,
            isPhone = isPhone,
            value = stats.highestCreditOnlySale?.let { formatMoney(it.amount, currencyCode) },
            subtitle = stats.highestCreditOnlySale?.let {
                stringResource(Res.string.pos_stats_highest_credit_sale_subtitle, it.holderName)
            },
            emptyText = stringResource(Res.string.pos_stats_highest_credit_sale_empty),
        )

        PosHighlightCard(
            title = stringResource(Res.string.pos_stats_top_credit_consumer),
            icon = Icons.Default.Star,
            isPhone = isPhone,
            value = stats.topCreditConsumer?.holderName,
            subtitle = stats.topCreditConsumer?.let {
                stringResource(
                    Res.string.pos_stats_top_credit_consumer_subtitle,
                    formatMoney(it.totalPurchases, currencyCode),
                )
            },
            emptyText = stringResource(Res.string.pos_stats_top_credit_consumer_empty),
        )

        val repeatOne = stringResource(Res.string.pos_stats_repeat_1)
        val repeatTwoFive = stringResource(Res.string.pos_stats_repeat_2_5)
        val repeatSixNine = stringResource(Res.string.pos_stats_repeat_6_9)
        val repeatTenPlus = stringResource(Res.string.pos_stats_repeat_10_plus)
        StatsBarCard(
            title = stringResource(Res.string.pos_stats_repeat_customers),
            description = stringResource(Res.string.pos_stats_repeat_customers_description),
            icon = Icons.Default.Repeat,
            bars = stats.repeatCustomers.buckets.toBarItems(
                colorFor = { _, index -> SlicePalette[index % SlicePalette.size] },
                labelFor = { item ->
                    when (item.key) {
                        PosDashboardStats.REPEAT_ONE -> repeatOne
                        PosDashboardStats.REPEAT_TWO_TO_FIVE -> repeatTwoFive
                        PosDashboardStats.REPEAT_SIX_TO_NINE -> repeatSixNine
                        PosDashboardStats.REPEAT_TEN_PLUS -> repeatTenPlus
                        else -> item.key
                    }
                },
            ),
            isPhone = isPhone,
            emptyText = emptyText,
        )
        val returnPct = formatPct(stats.repeatCustomers.returnPercent.toFloat())
        PosHighlightCard(
            title = stringResource(Res.string.pos_stats_repeat_return_rate),
            icon = Icons.Default.Repeat,
            isPhone = isPhone,
            value = if (stats.repeatCustomers.totalHolders > 0) returnPct else null,
            subtitle = stringResource(Res.string.pos_stats_repeat_return_subtitle),
            emptyText = stringResource(Res.string.pos_stats_repeat_return_empty),
        )

        val leZero = stringResource(Res.string.pos_stats_balance_le_zero)
        val d0To10 = stringResource(Res.string.pos_stats_balance_0_10)
        val d10To50 = stringResource(Res.string.pos_stats_balance_10_50)
        val d50Plus = stringResource(Res.string.pos_stats_balance_50_plus)
        fun balanceLabel(key: String): String = when (key) {
            PosDashboardStats.BALANCE_LE_ZERO -> leZero
            PosDashboardStats.BALANCE_D0_10 -> d0To10
            PosDashboardStats.BALANCE_D10_50 -> d10To50
            PosDashboardStats.BALANCE_D50_PLUS -> d50Plus
            else -> key
        }
        StatsBarCard(
            title = stringResource(Res.string.pos_stats_balances_volunteers),
            description = stringResource(Res.string.pos_stats_balances_volunteers_description),
            icon = Icons.Default.AccountBalance,
            bars = stats.volunteerBalanceBuckets.toBarItems(
                colorFor = { _, index -> SlicePalette[index % SlicePalette.size] },
                labelFor = { balanceLabel(it.key) },
            ),
            isPhone = isPhone,
            emptyText = emptyText,
        )
        StatsBarCard(
            title = stringResource(Res.string.pos_stats_balances_guests),
            description = stringResource(Res.string.pos_stats_balances_guests_description),
            icon = Icons.Default.People,
            bars = stats.guestBalanceBuckets.toBarItems(
                colorFor = { _, index -> SlicePalette[index % SlicePalette.size] },
                labelFor = { balanceLabel(it.key) },
            ),
            isPhone = isPhone,
            emptyText = emptyText,
        )
    }
}

@Composable
private fun PosHourGraph(
    title: String,
    description: String,
    icon: ImageVector,
    hours: List<PosHourPoint>,
    isPhone: Boolean,
) {
    val dataPoints = hours.toHourGraphPoints()
    if (dataPoints.size < 2) return
    GraphCardWithExport(
        title = title,
        icon = icon,
        dataPoints = dataPoints,
        timePeriod = TimePeriod.ONE_WEEK,
        isPhone = isPhone,
        description = description,
    )
}

@Composable
private fun PosCreditsChfGraph(
    stats: PosDashboardSnapshot,
    timePeriod: TimePeriod,
    isPhone: Boolean,
    now: Long,
) {
    val seriesData = listOf(
        Triple(
            stringResource(Res.string.pos_stats_credits_shift_chf),
            stats.shiftCreditChf.toGraphPoints(timePeriod, now),
            SlicePalette[0],
        ),
        Triple(
            stringResource(Res.string.pos_stats_credits_manual_chf),
            stats.manualCreditChf.toGraphPoints(timePeriod, now),
            SlicePalette[1],
        ),
        Triple(
            stringResource(Res.string.pos_stats_credits_reversal_chf),
            stats.shiftReversalChf.toGraphPoints(timePeriod, now),
            SlicePalette[2],
        ),
    )
    if (seriesData.all { it.second.size < 2 }) return
    MultiLineGraph(
        label = stringResource(Res.string.pos_stats_credits_chf),
        description = stringResource(Res.string.pos_stats_credits_chf_description),
        seriesData = seriesData,
        timePeriod = timePeriod,
        isPhone = isPhone,
    )
}

@Composable
private fun PosLineGraph(
    title: String,
    description: String,
    icon: ImageVector,
    series: List<PosSeriesPoint>,
    timePeriod: TimePeriod,
    isPhone: Boolean,
    now: Long,
) {
    val dataPoints = series.toGraphPoints(timePeriod, now)
    if (dataPoints.size < 2) return
    GraphCardWithExport(
        title = title,
        icon = icon,
        dataPoints = dataPoints,
        timePeriod = timePeriod,
        isPhone = isPhone,
        description = description,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StatsPieCard(
    title: String,
    icon: ImageVector,
    slices: List<PieSlice>,
    isPhone: Boolean,
    emptyText: String,
    description: String? = null,
) {
    val platformContext = LocalPlatformContext.current
    val useTouchGestures = !platformContext.isDesktop
    var showExportDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportType by remember { mutableStateOf<ExportType?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportType?>(null) }
    val canExport = slices.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .then(
                if (canExport) {
                    Modifier.graphCardExportInteraction(useTouchGestures) { showExportDialog = true }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPhone) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPhone) 2.dp else 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isPhone) 36.dp else 40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (slices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isPhone) 160.dp else 200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(8.dp),
                    ) {
                        PieChart(data = slices, modifier = Modifier.fillMaxSize())
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        slices.forEach { slice ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(slice.color, shape = RoundedCornerShape(4.dp)),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = slice.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = slice.detail.ifBlank { formatPct(slice.percentage) },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
            },
        )
    }

    val scope = rememberCoroutineScope()
    LaunchedEffect(isExporting, pendingExportType) {
        if (isExporting && pendingExportType != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val prefix = title.lowercase()
                        .replace(Regex("[^a-z0-9]+"), "_")
                        .trim('_')
                        .ifBlank { "pos_distribution" }
                    val file = if (pendingExportType == ExportType.XLSX) {
                        GraphExportBridge.exportDistributionXlsx(
                            platformContext = platformContext,
                            fileNamePrefix = prefix,
                            sheetName = title.take(31),
                            title = title,
                            firstColumnHeader = title,
                            rows = slices.map { slice ->
                                DistributionExportRow(slice.label, slice.count, slice.percentage)
                            },
                        )
                    } else {
                        GraphExportBridge.exportPieChartJpg(
                            platformContext = platformContext,
                            title = title,
                            segments = slices.map { slice ->
                                Pair(slice.label, Pair(slice.percentage, slice.color.toArgb()))
                            },
                        )
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

    if (showPreviewDialog && exportedFile != null && exportType != null) {
        ExportedFilePreviewDialog(
            file = exportedFile!!,
            exportType = exportType!!,
            title = title,
            onDismiss = {
                showPreviewDialog = false
                exportedFile = null
                exportType = null
            },
        )
    }

    if (isExporting) {
        ExportLoadingDialog()
    }
}

@Composable
internal fun PosHighlightCard(
    title: String,
    icon: ImageVector,
    isPhone: Boolean,
    value: String?,
    subtitle: String?,
    emptyText: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPhone) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPhone) 2.dp else 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (isPhone) 36.dp else 40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (value.isNullOrBlank()) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = value,
                        style = if (isPhone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

internal data class StatsBarItem(
    val label: String,
    val value: Float,
    val color: Color,
    val count: Int = 0,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StatsBarCard(
    title: String,
    icon: ImageVector,
    bars: List<StatsBarItem>,
    isPhone: Boolean,
    emptyText: String,
    description: String? = null,
) {
    val platformContext = LocalPlatformContext.current
    val useTouchGestures = !platformContext.isDesktop
    var showExportDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportType by remember { mutableStateOf<ExportType?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportType?>(null) }
    val canExport = bars.any { it.value > 0f }
    val maxValue = bars.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    val total = bars.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .then(
                if (canExport) {
                    Modifier.graphCardExportInteraction(useTouchGestures) { showExportDialog = true }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPhone) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPhone) 2.dp else 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isPhone) 36.dp else 40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!canExport) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isPhone) 160.dp else 200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val barMaxHeight = if (isPhone) 120.dp else 160.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barMaxHeight + 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    bars.forEach { bar ->
                        val fraction = (bar.value / maxValue).coerceIn(0f, 1f)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(
                                text = bar.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .height((barMaxHeight.value * fraction).dp.coerceAtLeast(if (bar.value > 0f) 4.dp else 0.dp))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(bar.color),
                            )
                            Text(
                                text = bar.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

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
            },
        )
    }

    val scope = rememberCoroutineScope()
    LaunchedEffect(isExporting, pendingExportType) {
        if (isExporting && pendingExportType != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val prefix = title.lowercase()
                        .replace(Regex("[^a-z0-9]+"), "_")
                        .trim('_')
                        .ifBlank { "histogram" }
                    val file = if (pendingExportType == ExportType.XLSX) {
                        GraphExportBridge.exportDistributionXlsx(
                            platformContext = platformContext,
                            fileNamePrefix = prefix,
                            sheetName = title.take(31),
                            title = title,
                            firstColumnHeader = title,
                            rows = bars.map { bar ->
                                DistributionExportRow(
                                    label = bar.label,
                                    count = bar.count,
                                    percentage = (bar.value / total) * 100f,
                                )
                            },
                        )
                    } else {
                        GraphExportBridge.exportPieChartJpg(
                            platformContext = platformContext,
                            title = title,
                            segments = bars.map { bar ->
                                Pair(bar.label, Pair((bar.value / total) * 100f, bar.color.toArgb()))
                            },
                        )
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

    if (showPreviewDialog && exportedFile != null && exportType != null) {
        ExportedFilePreviewDialog(
            file = exportedFile!!,
            exportType = exportType!!,
            title = title,
            onDismiss = {
                showPreviewDialog = false
                exportedFile = null
                exportType = null
            },
        )
    }

    if (isExporting) {
        ExportLoadingDialog()
    }
}

internal fun List<PosSeriesPoint>.toGraphPoints(timePeriod: TimePeriod, now: Long): List<DataPoint> {
    if (isEmpty()) return emptyList()
    val start = first().timestamp
    val format = formatGraphDateLabel(timePeriod, start, now)
    return map { DataPoint(format(it.timestamp), it.value.toFloat(), it.timestamp) }
}

internal fun List<PosNamedAmount>.toPieSlices(
    colorFor: (PosNamedAmount, Int) -> Color,
    labelFor: (PosNamedAmount) -> String,
    detailFor: (PosNamedAmount, Float) -> String,
): List<PieSlice> {
    val total = sumOf { it.value }
    if (total <= 0.0) return emptyList()
    return mapIndexed { index, item ->
        val pct = ((item.value / total) * 100.0).toFloat()
        PieSlice(
            label = labelFor(item),
            percentage = pct,
            color = colorFor(item, index),
            detail = detailFor(item, pct),
            count = item.quantity,
        )
    }
}

private fun PosCategoryProductBreakdown.toProductSlices(otherLabel: String): List<PieSlice> {
    return products.toPieSlices(
        colorFor = { item, index ->
            if (item.key == PosDashboardStats.OTHER_PRODUCT_KEY) SlicePalette.last()
            else SlicePalette[index % (SlicePalette.size - 1)]
        },
        labelFor = { item ->
            if (item.key == PosDashboardStats.OTHER_PRODUCT_KEY) otherLabel else item.key
        },
        detailFor = { item, pct -> "${item.quantity} (${formatPct(pct)})" },
    )
}

private fun formatMoney(value: Double, currency: String): String =
    String.format(Locale.getDefault(), "%.2f %s", value, currency)

internal fun formatPct(percentage: Float): String =
    String.format(Locale.getDefault(), "%.1f%%", percentage)

internal fun List<PosHourPoint>.toHourGraphPoints(): List<DataPoint> {
    return mapIndexed { index, point ->
        DataPoint("${point.hour}h", point.value.toFloat(), index * 3_600_000L)
    }
}

internal fun List<PosNamedAmount>.toBarItems(
    colorFor: (PosNamedAmount, Int) -> Color,
    labelFor: (PosNamedAmount) -> String,
): List<StatsBarItem> {
    return mapIndexed { index, item ->
        StatsBarItem(
            label = labelFor(item),
            value = item.quantity.toFloat().takeIf { it > 0f } ?: item.value.toFloat(),
            color = colorFor(item, index),
            count = item.quantity,
        )
    }
}
