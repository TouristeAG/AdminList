package com.eventmanager.app.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferType
import com.eventmanager.app.data.reports.PosAccountingReport
import com.eventmanager.app.data.reports.PosAccountingReportBuilder
import com.eventmanager.app.data.reports.PosSaleDetail
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object PosReportPdfEngine {

    private val GENEVA = TimeZone.getTimeZone("Europe/Zurich")
    private val dateTimeFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply { timeZone = GENEVA }
    private val fileFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).apply { timeZone = GENEVA }

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 36f
    private const val MARGIN_TOP = 48f
    private const val MARGIN_BOTTOM = 48f
    private const val HEADER_FILL = 0xFF374785.toInt()
    private const val HEADER_TEXT = 0xFFFFFFFF.toInt()

    fun render(report: PosAccountingReport, outputDirectory: File): File {
        outputDirectory.mkdirs()
        val file = File(outputDirectory, "pos_decompte_${fileFmt.format(report.generatedAtMs)}.pdf")
        val layout = PdfLayout()
        try {
            addCover(layout, report)
            addSummarySection(layout, report)
            addTypeSummarySection(layout, report)
            addCategorySection(layout, report)
            addProductSection(layout, report)
            addPosSalesDetail(layout, report)
            addManualAdjustments(layout, report)
            addShiftTransfers(layout, report)
            addFooterNote(layout, report)
            layout.finish()
            FileOutputStream(file).use { layout.document.writeTo(it) }
        } finally {
            layout.document.close()
        }
        return file
    }

    private fun addCover(layout: PdfLayout, report: PosAccountingReport) {
        layout.drawTitle("Décompte POS")
        layout.drawBody("Période : ${report.period.label}")
        report.period.closureLabel?.let { layout.drawBody("Fermeture : $it") }
        layout.drawBody("Devise : ${report.currencyCode}")
        report.period.venueLabel?.let { layout.drawBody("Lieu : $it") }
        layout.drawBody("Généré le : ${dateTimeFmt.format(report.generatedAtMs)}")
        layout.gap(8f)
    }

    private fun addSummarySection(layout: PdfLayout, report: PosAccountingReport) {
        layout.section("Synthèse comptable")
        layout.table(
            weights = floatArrayOf(2.5f, 1f),
            header = null,
            rows = listOf(
                listOf("Nombre de ventes POS", report.totalPosSalesCount.toString()),
                listOf("Encaissements espèces / carte", money(report.totalCashCollected, report.currencyCode)),
                listOf("Crédit interne consommé", money(report.totalCreditUsed, report.currencyCode)),
                listOf("Économies remises bar", money(report.totalBarDiscountSavings, report.currencyCode)),
                listOf("Ajustements manuels (+)", money(report.totalManualPositive, report.currencyCode)),
                listOf("Ajustements manuels (−)", money(report.totalManualNegative, report.currencyCode)),
                listOf("Crédits de shift", money(report.totalShiftCredit, report.currencyCode)),
                listOf("Annulations de shift", money(report.totalShiftReversal, report.currencyCode)),
                listOf("Total transferts", report.totalTransferCount.toString()),
            ),
        )
        layout.gap(8f)
    }

    private fun addTypeSummarySection(layout: PdfLayout, report: PosAccountingReport) {
        if (report.typeSummaries.isEmpty()) return
        layout.section("Par type de transfert")
        layout.table(
            weights = floatArrayOf(2f, 0.8f, 1.2f, 1.2f, 1.2f),
            header = listOf("Type", "Nb", "Montant", "Crédit", "Espèces"),
            rows = report.typeSummaries.map { s ->
                listOf(
                    transferTypeLabel(s.type),
                    s.count.toString(),
                    money(s.totalAmount, report.currencyCode),
                    money(s.creditTotal, report.currencyCode),
                    money(s.cashTotal, report.currencyCode),
                )
            },
        )
        layout.gap(8f)
    }

    private fun addCategorySection(layout: PdfLayout, report: PosAccountingReport) {
        if (report.categorySummaries.isEmpty()) return
        layout.section("Ventes par catégorie")
        layout.table(
            weights = floatArrayOf(2f, 1f, 1.5f),
            header = listOf("Catégorie", "Quantité", "Chiffre d'affaires"),
            rows = report.categorySummaries.map { c ->
                listOf(c.category.name, c.quantity.toString(), money(c.revenue, report.currencyCode))
            },
        )
        layout.gap(8f)
    }

    private fun addProductSection(layout: PdfLayout, report: PosAccountingReport) {
        if (report.productSummaries.isEmpty()) return
        layout.section("Ventes par produit")
        layout.table(
            weights = floatArrayOf(2.5f, 1f, 1.5f),
            header = listOf("Produit", "Quantité", "Chiffre d'affaires"),
            rows = report.productSummaries.map { p ->
                listOf(p.name, p.quantity.toString(), money(p.revenue, report.currencyCode))
            },
        )
        layout.gap(8f)
    }

    private fun addPosSalesDetail(layout: PdfLayout, report: PosAccountingReport) {
        if (report.posSales.isEmpty()) return
        layout.section("Détail des ventes POS")
        report.posSales.forEach { sale -> addSaleBlock(layout, sale, report.currencyCode) }
    }

    private fun addSaleBlock(layout: PdfLayout, sale: PosSaleDetail, currency: String) {
        val t = sale.transfer
        val discount = t.posBarDiscountPercent?.let { " · remise $it%" } ?: ""
        layout.drawBody("${dateTimeFmt.format(t.createdAt)} · ${t.holderName} (${t.holderType.name})$discount")
        if (sale.lineItems.isNotEmpty()) {
            layout.table(
                weights = floatArrayOf(2.5f, 0.8f, 1f, 1f),
                header = listOf("Article", "Qté", "Prix unit.", "Total"),
                rows = sale.lineItems.map { line ->
                    listOf(
                        line.name,
                        line.quantity.toString(),
                        money(line.unitPrice, currency),
                        money(line.lineTotal, currency),
                    )
                },
            )
        } else {
            layout.drawSmall(t.description.ifBlank { "—" })
        }
        layout.drawSmall(
            "Crédit : ${money(sale.creditPaid, currency)}   " +
                "Espèces/carte : ${money(sale.cashPaid, currency)}   " +
                "Total : ${money(sale.grossTotal, currency)}",
        )
        layout.gap(10f)
    }

    private fun addManualAdjustments(layout: PdfLayout, report: PosAccountingReport) {
        if (report.manualAdjustments.isEmpty()) return
        layout.section("Ajustements manuels")
        layout.table(
            weights = floatArrayOf(1.5f, 2f, 1.2f, 2.5f),
            header = listOf("Date", "Compte", "Montant", "Note"),
            rows = report.manualAdjustments.map { t ->
                listOf(
                    dateTimeFmt.format(t.createdAt),
                    "${t.holderName} (${t.holderType.name})",
                    money(t.amount, report.currencyCode),
                    t.description.ifBlank { "—" },
                )
            },
        )
        layout.gap(8f)
    }

    private fun addShiftTransfers(layout: PdfLayout, report: PosAccountingReport) {
        val rows = report.shiftCredits + report.shiftReversals
        if (rows.isEmpty()) return
        layout.section("Crédits et annulations de shift")
        layout.table(
            weights = floatArrayOf(1.5f, 2f, 1.2f, 1.5f, 2f),
            header = listOf("Date", "Bénévole", "Type", "Montant", "Shift"),
            rows = rows.sortedBy(AccountTransfer::createdAt).map { t ->
                listOf(
                    dateTimeFmt.format(t.createdAt),
                    t.holderName,
                    transferTypeLabel(t.type),
                    money(t.amount, report.currencyCode),
                    listOf(t.jobTypeName, t.jobDate?.let { dateTimeFmt.format(it) } ?: "")
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "—" },
                )
            },
        )
        layout.gap(8f)
    }

    private fun addFooterNote(layout: PdfLayout, report: PosAccountingReport) {
        layout.section("Notes méthodologiques")
        val offset = report.period.settingsOffsetHours
        val closure = PosAccountingReportBuilder.formatClosureLabel(
            report.period.closureHour,
            report.period.closureMinute,
        )
        layout.drawSmall(
            "Fuseau horaire : Europe/Zurich. Décalage paramètres : ${if (offset >= 0) "+" else ""}$offset h. " +
                "Fermeture appliquée : $closure. " +
                "Tous les montants sont en ${report.currencyCode}. " +
                "Ce document inclut les ventes POS, ajustements manuels, crédits de shift et annulations.",
        )
    }

    private fun money(value: Double, currency: String): String =
        String.format(Locale.getDefault(), "%.2f %s", value, currency)

    private fun transferTypeLabel(type: AccountTransferType): String = when (type) {
        AccountTransferType.POS_SALE -> "Vente POS"
        AccountTransferType.MANUAL_ADJUSTMENT -> "Ajustement manuel"
        AccountTransferType.SHIFT_CREDIT -> "Crédit shift"
        AccountTransferType.SHIFT_REVERSAL -> "Annulation shift"
    }

    private class PdfLayout {
        val document = PdfDocument()
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = MARGIN_TOP
        private val contentWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 22f
        }
        private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 14f
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 10f
        }
        private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 9f
        }
        private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9f
            color = HEADER_TEXT
        }
        private val headerFillPaint = Paint().apply { color = HEADER_FILL }

        init {
            startPage()
        }

        fun finish() {
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
        }

        fun drawTitle(text: String) {
            ensureSpace(lineHeight(titlePaint) + 12f)
            canvas?.drawText(text, MARGIN_LEFT, y, titlePaint)
            y += lineHeight(titlePaint) + 12f
        }

        fun drawBody(text: String) {
            for (line in wrap(text, bodyPaint, contentWidth)) {
                ensureSpace(lineHeight(bodyPaint))
                canvas?.drawText(line, MARGIN_LEFT, y, bodyPaint)
                y += lineHeight(bodyPaint)
            }
        }

        fun drawSmall(text: String) {
            for (line in wrap(text, smallPaint, contentWidth)) {
                ensureSpace(lineHeight(smallPaint))
                canvas?.drawText(line, MARGIN_LEFT, y, smallPaint)
                y += lineHeight(smallPaint)
            }
        }

        fun section(title: String) {
            gap(8f)
            ensureSpace(lineHeight(sectionPaint) + 6f)
            canvas?.drawText(title, MARGIN_LEFT, y, sectionPaint)
            y += lineHeight(sectionPaint) + 6f
        }

        fun gap(amount: Float) {
            ensureSpace(amount)
            y += amount
        }

        fun table(weights: FloatArray, header: List<String>?, rows: List<List<String>>) {
            val colWidths = weights.map { it / weights.sum() * contentWidth }
            val cellPadding = 6f
            val rowLineHeight = lineHeight(bodyPaint)
            val headerLineHeight = lineHeight(headerPaint)

            fun drawRow(cells: List<String>, isHeader: Boolean) {
                val paint = if (isHeader) headerPaint else bodyPaint
                val lineHeight = if (isHeader) headerLineHeight else rowLineHeight
                val wrapped = cells.mapIndexed { index, cell ->
                    val width = colWidths[index] - cellPadding * 2
                    wrap(cell, paint, width)
                }
                val rowHeight = (wrapped.maxOf { it.size }.coerceAtLeast(1) * lineHeight) + cellPadding * 2
                ensureSpace(rowHeight)
                val top = y - lineHeight + cellPadding
                if (isHeader) {
                    canvas?.drawRect(MARGIN_LEFT, top, MARGIN_LEFT + contentWidth, top + rowHeight, headerFillPaint)
                }
                var x = MARGIN_LEFT
                wrapped.forEachIndexed { index, lines ->
                    val alignRight = !isHeader && index > 0
                    var textY = top + cellPadding + lineHeight
                    lines.forEach { line ->
                        val textX = if (alignRight) {
                            x + colWidths[index] - cellPadding - paint.measureText(line)
                        } else {
                            x + cellPadding
                        }
                        canvas?.drawText(line, textX, textY, paint)
                        textY += lineHeight
                    }
                    x += colWidths[index]
                }
                y = top + rowHeight + lineHeight * 0.2f
            }

            header?.let { drawRow(it, isHeader = true) }
            rows.forEach { drawRow(it, isHeader = false) }
        }

        private fun startPage() {
            page?.let { document.finishPage(it) }
            pageNumber += 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page!!.canvas
            y = MARGIN_TOP
        }

        private fun ensureSpace(required: Float) {
            if (y + required > PAGE_HEIGHT - MARGIN_BOTTOM) {
                startPage()
            }
        }

        private fun lineHeight(paint: Paint): Float = paint.fontMetrics.run { descent - ascent }

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isEmpty()) return listOf("")
            val words = text.split(' ')
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "${current} $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines += current.toString()
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
            return lines.ifEmpty { listOf("") }
        }
    }
}
