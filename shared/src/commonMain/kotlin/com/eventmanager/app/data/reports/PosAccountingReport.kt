package com.eventmanager.app.data.reports

import com.eventmanager.app.data.models.AccountHolderType
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferType
import com.eventmanager.app.data.models.SalesCategory

enum class PosReportPeriodMode {
    EVENING,
    PERIOD
}

enum class PosReportVenueScope {
    ALL,
    GLOBAL,
    VENUE,
}

data class PosReportPeriod(
    val mode: PosReportPeriodMode,
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val closureLabel: String?,
    val settingsOffsetHours: Int,
    val closureHour: Int,
    val closureMinute: Int,
    val venueScope: PosReportVenueScope = PosReportVenueScope.ALL,
    val venueName: String? = null,
    val venueLabel: String? = null,
)

data class PosReportLineItem(
    val itemId: Long,
    val name: String,
    val unitPrice: Double,
    val quantity: Int,
    val lineTotal: Double,
    val category: SalesCategory,
)

data class PosSaleDetail(
    val transfer: AccountTransfer,
    val lineItems: List<PosReportLineItem>,
    val grossTotal: Double,
    val creditPaid: Double,
    val cashPaid: Double,
)

data class CategorySalesSummary(
    val category: SalesCategory,
    val quantity: Int,
    val revenue: Double,
)

data class ProductSalesSummary(
    val name: String,
    val quantity: Int,
    val revenue: Double,
)

data class TransferTypeSummary(
    val type: AccountTransferType,
    val count: Int,
    val totalAmount: Double,
    val creditTotal: Double,
    val cashTotal: Double,
)

data class PosAccountingReport(
    val period: PosReportPeriod,
    val currencyCode: String,
    val generatedAtMs: Long,
    val allTransfers: List<AccountTransfer>,
    val posSales: List<PosSaleDetail>,
    val manualAdjustments: List<AccountTransfer>,
    val shiftCredits: List<AccountTransfer>,
    val shiftReversals: List<AccountTransfer>,
    val typeSummaries: List<TransferTypeSummary>,
    val categorySummaries: List<CategorySalesSummary>,
    val productSummaries: List<ProductSalesSummary>,
    val totalPosSalesCount: Int,
    val totalCashCollected: Double,
    val totalCreditUsed: Double,
    val totalManualPositive: Double,
    val totalManualNegative: Double,
    val totalShiftCredit: Double,
    val totalShiftReversal: Double,
    val totalBarDiscountSavings: Double,
) {
    val totalTransferCount: Int get() = allTransfers.size
}
